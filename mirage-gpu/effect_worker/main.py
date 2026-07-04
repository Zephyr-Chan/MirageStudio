"""effect_worker 入口：ComfyUI 特效渲染工作器。

消费 ``stream:effect`` 消息，协调完整渲染链路：
  1. 从 MinIO 下载输入图像（如有）
  2. 上传图像到 ComfyUI input 目录
  3. Jinja2 渲染工作流模板（填充 prompt/seed/input_image/cn_strength）
  4. POST /prompt 提交工作流
  5. WebSocket 监听渲染进度
  6. GET /history + GET /view 获取输出图像
  7. 上传结果到 MinIO 并回写 SUCCESS + presigned URL

无 ComfyUI 时使用 mock 模式生成占位图像，确保端到端链路可验证。

消息字段（Java 后端投递）:
  taskId       : 任务 ID
  prompt       : 正向提示词
  inputImage   : MinIO 输入图像对象路径（可选）
  template     : 工作流模板名（如 cyberpunk_style.json，可选）
  seed         : 随机种子（可选，默认随机）
  cnStrength   : ControlNet 强度（可选）
  steps        : 采样步数（可选）
  cfg          : CFG scale（可选）
"""

import logging
import os
import random
import shutil
import tempfile
from pathlib import Path
from typing import Any, Dict, Optional

from common.config import Config, settings
from common.gpu_slot import GpuSlotManager
from common.minio_client import MinioClient
from common.status_writer import StatusWriter
from common.stream_consumer import StreamConsumer

from .comfy_client import ComfyClient
from .workflow_renderer import WorkflowRenderer

logger = logging.getLogger(__name__)

STREAM_NAME = "stream:effect"

# 默认工作流模板
_DEFAULT_TEMPLATE = "cyberpunk_style.json"


class EffectWorker:
    """ComfyUI 特效渲染工作器。"""

    def __init__(self, config: Config = None) -> None:
        self.config = config or settings

        # 共享组件
        self.status_writer = StatusWriter(config=self.config)
        self.gpu_slot = GpuSlotManager(config=self.config)
        self.minio = MinioClient(config=self.config)

        # 特效组件
        self.comfy_client = ComfyClient(config=self.config)
        self.workflow_renderer = WorkflowRenderer(config=self.config)

    # ------------------------------------------------------------------ 消息处理
    def handle_message(self, msg_id: str, fields: Dict[str, Any]) -> bool:
        """处理单条特效渲染任务消息。"""
        task_id = fields.get("taskId", "")
        if not task_id:
            logger.error("消息缺少 taskId 字段 msg_id=%s", msg_id)
            return True

        prompt = fields.get("prompt", "")
        input_image_obj = fields.get("inputImage", "")
        template_name = fields.get("template", _DEFAULT_TEMPLATE)
        seed = int(fields.get("seed", str(random.randint(0, 2**32 - 1))))
        cn_strength = float(fields.get("cnStrength", str(self.config.default_cn_strength)))
        steps = int(fields.get("steps", "20"))
        cfg = float(fields.get("cfg", "7.0"))
        negative_prompt = fields.get("negativePrompt", "")

        logger.info(
            "[%s] 开始特效渲染 prompt=%s... template=%s seed=%d",
            task_id,
            prompt[:30],
            template_name,
            seed,
        )
        self.status_writer.write_running(task_id, stage="init", progress=0, message="渲染任务已接收")

        work_dir = None
        try:
            # 1. 申请 GPU 槽位
            self.status_writer.write_progress(task_id, stage="gpu_wait", progress=1, message="等待 GPU 槽位")
            if not self.gpu_slot.acquire(task_id, timeout_seconds=300):
                raise RuntimeError("GPU 槽位申请超时")
            logger.info("[%s] GPU 槽位已获取", task_id)

            work_dir = Path(tempfile.mkdtemp(prefix=f"effect_{task_id}_"))

            # 2. 确定是否 mock 模式
            use_mock = self.config.mock_effect or not self.comfy_client.health_check()
            if use_mock:
                logger.info("[%s] ComfyUI 不可用，使用 mock 模式", task_id)

            # 3. 处理输入图像
            input_image_filename = ""
            if input_image_obj:
                self.status_writer.write_progress(task_id, stage="download_input", progress=5, message="下载输入图像")
                local_input = work_dir / "input_image.png"
                try:
                    self.minio.download_file(input_image_obj, str(local_input))
                    if not use_mock:
                        self.status_writer.write_progress(task_id, stage="upload_comfy", progress=10, message="上传图像到 ComfyUI")
                        input_image_filename = self.comfy_client.upload_image(str(local_input))
                    else:
                        input_image_filename = "input_image.png"
                except Exception:
                    logger.exception("[%s] 输入图像处理失败，继续无图模式", task_id)

            # 4. 渲染工作流模板
            self.status_writer.write_progress(task_id, stage="render_workflow", progress=15, message="渲染工作流模板")
            workflow = self.workflow_renderer.render(
                template_name=template_name,
                prompt=prompt,
                seed=seed,
                input_image=input_image_filename,
                cn_strength=cn_strength,
                negative_prompt=negative_prompt,
                steps=steps,
                cfg=cfg,
            )

            # 5. 执行渲染
            if use_mock:
                result_images = self._mock_render(task_id)
            else:
                result_images = self._render_via_comfy(task_id, workflow)

            if not result_images:
                raise RuntimeError("未获取到渲染输出图像")

            # 6. 上传结果到 MinIO
            self.status_writer.write_progress(task_id, stage="upload_result", progress=95, message="上传渲染结果")
            object_name = f"effect/{task_id}/output.png"
            self.minio.upload_bytes(
                result_images[0],
                object_name,
                content_type="image/png",
            )

            # 7. 生成 presigned URL 并回写 SUCCESS
            presigned_url = self.minio.presigned_url(object_name, expires_hours=168)
            self.status_writer.write_success(
                task_id,
                result_url=presigned_url,
                stage="done",
                message="特效渲染完成",
            )
            logger.info("[%s] 渲染任务完成 url=%s", task_id, presigned_url)

            return True

        except Exception as exc:
            logger.exception("[%s] 渲染任务失败: %s", task_id, exc)
            self.status_writer.write_failed(task_id, error=str(exc), stage="effect")
            raise

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

    # ------------------------------------------------------------------ ComfyUI 渲染
    def _render_via_comfy(self, task_id: str, workflow: Dict[str, Any]) -> list:
        """通过 ComfyUI 执行渲染。"""

        def on_progress(value: int, max_val: int, message: str):
            # 进度区间 15% -> 90%
            if max_val > 0:
                fraction = value / max_val
            else:
                fraction = 0.5
            percent = int(15 + fraction * 75)
            self.status_writer.write_progress(task_id, stage="comfy_render", progress=percent, message=message)

        self.status_writer.write_progress(task_id, stage="comfy_render", progress=15, message="提交工作流到 ComfyUI")

        images = self.comfy_client.execute_workflow(workflow, on_progress=on_progress)
        logger.info("[%s] ComfyUI 渲染完成，获取 %d 张输出图像", task_id, len(images))
        return images

    # ------------------------------------------------------------------ mock 渲染
    def _mock_render(self, task_id: str) -> list:
        """mock 模式渲染：生成占位图像。"""
        self.status_writer.write_progress(task_id, stage="mock_render", progress=50, message="Mock 渲染中")
        img_bytes = self.comfy_client.mock_execute()
        self.status_writer.write_progress(task_id, stage="mock_render", progress=90, message="Mock 渲染完成")
        return [img_bytes]

    # ------------------------------------------------------------------ 启动
    def run(self) -> None:
        """启动特效工作器。"""
        logger.info("EffectWorker 启动 stream=%s", STREAM_NAME)
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
    worker = EffectWorker()
    worker.run()


if __name__ == "__main__":
    main()
