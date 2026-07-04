"""common 包：MirageStudio GPU 微服务共享基础设施。

包含 Redis Streams 消费、GPU 槽位管理、状态回写、MinIO 客户端与配置等
跨 worker 复用的能力。
"""

from .config import Config, settings

__all__ = ["Config", "settings"]
