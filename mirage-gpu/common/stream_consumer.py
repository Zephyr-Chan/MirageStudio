"""Redis Streams 消费者基类。

基于 XREADGROUP + XACK 的消费组模式实现可靠消费：
  - 启动时自动创建 stream 与消费组（若不存在）。
  - XREADGROUP 以阻塞方式拉取新消息。
  - 业务处理成功后 XACK 确认；异常时回写 FAILED 状态并仍然 XACK（避免毒丸消息卡死队列）。
  - 处理中断（SIGINT/SIGTERM）时优雅退出。

子类只需实现 handle_message() 即可完成具体业务逻辑。
"""

import logging
import signal
import time
from typing import Any, Callable, Dict, Optional

import redis
from redis.exceptions import ResponseError

from .config import Config
from .status_writer import StatusWriter

logger = logging.getLogger(__name__)

# 单次 XREADGROUP 阻塞超时（毫秒），用于周期性检查退出标志
_BLOCK_MS = 5000
# 每条 stream 一次拉取的最大条数
_BATCH_COUNT = 5


class StreamConsumer:
    """Redis Streams 消费组消费者基类。

    Parameters
    ----------
    stream_name : str
        要消费的 stream 名称，如 ``stream:recon``。
    config : Config
        全局配置实例。
    handler : Callable
        消息处理回调，签名为 ``(msg_id: str, fields: dict) -> bool``，
        返回 True 表示处理成功。若抛出异常则视为失败。
    """

    def __init__(
        self,
        stream_name: str,
        config: Config,
        handler: Callable[[str, Dict[str, Any]], bool],
        status_writer: Optional[StatusWriter] = None,
    ) -> None:
        self.stream_name = stream_name
        self.config = config
        self.handler = handler
        self._running = False

        # 独立的 Redis 连接（阻塞读取不应与业务写入共用同一连接）
        self.redis_client = redis.Redis.from_url(config.redis_url, decode_responses=True)

        # 状态回写器（用于异常时回写 FAILED）
        self.status_writer = status_writer or StatusWriter(
            redis_client=self.redis_client, config=config
        )

        # 确保消费组存在
        self._ensure_group()

    # ------------------------------------------------------------------ 初始化
    def _ensure_group(self) -> None:
        """创建 stream 与消费组（MKSTREAM 自动创建 stream）。"""
        try:
            self.redis_client.xgroup_create(
                self.stream_name, self.config.consumer_group, id="0", mkstream=True
            )
            logger.info(
                "消费组 %s 已在 stream %s 上创建",
                self.config.consumer_group,
                self.stream_name,
            )
        except ResponseError as e:
            # BUSYGROUP 表示消费组已存在，属正常情况
            if "BUSYGROUP" in str(e):
                logger.debug("消费组 %s 已存在，跳过创建", self.config.consumer_group)
            else:
                raise

    # ------------------------------------------------------------------ 生命周期
    def start(self) -> None:
        """启动消费循环，阻塞直到 stop() 被调用或收到终止信号。"""
        self._running = True
        logger.info(
            "开始消费 stream=%s group=%s consumer=%s",
            self.stream_name,
            self.config.consumer_group,
            self.config.consumer_name,
        )

        while self._running:
            try:
                self._consume_once()
            except redis.ConnectionError:
                logger.warning("Redis 连接断开，5s 后重试")
                time.sleep(5)
            except Exception:
                logger.exception("消费循环发生未预期异常，10s 后继续")
                time.sleep(10)

        logger.info("消费者已停止: stream=%s", self.stream_name)

    def stop(self) -> None:
        """优雅停止消费循环。"""
        logger.info("收到停止信号，准备退出...")
        self._running = False

    # ------------------------------------------------------------------ 消费
    def _consume_once(self) -> None:
        """执行一次 XREADGROUP 拉取并处理。"""
        # ">" 表示只读取从未投递给本消费组的新消息
        resp = self.redis_client.xreadgroup(
            groupname=self.config.consumer_group,
            consumername=self.config.consumer_name,
            streams={self.stream_name: ">"},
            count=_BATCH_COUNT,
            block=_BLOCK_MS,
        )

        if not resp:
            return  # 超时，无新消息

        # resp 格式: [(stream_name, [(msg_id, {field: value, ...}), ...]), ...]
        for _stream, messages in resp:
            for msg_id, fields in messages:
                self._process_message(msg_id, fields)

    def _process_message(self, msg_id: str, fields: Dict[str, Any]) -> None:
        """处理单条消息：调用 handler，成功则 XACK，失败则回写 FAILED 后 XACK。"""
        task_id = fields.get("taskId", "unknown")
        logger.info("收到消息 msg_id=%s taskId=%s", msg_id, task_id)

        try:
            success = self.handler(msg_id, fields)
            if success is False:
                raise RuntimeError("handler 返回 False，视为处理失败")
            logger.info("消息处理成功 msg_id=%s taskId=%s", msg_id, task_id)
        except Exception as exc:
            logger.exception("消息处理失败 msg_id=%s taskId=%s: %s", msg_id, task_id, exc)
            # 回写 FAILED 状态（含错误信息），尽量不让异常影响后续消息
            try:
                self.status_writer.write_failed(
                    task_id=task_id,
                    error=str(exc),
                    stage=fields.get("stage", "unknown"),
                )
            except Exception:
                logger.exception("回写 FAILED 状态时出错 taskId=%s", task_id)
        finally:
            # 无论成功/失败都 ACK，避免毒丸消息阻塞队列
            # （失败已通过状态回写告知 Java 后端，无需重复投递）
            try:
                self.redis_client.xack(self.stream_name, self.config.consumer_group, msg_id)
                logger.debug("已 ACK msg_id=%s", msg_id)
            except Exception:
                logger.exception("XACK 失败 msg_id=%s", msg_id)

    # ------------------------------------------------------------------ 信号处理
    def install_signal_handlers(self) -> None:
        """注册 SIGINT / SIGTERM 信号处理，实现优雅退出。"""

        def _handle(signum, frame):
            logger.info("收到信号 %s", signum)
            self.stop()

        signal.signal(signal.SIGINT, _handle)
        signal.signal(signal.SIGTERM, _handle)

    def run_forever(self) -> None:
        """便捷方法：注册信号处理 + 启动消费。"""
        self.install_signal_handlers()
        self.start()
