package com.mirage.integration;

import com.mirage.common.exception.BusinessException;
import com.mirage.common.exception.BizExceptionEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * MinIO 客户端封装: 上传 / 下载 / presigned URL
 *
 * <p>内部委托 io.minio.MinioClient (使用全限定名避免与本类命名冲突)。</p>
 */
@Slf4j
@Component
public class MinioClient {

    private final io.minio.MinioClient minioClient;

    @Value("${minio.bucket:mirage-assets}")
    private String defaultBucket;

    public MinioClient(@Value("${minio.endpoint}") String endpoint,
                       @Value("${minio.access-key}") String accessKey,
                       @Value("${minio.secret-key}") String secretKey) {
        this.minioClient = io.minio.MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        log.info("MinIO 客户端初始化: endpoint={}", endpoint);
    }

    /**
     * 上传文件 (流式)
     *
     * @param bucket      桶名, 为空使用默认桶
     * @param objectName  对象键
     * @param inputStream 文件流
     * @param size        文件大小 (未知传 -1)
     * @param contentType MIME 类型
     */
    public void upload(String bucket, String objectName, InputStream inputStream,
                       long size, String contentType) {
        String targetBucket = (bucket == null || bucket.isEmpty()) ? defaultBucket : bucket;
        try {
            long partSize = (size < 0) ? -1 : size;
            minioClient.putObject(
                    io.minio.PutObjectArgs.builder()
                            .bucket(targetBucket)
                            .object(objectName)
                            .stream(inputStream, partSize, -1)
                            .contentType(contentType == null ? "application/octet-stream" : contentType)
                            .build());
            log.info("MinIO 上传成功: bucket={}, object={}", targetBucket, objectName);
        } catch (Exception e) {
            log.error("MinIO 上传失败: bucket={}, object={}", targetBucket, objectName, e);
            throw new BusinessException(BizExceptionEnum.MINIO_OPERATION_FAIL,
                    "MinIO 上传失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件流
     */
    public InputStream download(String bucket, String objectName) {
        try {
            return minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            log.error("MinIO 下载失败: bucket={}, object={}", bucket, objectName, e);
            throw new BusinessException(BizExceptionEnum.MINIO_OPERATION_FAIL,
                    "MinIO 下载失败: " + e.getMessage());
        }
    }

    /**
     * 生成预签名下载 URL (默认有效期 1 小时)
     */
    public String getPresignedDownloadUrl(String bucket, String objectName) {
        return getPresignedDownloadUrl(bucket, objectName, 3600);
    }

    /**
     * 生成预签名下载 URL
     *
     * @param expiry 有效期 (秒)
     */
    public String getPresignedDownloadUrl(String bucket, String objectName, int expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(expiry)
                            .build());
        } catch (Exception e) {
            log.error("MinIO 生成预签名URL失败: bucket={}, object={}", bucket, objectName, e);
            throw new BusinessException(BizExceptionEnum.MINIO_OPERATION_FAIL,
                    "生成预签名URL失败: " + e.getMessage());
        }
    }

    /**
     * 生成预签名上传 URL
     */
    public String getPresignedUploadUrl(String bucket, String objectName, int expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.PUT)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(expiry)
                            .build());
        } catch (Exception e) {
            log.error("MinIO 生成上传预签名URL失败: bucket={}, object={}", bucket, objectName, e);
            throw new BusinessException(BizExceptionEnum.MINIO_OPERATION_FAIL,
                    "生成上传预签名URL失败: " + e.getMessage());
        }
    }

    /**
     * 确保桶存在 (不存在则创建)
     */
    public void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(
                        io.minio.MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 桶已创建: {}", bucket);
            }
        } catch (Exception e) {
            log.error("MinIO 桶检查/创建失败: {}", bucket, e);
            throw new BusinessException(BizExceptionEnum.MINIO_OPERATION_FAIL,
                    "桶操作失败: " + e.getMessage());
        }
    }

    public String getDefaultBucket() {
        return defaultBucket;
    }
}
