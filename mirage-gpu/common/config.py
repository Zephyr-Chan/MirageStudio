"""配置模块：从环境变量读取所有外部依赖地址与运行参数。

MirageStudio GPU 微服务统一配置入口，供 recon_worker / effect_worker 共享。
所有参数均可通过环境变量覆盖，便于容器化部署与本地调试。
"""

import os
from dataclasses import dataclass, field
from typing import List


def _get_bool(key: str, default: bool = False) -> bool:
    """从环境变量读取布尔值，兼容 true/1/yes 等常见写法。"""
    val = os.getenv(key)
    if val is None:
        return default
    return val.strip().lower() in ("1", "true", "yes", "on")


@dataclass
class Config:
    """全局配置，所有字段均带默认值，可在环境中覆盖。"""

    # ------------------------------------------------------------------ Redis
    redis_url: str = "redis://localhost:6379/0"

    # ------------------------------------------------------------------ MinIO
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    minio_secure: bool = False
    minio_bucket: str = "mirage"

    # ---------------------------------------------------------------- ComfyUI
    comfyui_url: str = "http://localhost:8188"

    # ------------------------------------------------------------- Java 后端
    java_backend_url: str = "http://localhost:8080"

    # ------------------------------------------------------------------- GPU
    # 当前实例可用的 GPU 编号（用于 Lua 槽位申请时标识持有者维度）
    gpu_id: int = 0
    # 槽位总量上限（应与 Java 侧 GpuSlotManager 保持一致）
    gpu_slot_capacity: int = 1

    # --------------------------------------------------------------- 消费组
    consumer_group: str = "gpu-workers"
    consumer_name: str = "worker-1"

    # ------------------------------------------------------------- Mock 开关
    # 无 COLMAP / gaussian-splatting / ComfyUI 时使用占位实现，打通链路
    mock_recon: bool = False
    mock_effect: bool = False

    # ----------------------------------------------- recon 外部工具路径/命令
    colmap_bin: str = "colmap"
    # gaussian-splatting train.py 绝对路径
    gs_train_script: str = "/opt/gaussian-splatting/train.py"
    # .ply -> .splat 转换脚本路径（如 simonfri/splat 或自研脚本）
    splat_converter: str = "/opt/tools/gaussian_to_splat.py"

    # ----------------------------------------------- effect 渲染参数默认值
    default_cn_strength: float = 0.6

    @classmethod
    def from_env(cls) -> "Config":
        """从环境变量构建配置实例。"""
        return cls(
            redis_url=os.getenv("REDIS_URL", cls.redis_url),
            minio_endpoint=os.getenv("MINIO_ENDPOINT", cls.minio_endpoint),
            minio_access_key=os.getenv("MINIO_ACCESS_KEY", cls.minio_access_key),
            minio_secret_key=os.getenv("MINIO_SECRET_KEY", cls.minio_secret_key),
            minio_secure=_get_bool("MINIO_SECURE", cls.minio_secure),
            minio_bucket=os.getenv("MINIO_BUCKET", cls.minio_bucket),
            comfyui_url=os.getenv("COMFYUI_URL", cls.comfyui_url),
            java_backend_url=os.getenv("JAVA_BACKEND_URL", cls.java_backend_url),
            gpu_id=int(os.getenv("GPU_ID", cls.gpu_id)),
            gpu_slot_capacity=int(os.getenv("GPU_SLOT_CAPACITY", cls.gpu_slot_capacity)),
            consumer_group=os.getenv("CONSUMER_GROUP", cls.consumer_group),
            consumer_name=os.getenv("CONSUMER_NAME", cls.consumer_name),
            mock_recon=_get_bool("MOCK_RECON", cls.mock_recon),
            mock_effect=_get_bool("MOCK_EFFECT", cls.mock_effect),
            colmap_bin=os.getenv("COLMAP_BIN", cls.colmap_bin),
            gs_train_script=os.getenv("GS_TRAIN_SCRIPT", cls.gs_train_script),
            splat_converter=os.getenv("SPLAT_CONVERTER", cls.splat_converter),
            default_cn_strength=float(
                os.getenv("DEFAULT_CN_STRENGTH", cls.default_cn_strength)
            ),
        )


# 模块级单例，供各模块直接 import 使用
settings = Config.from_env()
