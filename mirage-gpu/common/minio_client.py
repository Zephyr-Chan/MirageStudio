"""MinIO 客户端封装。

提供对象存储的常用操作：
  - upload_file: 上传本地文件到指定对象路径
  - download_file: 下载对象到本地文件
  - presigned_url: 生成预签名下载 URL（供前端 / Java 后端访问）
  - upload_bytes: 上传内存字节流

使用 minio 官方 Python SDK，启动时自动创建 bucket（若不存在）。
"""

import io
import logging
import mimetypes
from typing import Optional

from minio import Minio
from minio.error import S3Error

from .config import Config

logger = logging.getLogger(__name__)


class MinioClient:
    """MinIO 对象存储客户端封装。"""

    def __init__(self, config: Optional[Config] = None) -> None:
        self.config = config or Config.from_env()
        self.client = Minio(
            endpoint=self.config.minio_endpoint,
            access_key=self.config.minio_access_key,
            secret_key=self.config.minio_secret_key,
            secure=self.config.minio_secure,
        )
        self.bucket = self.config.minio_bucket
        self._ensure_bucket()

    # ------------------------------------------------------------------ 初始化
    def _ensure_bucket(self) -> None:
        """确保 bucket 存在。"""
        try:
            exists = self.client.bucket_exists(self.bucket)
            if not exists:
                self.client.make_bucket(self.bucket)
                logger.info("MinIO bucket 已创建: %s", self.bucket)
            else:
                logger.debug("MinIO bucket 已存在: %s", self.bucket)
        except S3Error:
            logger.exception("MinIO bucket 初始化失败: %s", self.bucket)

    # ------------------------------------------------------------------ 上传
    def upload_file(
        self,
        local_path: str,
        object_name: str,
        content_type: Optional[str] = None,
    ) -> str:
        """上传本地文件。

        Parameters
        ----------
        local_path : str
            本地文件路径。
        object_name : str
            对象存储路径，如 ``recon/task-xxx/output.splat``。
        content_type : str, optional
            MIME 类型，未指定时自动推断。

        Returns
        -------
        str
            对象存储路径 object_name。
        """
        import os

        if content_type is None:
            content_type = mimetypes.guess_type(local_path)[0] or "application/octet-stream"

        file_size = os.path.getsize(local_path)
        self.client.fput_object(
            bucket_name=self.bucket,
            object_name=object_name,
            file_path=local_path,
            content_type=content_type,
        )
        logger.info(
            "MinIO 上传完成 bucket=%s object=%s size=%d type=%s",
            self.bucket,
            object_name,
            file_size,
            content_type,
        )
        return object_name

    def upload_bytes(
        self,
        data: bytes,
        object_name: str,
        content_type: str = "application/octet-stream",
    ) -> str:
        """上传内存字节流。"""
        self.client.put_object(
            bucket_name=self.bucket,
            object_name=object_name,
            data=io.BytesIO(data),
            length=len(data),
            content_type=content_type,
        )
        logger.info(
            "MinIO 字节上传完成 bucket=%s object=%s size=%d",
            self.bucket,
            object_name,
            len(data),
        )
        return object_name

    # ------------------------------------------------------------------ 下载
    def download_file(self, object_name: str, local_path: str) -> str:
        """下载对象到本地文件。

        Returns
        -------
        str
            本地文件路径。
        """
        self.client.fget_object(
            bucket_name=self.bucket,
            object_name=object_name,
            file_path=local_path,
        )
        logger.info("MinIO 下载完成 object=%s -> %s", object_name, local_path)
        return local_path

    def get_object_bytes(self, object_name: str) -> bytes:
        """获取对象的全部字节内容。"""
        response = self.client.get_object(self.bucket, object_name)
        try:
            return response.read()
        finally:
            response.close()
            response.release_conn()

    # ------------------------------------------------------------------ 预签名
    def presigned_url(self, object_name: str, expires_hours: int = 24) -> str:
        """生成预签名下载 URL。

        Parameters
        ----------
        object_name : str
            对象存储路径。
        expires_hours : int
            URL 有效期（小时）。

        Returns
        -------
        str
            预签名 URL。
        """
        from datetime import timedelta

        url = self.client.presigned_get_object(
            bucket_name=self.bucket,
            object_name=object_name,
            expires=timedelta(hours=expires_hours),
        )
        logger.debug(
            "MinIO presigned URL object=%s expires=%sh", object_name, expires_hours
        )
        return url

    # ------------------------------------------------------------------ 辅助
    def object_exists(self, object_name: str) -> bool:
        """检查对象是否存在。"""
        try:
            self.client.stat_object(self.bucket, object_name)
            return True
        except S3Error:
            return False
