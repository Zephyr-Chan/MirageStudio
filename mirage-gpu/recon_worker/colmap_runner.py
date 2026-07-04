"""COLMAP SfM 运行器。

调用 COLMAP 的 ``automatic_reconstructor`` 命令完成从图像集到稀疏/稠密重建
的完整流程，输出 COLMAP 稀疏模型目录（含 cameras.bin / images.bin / points3D.bin）。

MVP 阶段直接通过 subprocess 调用 COLMAP CLI，后续可替换为 Python 绑定。
无 COLMAP 安装时进入 mock 模式，生成占位文件以打通后续链路。
"""

import logging
import os
import shutil
import subprocess
from pathlib import Path
from typing import Optional

from common.config import Config

logger = logging.getLogger(__name__)


class ColmapRunner:
    """COLMAP SfM 重建运行器。"""

    def __init__(self, config: Optional[Config] = None) -> None:
        self.config = config or Config.from_env()

    # ------------------------------------------------------------------ 主流程
    def run(
        self,
        images_dir: str,
        workspace_dir: str,
        task_id: str,
        on_progress: Optional[callable] = None,
    ) -> str:
        """执行 COLMAP automatic_reconstructor 全流程。

        Parameters
        ----------
        images_dir : str
            输入图像目录路径。
        workspace_dir : str
            COLMAP 工作空间目录（输出 sparse 模型等）。
        task_id : str
            任务 ID，用于日志标识。
        on_progress : callable, optional
            进度回调 ``on_progress(percent: int, message: str)``。

        Returns
        -------
        str
            稀疏模型目录路径（含 cameras.bin 等）。
        """
        workspace = Path(workspace_dir)
        workspace.mkdir(parents=True, exist_ok=True)

        # mock 模式：跳过 COLMAP，生成占位模型目录
        if self.config.mock_recon:
            return self._mock_run(images_dir, workspace, task_id, on_progress)

        sparse_dir = workspace / "sparse" / "0"
        sparse_dir.mkdir(parents=True, exist_ok=True)

        logger.info("[%s] COLMAP 重建开始 images=%s workspace=%s", task_id, images_dir, workspace)
        if on_progress:
            on_progress(0, "COLMAP 重建启动")

        # 构造命令：colmap automatic_reconstructor
        cmd = [
            self.config.colmap_bin,
            "automatic_reconstructor",
            "--workspace_path", str(workspace),
            "--image_path", str(images_dir),
            "--data_type", "individual",  # 单张照片模式（非视频帧）
            "--quality", "medium",         # MVP 阶段用 medium 平衡速度与质量
            "--single_camera", "1",        # 假设单相机
            "--dense", "0",                # MVP 不做稠密重建，只需稀疏位姿
        ]

        try:
            self._run_subprocess(cmd, task_id, on_progress, progress_range=(0, 30))
        except FileNotFoundError:
            logger.warning("[%s] COLMAP 未安装，回退到 mock 模式", task_id)
            return self._mock_run(images_dir, workspace, task_id, on_progress)

        # 校验输出
        if not (sparse_dir / "cameras.bin").exists():
            logger.error("[%s] COLMAP 输出缺失 cameras.bin，回退到 mock 模式", task_id)
            return self._mock_run(images_dir, workspace, task_id, on_progress)

        logger.info("[%s] COLMAP 重建完成 sparse_dir=%s", task_id, sparse_dir)
        if on_progress:
            on_progress(30, "COLMAP 重建完成")

        return str(sparse_dir)

    # ------------------------------------------------------------------ subprocess
    def _run_subprocess(
        self,
        cmd: list,
        task_id: str,
        on_progress: Optional[callable],
        progress_range: tuple,
    ) -> None:
        """运行子进程并解析输出日志驱动进度回调。"""
        logger.info("[%s] 执行命令: %s", task_id, " ".join(cmd))
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )

        start_pct, end_pct = progress_range
        for line in process.stdout:
            line = line.strip()
            if line:
                logger.debug("[%s] COLMAP: %s", task_id, line)
                # 简单的进度估算：COLMAP 无标准进度输出，按日志关键字粗略映射
                if on_progress:
                    lower = line.lower()
                    if "feature extraction" in lower:
                        on_progress(start_pct + 2, "特征提取中")
                    elif "feature matching" in lower:
                        on_progress(start_pct + 5, "特征匹配中")
                    elif "mapping" in lower or "incremental" in lower:
                        on_progress(start_pct + 10, "SfM 增量重建中")

        ret = process.wait()
        if ret != 0:
            raise RuntimeError(f"COLMAP 进程异常退出 code={ret}")

    # ------------------------------------------------------------------ mock
    def _mock_run(
        self,
        images_dir: str,
        workspace: Path,
        task_id: str,
        on_progress: Optional[callable],
    ) -> str:
        """mock 模式：生成占位稀疏模型文件。"""
        logger.info("[%s] COLMAP mock 模式：生成占位文件", task_id)
        if on_progress:
            on_progress(5, "COLMAP mock 模式启动")

        sparse_dir = workspace / "sparse" / "0"
        sparse_dir.mkdir(parents=True, exist_ok=True)

        # 生成占位二进制文件（COLMAP cameras.bin / images.bin / points3D.bin）
        # 写入最小合法头，实际生产中替换为真实 COLMAP 输出
        for name in ("cameras.bin", "images.bin", "points3D.bin"):
            (sparse_dir / name).write_bytes(b"MOCK_COLMAP_" + name.encode())

        if on_progress:
            on_progress(30, "COLMAP mock 模式完成")

        return str(sparse_dir)
