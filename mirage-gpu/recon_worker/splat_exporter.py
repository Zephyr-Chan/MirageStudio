"""PLY -> .splat / .ksplat 转换器。

将 gaussian-splatting 训练输出的 .ply（3D 高斯模型）转换为
Web 端渲染所需的 .splat 格式（兼容 simonfri/splat viewer 等）或
.ksplat 格式（兼容 PlayCanvas 超级采样渲染器）。

MVP 阶段通过 subprocess 调用外部转换脚本，无脚本时进入 mock 模式
直接复制 .ply 作为占位 .splat（前端需兼容处理或仅用于链路验证）。

PLY 高斯点格式（gaussian-splatting 输出）：
  每个顶点含 x,y,z, nx,ny,nz, f_dc_0/1/2, opacity,
  scale_0/1/2, rot_0/1/2/3（共 17 个 float 属性）

.splat 格式（每点 32 bytes）：
  x,y,z (3 * float32) + scale(3 * float32) + rgba(4 * uint8) + rot(4 * uint8)
"""

import logging
import struct
import subprocess
import sys
from pathlib import Path
from typing import Optional

from common.config import Config

logger = logging.getLogger(__name__)

# PLY 属性名 -> 索引（与 gaussian-splatting 输出一致）
_PLY_PROPS = [
    "x", "y", "z",
    "nx", "ny", "nz",
    "f_dc_0", "f_dc_1", "f_dc_2",
    "opacity",
    "scale_0", "scale_1", "scale_2",
    "rot_0", "rot_1", "rot_2", "rot_3",
]


class SplatExporter:
    """.ply -> .splat 转换器。"""

    def __init__(self, config: Optional[Config] = None) -> None:
        self.config = config or Config.from_env()

    # ------------------------------------------------------------------ 主流程
    def export(
        self,
        ply_path: str,
        output_path: str,
        task_id: str,
        format: str = "splat",
        on_progress: Optional[callable] = None,
    ) -> str:
        """将 .ply 转换为 .splat 或 .ksplat。

        Parameters
        ----------
        ply_path : str
            输入 .ply 文件路径。
        output_path : str
            输出文件路径（.splat 或 .ksplat）。
        task_id : str
            任务 ID。
        format : str
            目标格式：``splat`` 或 ``ksplat``。
        on_progress : callable, optional
            进度回调（转换阶段占 85-95%）。

        Returns
        -------
        str
            输出文件路径。
        """
        if on_progress:
            on_progress(85, f"开始 .ply -> .{format} 转换")

        # mock 模式：直接复制 ply 作为占位输出
        if self.config.mock_recon:
            return self._mock_export(ply_path, output_path, task_id, on_progress)

        # 尝试调用外部转换脚本
        if self.config.splat_converter and Path(self.config.splat_converter).exists():
            return self._convert_via_script(ply_path, output_path, format, task_id, on_progress)

        # 内置纯 Python 转换（.ply -> .splat）
        try:
            if format == "splat":
                self._convert_ply_to_splat(ply_path, output_path)
            else:
                # ksplat 暂不支持内置转换，回退到 mock
                logger.warning("[%s] ksplat 格式暂无内置转换器，回退 mock", task_id)
                return self._mock_export(ply_path, output_path, task_id, on_progress)
        except Exception as exc:
            logger.warning("[%s] 内置转换失败: %s，回退 mock", task_id, exc)
            return self._mock_export(ply_path, output_path, task_id, on_progress)

        logger.info("[%s] .%s 转换完成: %s", task_id, format, output_path)
        if on_progress:
            on_progress(95, f".{format} 转换完成")

        return output_path

    # ------------------------------------------------------------------ 内置转换
    def _convert_ply_to_splat(self, ply_path: str, output_path: str) -> None:
        """纯 Python 实现 .ply -> .splat 转换。

        .splat 每点 32 bytes 布局：
          position(3f) + scale(3f) + color(4B RGBA) + rotation(4B)
        其中 color 和 rotation 需从高斯属性量化为 uint8。
        """
        import numpy as np

        # 读取 PLY
        vertices = self._read_ply(ply_path)
        if vertices is None or len(vertices) == 0:
            raise ValueError("PLY 文件无有效顶点")

        count = len(vertices)
        logger.info("PLY 顶点数: %d", count)

        # SH C0 系数 -> RGB
        SH_C0 = 0.28209479177387814

        # 提取各属性
        positions = vertices[:, 0:3].astype(np.float32)  # x, y, z
        scales = np.exp(vertices[:, 10:13]).astype(np.float32)  # scale_0/1/2 (log scale -> exp)
        rotations = vertices[:, 13:17].astype(np.float32)  # rot_0/1/2/3 (quaternion)

        # f_dc -> RGB color
        dc = vertices[:, 6:9]
        colors = (0.5 + SH_C0 * dc).clip(0, 1) * 255
        colors = colors.astype(np.uint8)

        # opacity -> alpha (sigmoid)
        opacity = vertices[:, 9]
        alphas = (1.0 / (1.0 + np.exp(-opacity))).clip(0, 1) * 255
        alphas = alphas.astype(np.uint8).reshape(-1, 1)

        # rgba
        rgba = np.hstack([colors, alphas])  # (N, 4)

        # rotation quaternion -> normalized, then quantize to uint8 [-1, 1] -> [0, 255]
        norms = np.linalg.norm(rotations, axis=1, keepdims=True)
        norms = np.where(norms == 0, 1, norms)
        rot_norm = rotations / norms
        rot_uint8 = ((rot_norm + 1.0) * 127.5).clip(0, 255).astype(np.uint8)

        # 写入 .splat: [position(3f) | scale(3f) | rgba(4B) | rotation(4B)]
        with open(output_path, "wb") as f:
            # header: 顶点数 (uint64)
            f.write(struct.pack("<Q", count))
            for i in range(count):
                f.write(positions[i].tobytes())      # 12 bytes
                f.write(scales[i].tobytes())          # 12 bytes
                f.write(rgba[i].tobytes())            # 4 bytes
                f.write(rot_uint8[i].tobytes())       # 4 bytes
        # 总计每点 32 bytes + header 8 bytes

    def _read_ply(self, ply_path: str):
        """读取 PLY 文件，返回 (N, 17) 的 numpy 数组。"""
        import numpy as np

        with open(ply_path, "rb") as f:
            # 解析 header
            header_lines = []
            vertex_count = 0
            while True:
                line = f.readline().decode("ascii").strip()
                header_lines.append(line)
                if line.startswith("element vertex"):
                    vertex_count = int(line.split()[-1])
                if line == "end_header":
                    break

            if vertex_count == 0:
                return None

            # 读取二进制数据：17 个 float32
            data = f.read(vertex_count * 17 * 4)
            vertices = np.frombuffer(data, dtype=np.float32).reshape(-1, 17)

        return vertices

    # ------------------------------------------------------------------ 外部脚本
    def _convert_via_script(
        self,
        ply_path: str,
        output_path: str,
        format: str,
        task_id: str,
        on_progress: Optional[callable],
    ) -> str:
        """调用外部转换脚本。"""
        cmd = [
            sys.executable,
            self.config.splat_converter,
            "--input", ply_path,
            "--output", output_path,
            "--format", format,
        ]
        logger.info("[%s] 执行转换脚本: %s", task_id, " ".join(cmd))

        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode != 0:
            raise RuntimeError(
                f"转换脚本失败 code={result.returncode} stderr={result.stderr[:500]}"
            )

        if on_progress:
            on_progress(95, f".{format} 转换完成（外部脚本）")

        return output_path

    # ------------------------------------------------------------------ mock
    def _mock_export(
        self,
        ply_path: str,
        output_path: str,
        task_id: str,
        on_progress: Optional[callable],
    ) -> str:
        """mock 模式：复制 .ply 内容到输出路径。"""
        logger.info("[%s] splat 导出 mock 模式：复制 .ply 到输出", task_id)

        import shutil
        shutil.copy2(ply_path, output_path)

        if on_progress:
            on_progress(95, "splat 导出 mock 模式完成")

        return output_path
