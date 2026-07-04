"""recon_worker 入口：3D 重建工作器。

消费 ``stream:recon`` 消息，协调完整重建链路：
  1. 从 MinIO 下载输入图像集
  2. COLMAP SfM 重建（稀疏位姿）
  3. gaussian-splatting 训练（.ply 高斯模型）
  4. .ply -> .splat 格式转换
  5. 上传 .splat 到 MinIO 并回写 SUCCESS + presigned URL

全流程通过 GPU 槽位管理确保并发安全，进度通过 StatusWriter 实时回写。

消息字段（Java 后端投递）:
  taskId        : 任务 ID
  imagesPrefix  : MinIO 中图像集前缀路径（如 recon/task-xxx/images/）
  iterations    : 训练迭代数（可选，默认 30000）
  callbackUrl   : 回调 URL（可选，状态回写已覆盖）
"""

import json
import logging
import os
import shutil
import tempfile
from pathlib import Path
from typing import Any, Dict

from common.config import Config, settings
from common.gpu_slot import GpuSlotManager
from common.minio_client import MinioClient
from common.status_writer import StatusWriter
from common.stream_consumer import StreamConsumer

from .colmap_runner import ColmapRunner
from .splat_exporter import SplatExporter
from .train_runner import TrainRunner

logger = logging.getLogger(__name__)

STREAM_NAME = "stream:recon"


class ReconWorker:
    """3D 重建工作器：协调 COLMAP -> 训练 -> 导出完整链路。"""

    def __init__(self, config: Config = None) -> None:
        self.config = config or settings

        # 共享组件
        self.status_writer = StatusWriter(config=self.config)
        self.gpu_slot = GpuSlotManager(config=self.config)
        self.minio = MinioClient(config=self.config)

        # 链路组件
        self.colmap_runner = ColmapRunner(config=self.config)
        self.train_runner = TrainRunner(config=self.config)
        self.splat_exporter = SplatExporter(config=self.config)

    # ------------------------------------------------------------------ 消息处理
    def handle_message(self, msg_id: str, fields: Dict[str, Any]) -> bool:
        """处理单条重建任务消息。"""
        task_id = fields.get("taskId", "")
        if not task_id:
            logger.error("消息缺少 taskId 字段 msg_id=%s", msg_id)
            return True  # ACK 掉无效消息

        images_prefix = fields.get("imagesPrefix", "")
        iterations = int(fields.get("iterations", "30000"))

        logger.info("[%s] 开始重建任务 images=%s iterations=%d", task_id, images_prefix, iterations)
        self.status_writer.write_running(task_id, stage="init", progress=0, message="重建任务已接收")

        work_dir = None
        try:
            # 1. 申请 GPU 槽位
            self.status_writer.write_progress(task_id, stage="gpu_wait", progress=1, message="等待 GPU 槽位")
            if not self.gpu_slot.acquire(task_id, timeout_seconds=300):
                raise RuntimeError("GPU 槽位申请超时")
            logger.info("[%s] GPU 槽位已获取", task_id)

            # 2. 创建临时工作目录
            work_dir = Path(tempfile.mkdtemp(prefix=f"recon_{task_id}_"))
            images_dir = work_dir / "images"
            images_dir.mkdir(parents=True, exist_ok=True)

            # 3. 从 MinIO 下载输入图像
            self.status_writer.write_progress(task_id, stage="download", progress=2, message="下载输入图像")
            self._download_images(images_prefix, images_dir, task_id)

            # 4. 定义进度回调
            def on_progress(percent: int, message: str):
                self.status_writer.write_progress(task_id, stage="pipeline", progress=percent, message=message)

            # 5. COLMAP 重建
            colmap_dir = work_dir / "colmap"
            on_progress(5, "COLMAP 重建启动")
            sparse_dir = self.colmap_runner.run(
                images_dir=str(images_dir),
                workspace_dir=str(colmap_dir),
                task_id=task_id,
                on_progress=on_progress,
            )

            # 6. gaussian-splatting 训练
            train_dir = work_dir / "training"
            ply_path = self.train_runner.run(
                colmap_dir=sparse_dir,
                images_dir=str(images_dir),
                output_dir=str(train_dir),
                task_id=task_id,
                iterations=iterations,
                on_progress=on_progress,
            )

            # 7. .ply -> .splat 转换
            splat_path = str(work_dir / "output.splat")
            self.splat_exporter.export(
                ply_path=ply_path,
                output_path=splat_path,
                task_id=task_id,
                format="splat",
                on_progress=on_progress,
            )

            # 8. 上传 .splat 到 MinIO
            self.status_writer.write_progress(task_id, stage="upload", progress=96, message="上传结果文件")
            object_name = f"recon/{task_id}/output.splat"
            self.minio.upload_file(splat_path, object_name)

            # 9. 生成 presigned URL 并回写 SUCCESS
            presigned_url = self.minio.presigned_url(object_name, expires_hours=168)  # 7 天
            self.status_writer.write_success(
                task_id,
                result_url=presigned_url,
                stage="done",
                message="3D 重建完成",
            )
            logger.info("[%s] 重建任务完成 url=%s", task_id, presigned_url)

            return True

        except Exception as exc:
            logger.exception("[%s] 重建任务失败: %s", task_id, exc)
            self.status_writer.write_failed(task_id, error=str(exc), stage="recon")
            raise  # 重新抛出，让 StreamConsumer 记录日志（但消息仍会被 ACK）

        finally:
            # 释放 GPU 槽位
            try:
                self.gpu_slot.release(task_id)
            except Exception:
                logger.exception("[%s] GPU 槽位释放失败", task_id)

            # 清理临时目录
            if work_dir and work_dir.exists():
                try:
                    shutil.rmtree(work_dir, ignore_errors=True)
                    logger.debug("[%s] 临时目录已清理: %s", task_id, work_dir)
                except Exception:
                    logger.exception("[%s] 临时目录清理失败", task_id)

    # ------------------------------------------------------------------ 辅助
    def _download_images(self, images_prefix: str, local_dir: Path, task_id: str) -> None:
        """从 MinIO 下载指定前缀下的所有图像文件到本地目录。"""
        if not images_prefix:
            logger.warning("[%s] imagesPrefix 为空，使用 mock 图像", task_id)
            self._create_mock_images(local_dir)
            return

        objects = self.minio.client.list_objects(self.minio.bucket, prefix=images_prefix, recursive=True)
        count = 0
        for obj in objects:
            # 跳过目录标记
            if obj.object_name.endswith("/"):
                continue
            # 仅下载图像文件
            ext = Path(obj.object_name).suffix.lower()
            if ext not in (".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tiff"):
                continue
            filename = Path(obj.object_name).name
            local_path = local_dir / filename
            try:
                self.minio.download_file(obj.object_name, str(local_path))
                count += 1
            except Exception:
                logger.exception("[%s] 下载图像失败: %s", task_id, obj.object_name)

        if count == 0:
            logger.warning("[%s] 未下载到任何图像，使用 mock 图像", task_id)
            self._create_mock_images(local_dir)
        else:
            logger.info("[%s] 共下载 %d 张图像", task_id, count)

    @staticmethod
    def _create_mock_images(local_dir: Path) -> None:
        """生成占位图像文件（仅用于 mock 模式链路验证）。"""
        from PIL import Image

        for i in range(3):
            img = Image.new("RGB", (800, 600), color=(i * 80, 100, 200 - i * 50))
            img.save(local_dir / f"mock_{i:03d}.jpg", "JPEG")

    # ------------------------------------------------------------------ 启动
    def run(self) -> None:
        """启动重建工作器。"""
        logger.info("ReconWorker 启动 stream=%s", STREAM_NAME)
        consumer = StreamConsumer(
            stream_name=STREAM_NAME,
            config=self.config,
            handler=self.handle_message,
            status_writer=self.status_writer,
        )
        consumer.run_forever()


def main():
    """CLI 入口。"""
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO"),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )
    worker = ReconWorker()
    worker.run()


if __name__ == "__main__":
    main()
