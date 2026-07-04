"""任务状态回写。

双通道回写机制：
  1. HTTP POST -> Java 后端 ``/api/internal/agent/task-status``
     （持久化到数据库，供 Agent / 前端查询）
  2. Redis ``task:status:{taskId}`` 写入 JSON 快照
     （供前端轮询 / WebSocket 推送近实时状态）

两个通道相互独立：HTTP 失败不影响 Redis 写入，反之亦然，
确保至少有一个通道能传达状态。HTTP 调用使用 httpx 带超时与重试。
"""

import json
import logging
import time
from typing import Any, Optional

import httpx
import redis

from .config import Config

logger = logging.getLogger(__name__)

# HTTP 调用超时与重试参数
_HTTP_TIMEOUT = 10.0  # 秒
_HTTP_RETRIES = 3

# Redis 状态键 TTL（7 天，避免无限堆积）
_REDIS_TTL = 7 * 24 * 3600

# 任务状态枚举（与 Java 后端 TaskStatus 对齐）
STATUS_PENDING = "PENDING"
STATUS_RUNNING = "RUNNING"
STATUS_SUCCESS = "SUCCESS"
STATUS_FAILED = "FAILED"


class StatusWriter:
    """任务状态回写器：HTTP + Redis 双写。"""

    def __init__(
        self,
        redis_client: Optional[redis.Redis] = None,
        config: Optional[Config] = None,
    ) -> None:
        self.config = config or Config.from_env()
        self.redis_client = redis_client or redis.Redis.from_url(
            self.config.redis_url, decode_responses=True
        )
        self._http_client = httpx.Client(timeout=_HTTP_TIMEOUT)

    # ------------------------------------------------------------------ 核心
    def write(
        self,
        task_id: str,
        status: str,
        stage: Optional[str] = None,
        progress: Optional[int] = None,
        message: Optional[str] = None,
        result_url: Optional[str] = None,
        extra: Optional[dict] = None,
    ) -> None:
        """写入任务状态（双通道）。

        Parameters
        ----------
        task_id : str
            任务 ID。
        status : str
            状态枚举值：PENDING / RUNNING / SUCCESS / FAILED。
        stage : str, optional
            当前阶段，如 ``colmap`` / ``train`` / ``splat_export`` / ``comfy_render``。
        progress : int, optional
            进度百分比 0-100。
        message : str, optional
            附加消息。
        result_url : str, optional
            结果文件 URL（MinIO presigned URL 等）。
        extra : dict, optional
            额外自定义字段。
        """
        payload = self._build_payload(
            task_id, status, stage, progress, message, result_url, extra
        )

        # --- 通道1: Redis（优先，近实时）---
        self._write_redis(task_id, payload)

        # --- 通道2: HTTP -> Java 后端（持久化）---
        self._write_http(task_id, payload)

    # ------------------------------------------------------------------ 便捷方法
    def write_running(
        self, task_id: str, stage: str, progress: int = 0, message: Optional[str] = None
    ) -> None:
        """写入 RUNNING 状态。"""
        self.write(task_id, STATUS_RUNNING, stage=stage, progress=progress, message=message)

    def write_progress(
        self, task_id: str, stage: str, progress: int, message: Optional[str] = None
    ) -> None:
        """写入进度更新（RUNNING 状态的快捷方法）。"""
        self.write(task_id, STATUS_RUNNING, stage=stage, progress=progress, message=message)

    def write_success(
        self,
        task_id: str,
        result_url: Optional[str] = None,
        stage: Optional[str] = None,
        message: Optional[str] = None,
    ) -> None:
        """写入 SUCCESS 状态。"""
        self.write(
            task_id,
            STATUS_SUCCESS,
            stage=stage,
            progress=100,
            result_url=result_url,
            message=message,
        )

    def write_failed(
        self,
        task_id: str,
        error: str,
        stage: Optional[str] = None,
        progress: Optional[int] = None,
    ) -> None:
        """写入 FAILED 状态。"""
        self.write(
            task_id,
            STATUS_FAILED,
            stage=stage,
            progress=progress,
            message=error,
        )

    # ------------------------------------------------------------------ 内部实现
    def _build_payload(
        self,
        task_id: str,
        status: str,
        stage: Optional[str],
        progress: Optional[int],
        message: Optional[str],
        result_url: Optional[str],
        extra: Optional[dict],
    ) -> dict:
        """构建状态 JSON payload。"""
        payload: dict[str, Any] = {
            "taskId": task_id,
            "status": status,
            "timestamp": int(time.time() * 1000),
        }
        if stage is not None:
            payload["stage"] = stage
        if progress is not None:
            payload["progress"] = max(0, min(100, int(progress)))
        if message is not None:
            payload["message"] = message
        if result_url is not None:
            payload["resultUrl"] = result_url
        if extra:
            payload["extra"] = extra
        return payload

    def _write_redis(self, task_id: str, payload: dict) -> None:
        """写入 Redis task:status:{taskId}。"""
        redis_key = f"task:status:{task_id}"
        try:
            self.redis_client.setex(redis_key, _REDIS_TTL, json.dumps(payload, ensure_ascii=False))
            logger.debug("Redis 状态已写入 key=%s status=%s", redis_key, payload["status"])
        except Exception:
            logger.exception("Redis 状态写入失败 key=%s", redis_key)

    def _write_http(self, task_id: str, payload: dict) -> None:
        """HTTP POST 回写 Java 后端（带重试）。"""
        url = f"{self.config.java_backend_url.rstrip('/')}/api/internal/agent/task-status"
        last_exc: Optional[Exception] = None

        for attempt in range(1, _HTTP_RETRIES + 1):
            try:
                resp = self._http_client.post(url, json=payload)
                if resp.status_code < 300:
                    logger.debug(
                        "HTTP 状态回写成功 taskId=%s status=%s attempt=%d",
                        task_id,
                        payload["status"],
                        attempt,
                    )
                    return
                logger.warning(
                    "HTTP 状态回写非 2xx taskId=%s code=%d body=%s attempt=%d",
                    task_id,
                    resp.status_code,
                    resp.text[:200],
                    attempt,
                )
            except Exception as exc:
                last_exc = exc
                logger.warning(
                    "HTTP 状态回写异常 taskId=%s attempt=%d: %s",
                    task_id,
                    attempt,
                    exc,
                )

            if attempt < _HTTP_RETRIES:
                time.sleep(1 * attempt)  # 线性退避

        logger.error(
            "HTTP 状态回写最终失败 taskId=%s last_error=%s", task_id, last_exc
        )

    # ------------------------------------------------------------------ 资源清理
    def close(self) -> None:
        """关闭 HTTP 客户端。"""
        self._http_client.close()

    def __del__(self):
        try:
            self.close()
        except Exception:
            pass
