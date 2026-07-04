"""ComfyUI HTTP + WebSocket 客户端。

封装与 ComfyUI 的完整交互链路：
  - POST /upload/image        : 上传输入图像
  - POST /prompt              : 提交工作流（API Format JSON）
  - WS  /ws?clientId=xxx      : WebSocket 接收进度与完成事件
  - GET  /history/{prompt_id} : 查询执行历史与输出节点
  - GET  /view?filename=xxx   : 下载输出图像
  - POST /interrupt           : 中断当前执行

WebSocket 断线时采用指数退避重连（1s -> 60s）。
"""

import asyncio
import json
import logging
import os
import time
import urllib.parse
from pathlib import Path
from typing import Any, Dict, List, Optional

import httpx
import websockets

from common.config import Config

logger = logging.getLogger(__name__)

# WebSocket 重连退避参数
_WS_RECONNECT_MIN = 1   # 秒
_WS_RECONNECT_MAX = 60  # 秒


class ComfyClient:
    """ComfyUI API 客户端。"""

    def __init__(self, config: Optional[Config] = None) -> None:
        self.config = config or Config.from_env()
        self.base_url = self.config.comfyui_url.rstrip("/")
        # 从 HTTP URL 推导 WS URL
        self.ws_url = self._http_to_ws(self.base_url)
        self.client_id = f"mirage-{self.config.consumer_name}-{os.getpid()}"
        self._http = httpx.Client(timeout=30.0)

    # ------------------------------------------------------------------ 工具
    @staticmethod
    def _http_to_ws(http_url: str) -> str:
        """将 http(s):// URL 转为 ws(s):// URL。"""
        if http_url.startswith("https://"):
            return "wss://" + http_url[8:]
        elif http_url.startswith("http://"):
            return "ws://" + http_url[7:]
        return http_url

    @property
    def is_mock(self) -> bool:
        """是否处于 mock 模式（ComfyUI 不可用）。"""
        return self.config.mock_effect

    # ------------------------------------------------------------------ 健康检查
    def health_check(self) -> bool:
        """检查 ComfyUI 是否可用。"""
        try:
            resp = self._http.get(f"{self.base_url}/system_stats", timeout=5.0)
            return resp.status_code == 200
        except Exception:
            return False

    # ------------------------------------------------------------------ 上传图像
    def upload_image(self, image_path: str, overwrite: bool = False) -> str:
        """上传图像到 ComfyUI input 目录。

        Parameters
        ----------
        image_path : str
            本地图像文件路径。
        overwrite : bool
            是否覆盖同名文件。

        Returns
        -------
        str
            ComfyUI 中引用该图像的文件名。
        """
        filename = Path(image_path).name
        with open(image_path, "rb") as f:
            files = {"image": (filename, f, "application/octet-stream")}
            data = {"overwrite": "true" if overwrite else "false"}
            resp = self._http.post(f"{self.base_url}/upload/image", files=files, data=data)

        resp.raise_for_status()
        result = resp.json()
        uploaded_name = result.get("name", filename)
        logger.info("ComfyUI 图像上传成功: %s -> %s", filename, uploaded_name)
        return uploaded_name

    # ------------------------------------------------------------------ 提交工作流
    def queue_prompt(self, workflow: Dict[str, Any]) -> str:
        """提交工作流到 ComfyUI 执行队列。

        Parameters
        ----------
        workflow : dict
            ComfyUI API Format 工作流 JSON。

        Returns
        -------
        str
            prompt_id，用于后续查询历史与 WebSocket 跟踪。
        """
        payload = {"prompt": workflow, "client_id": self.client_id}
        resp = self._http.post(f"{self.base_url}/prompt", json=payload)
        resp.raise_for_status()
        result = resp.json()
        prompt_id = result.get("prompt_id")
        if not prompt_id:
            raise RuntimeError(f"ComfyUI 未返回 prompt_id: {result}")
        logger.info("ComfyUI 工作流已提交 prompt_id=%s", prompt_id)
        return prompt_id

    # ------------------------------------------------------------------ 中断
    def interrupt(self) -> None:
        """中断当前正在执行的生成任务。"""
        try:
            self._http.post(f"{self.base_url}/interrupt")
            logger.info("ComfyUI 任务已中断")
        except Exception:
            logger.exception("ComfyUI 中断请求失败")

    # ------------------------------------------------------------------ 查询历史
    def get_history(self, prompt_id: str, timeout: int = 300) -> Optional[Dict[str, Any]]:
        """查询执行历史（轮询直到完成或超时）。

        Returns
        -------
        dict or None
            历史记录，含 outputs 节点信息；超时返回 None。
        """
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                resp = self._http.get(f"{self.base_url}/history/{prompt_id}", timeout=10.0)
                if resp.status_code == 200:
                    data = resp.json()
                    if prompt_id in data:
                        return data[prompt_id]
            except Exception:
                logger.debug("查询历史异常，重试中 prompt_id=%s", prompt_id)
            time.sleep(2)

        logger.warning("查询历史超时 prompt_id=%s", prompt_id)
        return None

    # ------------------------------------------------------------------ 下载输出
    def get_output_images(self, history: Dict[str, Any]) -> List[bytes]:
        """从历史记录中提取输出图像（字节列表）。

        Parameters
        ----------
        history : dict
            get_history 返回的历史记录。

        Returns
        -------
        list[bytes]
            输出图像的字节数据列表。
        """
        images: List[bytes] = []
        outputs = history.get("outputs", {})

        for node_id, node_output in outputs.items():
            # 图像输出
            for img_info in node_output.get("images", []):
                filename = img_info.get("filename")
                subfolder = img_info.get("subfolder", "")
                img_type = img_info.get("type", "output")
                if not filename:
                    continue
                img_bytes = self._view_image(filename, subfolder, img_type)
                if img_bytes:
                    images.append(img_bytes)
                    logger.info("获取输出图像: node=%s file=%s", node_id, filename)

            # GIF 输出
            for gif_info in node_output.get("gifs", []):
                filename = gif_info.get("filename")
                subfolder = gif_info.get("subfolder", "")
                img_type = gif_info.get("type", "output")
                if not filename:
                    continue
                img_bytes = self._view_image(filename, subfolder, img_type)
                if img_bytes:
                    images.append(img_bytes)
                    logger.info("获取输出GIF: node=%s file=%s", node_id, filename)

        return images

    def _view_image(self, filename: str, subfolder: str = "", img_type: str = "output") -> Optional[bytes]:
        """通过 /view 接口下载图像。"""
        params = {
            "filename": filename,
            "subfolder": subfolder,
            "type": img_type,
        }
        url = f"{self.base_url}/view?{urllib.parse.urlencode(params)}"
        try:
            resp = self._http.get(url, timeout=30.0)
            resp.raise_for_status()
            return resp.content
        except Exception:
            logger.exception("下载图像失败: %s", filename)
            return None

    # ------------------------------------------------------------------ WebSocket 进度监听
    async def listen_progress(
        self,
        prompt_id: str,
        on_progress: Optional[callable] = None,
        on_complete: Optional[callable] = None,
        on_error: Optional[callable] = None,
    ) -> None:
        """监听 WebSocket 进度消息，直到任务完成或出错。

        采用指数退避重连机制（1s -> 60s），断线自动恢复。

        Parameters
        ----------
        prompt_id : str
            要跟踪的 prompt ID。
        on_progress : callable, optional
            进度回调 ``on_progress(value: int, max: int, message: str)``。
        on_complete : callable, optional
            完成回调 ``on_complete()``。
        on_error : callable, optional
            错误回调 ``on_error(error: str)``。
        """
        reconnect_delay = _WS_RECONNECT_MIN
        ws_uri = f"{self.ws_url}/ws?clientId={self.client_id}"
        completed = False

        while not completed:
            try:
                logger.info("WebSocket 连接中: %s", ws_uri)
                async with websockets.connect(ws_uri, max_size=None) as ws:
                    logger.info("WebSocket 已连接，重置退避")
                    reconnect_delay = _WS_RECONNECT_MIN

                    async for raw_msg in ws:
                        if isinstance(raw_msg, bytes):
                            continue  # 跳过二进制预览帧

                        try:
                            msg = json.loads(raw_msg)
                        except json.JSONDecodeError:
                            continue

                        msg_type = msg.get("type")
                        data = msg.get("data", {})

                        if msg_type == "progress":
                            value = data.get("value", 0)
                            max_val = data.get("max", 0)
                            if on_progress:
                                on_progress(value, max_val, "渲染中")
                            logger.debug("进度: %s/%s", value, max_val)

                        elif msg_type == "executing":
                            # node 执行完成
                            exec_node = data.get("node")
                            exec_prompt_id = data.get("prompt_id")
                            if exec_prompt_id == prompt_id and exec_node is None:
                                # node 为 None 表示整个 prompt 执行完成
                                logger.info("任务执行完成 prompt_id=%s", prompt_id)
                                completed = True
                                if on_complete:
                                    on_complete()
                                break

                        elif msg_type == "execution_error":
                            error_msg = data.get("exception_message", "未知错误")
                            logger.error("执行错误: %s", error_msg)
                            completed = True
                            if on_error:
                                on_error(error_msg)
                            break

                        elif msg_type == "execution_interrupted":
                            logger.warning("执行被中断 prompt_id=%s", prompt_id)
                            completed = True
                            if on_error:
                                on_error("执行被中断")
                            break

                        elif msg_type == "status":
                            # 队列状态更新
                            remaining = data.get("status", {}).get("exec_info", {}).get("queue_remaining", 0)
                            logger.debug("队列剩余: %s", remaining)

                # 正常退出连接（未完成则继续重连）
                if not completed:
                    logger.warning("WebSocket 连接关闭但任务未完成，准备重连")

            except (
                websockets.exceptions.ConnectionClosed,
                websockets.exceptions.WebSocketException,
                ConnectionError,
                OSError,
            ) as exc:
                logger.warning("WebSocket 连接异常: %s，%ss 后重连", exc, reconnect_delay)

            except asyncio.CancelledError:
                logger.info("WebSocket 监听被取消")
                break

            except Exception as exc:
                logger.exception("WebSocket 未预期异常: %s", exc)

            if not completed:
                await asyncio.sleep(reconnect_delay)
                # 指数退避，上限 60s
                reconnect_delay = min(reconnect_delay * 2, _WS_RECONNECT_MAX)

    def listen_progress_sync(
        self,
        prompt_id: str,
        on_progress: Optional[callable] = None,
        on_complete: Optional[callable] = None,
        on_error: Optional[callable] = None,
    ) -> None:
        """listen_progress 的同步包装。"""
        asyncio.run(
            self.listen_progress(prompt_id, on_progress, on_complete, on_error)
        )

    # ------------------------------------------------------------------ 完整执行
    def execute_workflow(
        self,
        workflow: Dict[str, Any],
        on_progress: Optional[callable] = None,
    ) -> List[bytes]:
        """执行完整工作流：提交 -> WS 监听 -> 获取历史 -> 下载输出。

        Parameters
        ----------
        workflow : dict
            ComfyUI API Format 工作流。
        on_progress : callable, optional
            进度回调 ``on_progress(value: int, max: int, message: str)``。

        Returns
        -------
        list[bytes]
            输出图像字节列表。
        """
        # 1. 提交工作流
        prompt_id = self.queue_prompt(workflow)

        # 2. WebSocket 监听进度（用容器捕获错误，回调中无法直接 raise）
        ws_error: list = []

        def _on_complete():
            logger.info("WS 收到完成信号 prompt_id=%s", prompt_id)

        def _on_error(error: str):
            ws_error.append(error)
            logger.error("WS 收到错误信号: %s", error)

        self.listen_progress_sync(
            prompt_id,
            on_progress=on_progress,
            on_complete=_on_complete,
            on_error=_on_error,
        )

        # 若 WS 阶段捕获到错误，抛出异常
        if ws_error:
            raise RuntimeError(f"ComfyUI 执行错误: {ws_error[0]}")

        # 3. 查询历史获取输出
        history = self.get_history(prompt_id, timeout=120)
        if not history:
            raise RuntimeError(f"无法获取执行历史 prompt_id={prompt_id}")

        # 4. 下载输出图像
        images = self.get_output_images(history)
        if not images:
            raise RuntimeError(f"未找到输出图像 prompt_id={prompt_id}")

        return images

    # ------------------------------------------------------------------ mock 模式
    def mock_execute(self) -> bytes:
        """mock 模式：生成占位输出图像。"""
        from PIL import Image
        import io

        logger.info("ComfyUI mock 模式：生成占位图像")
        img = Image.new("RGB", (1024, 1024), color=(20, 20, 40))
        # 添加简单文字标记
        from PIL import ImageDraw
        draw = ImageDraw.Draw(img)
        draw.text((400, 500), "MOCK COMFYUI", fill=(0, 255, 200))

        buf = io.BytesIO()
        img.save(buf, format="PNG")
        return buf.getvalue()

    # ------------------------------------------------------------------ 资源清理
    def close(self) -> None:
        self._http.close()

    def __del__(self):
        try:
            self.close()
        except Exception:
            pass
