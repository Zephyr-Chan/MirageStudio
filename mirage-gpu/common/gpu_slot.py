"""GPU 槽位申请/释放。

通过 Redis Lua 脚本原子化地申请与释放 GPU 槽位，与 Java 后端的
GpuSlotManager 操作相同的 Redis key：
  - ``gpu:slots:available``  : 可用槽位计数（Redis string / int）
  - ``gpu:slot:holders``     : 槽位持有者哈希表，field=taskId, value=持有信息JSON

申请逻辑（Lua 原子执行）：
  1. 读取 gpu:slots:available 当前值
  2. 若 > 0，则 DECR 并 HSET holders
  3. 否则返回失败

释放逻辑：
  1. 检查 holders 中是否存在该 taskId
  2. 存在则 HDEL + INCR available
  3. 不存在则幂等返回成功
"""

import json
import logging
import time
from typing import Optional

import redis

from .config import Config

logger = logging.getLogger(__name__)

# Redis key 常量（与 Java 侧 GpuSlotManager 保持一致）
KEY_AVAILABLE = "gpu:slots:available"
KEY_HOLDERS = "gpu:slot:holders"

# ---------------------------------------------------------------- 申请脚本
# KEYS[1] = gpu:slots:available
# KEYS[2] = gpu:slot:holders
# ARGV[1] = taskId
# ARGV[2] = holderInfo (JSON 字符串)
# 返回: "OK" | "NO_SLOT"
_ACQUIRE_SCRIPT = """
local available = tonumber(redis.call('GET', KEYS[1]) or '0')
if available <= 0 then
    return 'NO_SLOT'
end
redis.call('DECR', KEYS[1])
redis.call('HSET', KEYS[2], ARGV[1], ARGV[2])
return 'OK'
"""

# ---------------------------------------------------------------- 释放脚本
# KEYS[1] = gpu:slots:available
# KEYS[2] = gpu:slot:holders
# ARGV[1] = taskId
# 返回: "RELEASED" | "NOT_HELD"
_RELEASE_SCRIPT = """
local exists = redis.call('HEXISTS', KEYS[2], ARGV[1])
if exists == 0 then
    return 'NOT_HELD'
end
redis.call('HDEL', KEYS[2], ARGV[1])
redis.call('INCR', KEYS[1])
return 'RELEASED'
"""


class GpuSlotManager:
    """GPU 槽位管理器，对接 Java 后端 GpuSlotManager 的 Redis 协议。"""

    def __init__(
        self,
        redis_client: Optional[redis.Redis] = None,
        config: Optional[Config] = None,
    ) -> None:
        self.config = config or Config.from_env()
        self.redis_client = redis_client or redis.Redis.from_url(
            self.config.redis_url, decode_responses=True
        )
        # 预编译 Lua 脚本（EVALSHA 缓存优化）
        self._acquire_sha = self.redis_client.script_load(_ACQUIRE_SCRIPT)
        self._release_sha = self.redis_client.script_load(_RELEASE_SCRIPT)

    # ------------------------------------------------------------------ 申请
    def acquire(self, task_id: str, timeout_seconds: int = 120) -> bool:
        """申请一个 GPU 槽位，支持轮询等待。

        Parameters
        ----------
        task_id : str
            任务 ID，作为 holder 的唯一标识。
        timeout_seconds : int
            最大等待秒数，超时返回 False。

        Returns
        -------
        bool
            是否成功获取槽位。
        """
        deadline = time.time() + timeout_seconds
        poll_interval = 2  # 秒

        while time.time() < deadline:
            holder_info = json.dumps(
                {
                    "taskId": task_id,
                    "gpuId": self.config.gpu_id,
                    "consumer": self.config.consumer_name,
                    "acquiredAt": int(time.time() * 1000),
                }
            )
            result = self.redis_client.evalsha(
                self._acquire_sha,
                2,
                KEY_AVAILABLE,
                KEY_HOLDERS,
                task_id,
                holder_info,
            )
            if result == "OK":
                logger.info("GPU 槽位申请成功 taskId=%s gpu=%s", task_id, self.config.gpu_id)
                return True

            logger.debug("暂无可用 GPU 槽位 taskId=%s，%ss 后重试", task_id, poll_interval)
            time.sleep(poll_interval)

        logger.warning("GPU 槽位申请超时 taskId=%s timeout=%ss", task_id, timeout_seconds)
        return False

    def try_acquire(self, task_id: str) -> bool:
        """非阻塞申请，立即返回结果。"""
        holder_info = json.dumps(
            {
                "taskId": task_id,
                "gpuId": self.config.gpu_id,
                "consumer": self.config.consumer_name,
                "acquiredAt": int(time.time() * 1000),
            }
        )
        result = self.redis_client.evalsha(
            self._acquire_sha, 2, KEY_AVAILABLE, KEY_HOLDERS, task_id, holder_info
        )
        if result == "OK":
            logger.info("GPU 槽位申请成功 taskId=%s", task_id)
            return True
        logger.debug("无可用 GPU 槽位 taskId=%s", task_id)
        return False

    # ------------------------------------------------------------------ 释放
    def release(self, task_id: str) -> bool:
        """释放 GPU 槽位（幂等）。

        Returns
        -------
        bool
            True 表示本次释放了槽位；False 表示该任务未持有槽位（幂等）。
        """
        result = self.redis_client.evalsha(
            self._release_sha, 2, KEY_AVAILABLE, KEY_HOLDERS, task_id
        )
        if result == "RELEASED":
            logger.info("GPU 槽位已释放 taskId=%s", task_id)
            return True
        logger.debug("任务未持有槽位（幂等释放）taskId=%s", task_id)
        return False

    # ------------------------------------------------------------------ 查询
    def available_slots(self) -> int:
        """查询当前可用槽位数。"""
        val = self.redis_client.get(KEY_AVAILABLE)
        return int(val) if val else 0

    def get_holder(self, task_id: str) -> Optional[dict]:
        """查询某任务是否持有槽位及其信息。"""
        raw = self.redis_client.hget(KEY_HOLDERS, task_id)
        if raw:
            return json.loads(raw)
        return None
