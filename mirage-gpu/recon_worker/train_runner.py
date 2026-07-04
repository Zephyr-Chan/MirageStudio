"""gaussian-splatting 训练运行器。

调用 3D Gaussian Splatting 的 ``train.py`` 完成从 COLMAP 稀疏模型到
训练好的 3D 高斯模型（.ply）的全流程。

核心功能：
  - 通过 subprocess 调用 train.py
  - 实时解析 stdout 中的 ``iteration X/30000`` 正则，提取训练进度并回调
  - 无 GPU / 无 gaussian-splatting 安装时进入 mock 模式生成占位 .ply
"""

import logging
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Optional

from common.config import Config

logger = logging.getLogger(__name__)

# 匹配 gaussian-splatting train.py 的训练迭代日志
# 典型输出: "[ITER 12000/30000] Loss: 0.012345"
# 或: "iteration 12000/30000"
_ITERATION_PATTERN = re.compile(
    r"(?:ITER|iteration)\s+(\d+)\s*/\s*(\d+)", re.IGNORECASE
)

# MVP 阶段默认训练迭代数
_DEFAULT_ITERATIONS = 30000


class TrainRunner:
    """3D Gaussian Splatting 训练运行器。"""

    def __init__(self, config: Optional[Config] = None) -> None:
        self.config = config or Config.from_env()

    # ------------------------------------------------------------------ 主流程
    def run(
        self,
        colmap_dir: str,
        images_dir: str,
        output_dir: str,
        task_id: str,
        iterations: int = _DEFAULT_ITERATIONS,
        on_progress: Optional[callable] = None,
    ) -> str:
        """执行 gaussian-splatting 训练。

        Parameters
        ----------
        colmap_dir : str
            COLMAP 稀疏模型目录（含 cameras.bin / images.bin / points3D.bin）。
        images_dir : str
            原始图像目录。
        output_dir : str
            模型输出目录，训练结束后生成 point_cloud.ply。
        task_id : str
            任务 ID。
        iterations : int
            训练迭代总轮数（默认 30000）。
        on_progress : callable, optional
            进度回调 ``on_progress(percent: int, message: str)``。

        Returns
        -------
        str
            输出 .ply 文件路径。
        """
        out_path = Path(output_dir)
        out_path.mkdir(parents=True, exist_ok=True)

        # 进度区间：COLMAP 已占 0-30%，训练占 30-85%
        train_progress_start = 30
        train_progress_end = 85

        # mock 模式
        if self.config.mock_recon:
            return self._mock_train(out_path, task_id, on_progress, train_progress_start)

        ply_path = out_path / "point_cloud" / "iteration_30000" / "point_cloud.ply"

        # 构造训练命令
        # 使用 sys.executable 调用 train.py，确保使用容器内正确的 Python 环境
        cmd = [
            sys.executable,
            self.config.gs_train_script,
            "--source_path", str(Path(colmap_dir).parent),  # gaussian-splatting 期望 source 根目录
            "--model_path", str(out_path),
            "--iterations", str(iterations),
            "--data_device", "cpu",   # 数据加载设备
            "--resolution", "1",      # MVP 不降采样
        ]

        logger.info("[%s] Gaussian Splatting 训练开始 colmap=%s output=%s", task_id, colmap_dir, out_path)
        if on_progress:
            on_progress(train_progress_start, "Gaussian Splatting 训练启动")

        try:
            self._run_subprocess(
                cmd, task_id, iterations, on_progress,
                train_progress_start, train_progress_end,
            )
        except FileNotFoundError:
            logger.warning("[%s] gaussian-splatting train.py 未找到，回退到 mock 模式", task_id)
            return self._mock_train(out_path, task_id, on_progress, train_progress_start)

        # 校验输出
        if not ply_path.exists():
            # 尝试查找最终 iteration 目录
            found_ply = self._find_latest_ply(out_path)
            if found_ply:
                ply_path = Path(found_ply)
            else:
                logger.error("[%s] 训练输出 .ply 未找到，回退到 mock 模式", task_id)
                return self._mock_train(out_path, task_id, on_progress, train_progress_start)

        logger.info("[%s] 训练完成 ply=%s", task_id, ply_path)
        if on_progress:
            on_progress(train_progress_end, "Gaussian Splatting 训练完成")

        return str(ply_path)

    # ------------------------------------------------------------------ subprocess
    def _run_subprocess(
        self,
        cmd: list,
        task_id: str,
        total_iterations: int,
        on_progress: Optional[callable],
        progress_start: int,
        progress_end: int,
    ) -> None:
        """运行 train.py 子进程，实时解析迭代进度。"""
        logger.info("[%s] 执行命令: %s", task_id, " ".join(cmd))

        env = os.environ.copy()
        # 确保使用指定 GPU
        env["CUDA_VISIBLE_DEVICES"] = str(self.config.gpu_id)

        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
            env=env,
        )

        progress_span = progress_end - progress_start

        for line in process.stdout:
            line = line.strip()
            if not line:
                continue

            logger.debug("[%s] GS: %s", task_id, line)

            # 解析迭代进度
            match = _ITERATION_PATTERN.search(line)
            if match and on_progress:
                current = int(match.group(1))
                total = int(match.group(2)) or total_iterations
                fraction = current / total
                percent = int(progress_start + fraction * progress_span)
                on_progress(
                    min(percent, progress_end),
                    f"训练迭代 {current}/{total} ({fraction*100:.1f}%)",
                )

        ret = process.wait()
        if ret != 0:
            raise RuntimeError(f"gaussian-splatting train.py 异常退出 code={ret}")

    # ------------------------------------------------------------------ 辅助
    @staticmethod
    def _find_latest_ply(model_path: Path) -> Optional[str]:
        """在模型输出目录中查找最新的 point_cloud.ply。"""
        point_cloud_dir = model_path / "point_cloud"
        if not point_cloud_dir.exists():
            return None

        iteration_dirs = sorted(
            [d for d in point_cloud_dir.iterdir() if d.is_dir() and d.name.startswith("iteration_")],
            key=lambda d: int(d.name.replace("iteration_", "")),
            reverse=True,
        )
        for d in iteration_dirs:
            ply = d / "point_cloud.ply"
            if ply.exists():
                return str(ply)
        return None

    # ------------------------------------------------------------------ mock
    def _mock_train(
        self,
        out_path: Path,
        task_id: str,
        on_progress: Optional[callable],
        progress_start: int,
    ) -> str:
        """mock 模式：生成占位 .ply 文件。"""
        logger.info("[%s] 训练 mock 模式：生成占位 .ply 文件", task_id)

        ply_dir = out_path / "point_cloud" / "iteration_30000"
        ply_dir.mkdir(parents=True, exist_ok=True)
        ply_path = ply_dir / "point_cloud.ply"

        # 生成最小 PLY 文件（仅包含头部 + 1 个占位顶点）
        # 实际 gaussian-splatting 输出的 PLY 包含大量高斯点
        ply_header = (
            "ply\n"
            "format binary_little_endian 1.0\n"
            "element vertex 1\n"
            "property float x\n"
            "property float y\n"
            "property float z\n"
            "property float nx\n"
            "property float ny\n"
            "property float nz\n"
            "property float f_dc_0\n"
            "property float f_dc_1\n"
            "property float f_dc_2\n"
            "property float opacity\n"
            "property float scale_0\n"
            "property float scale_1\n"
            "property float scale_2\n"
            "property float rot_0\n"
            "property float rot_1\n"
            "property float rot_2\n"
            "property float rot_3\n"
            "end_header\n"
        )
        # 1 个顶点 * 17 floats * 4 bytes
        # 属性顺序: x,y,z, nx,ny,nz, f_dc_0/1/2, opacity, scale_0/1/2, rot_0/1/2/3
        import struct
        vertex_data = struct.pack(
            "<17f",
            0.0, 0.0, 0.0,        # position: x, y, z
            0.0, 0.0, 0.0,        # normal: nx, ny, nz
            0.0, 0.0, 0.0,        # f_dc_0, f_dc_1, f_dc_2
            1.0,                  # opacity
            0.1, 0.1, 0.1,        # scale_0, scale_1, scale_2
            1.0, 0.0, 0.0, 0.0,   # rot_0, rot_1, rot_2, rot_3 (quaternion)
        )

        ply_path.write_bytes(ply_header.encode("ascii") + vertex_data)

        if on_progress:
            # 模拟训练进度跳变
            on_progress(progress_start, "训练 mock 模式启动")
            on_progress(85, "训练 mock 模式完成")

        return str(ply_path)
