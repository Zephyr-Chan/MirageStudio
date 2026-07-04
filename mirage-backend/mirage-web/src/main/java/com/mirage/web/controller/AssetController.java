package com.mirage.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mirage.common.R;
import com.mirage.dao.entity.Asset;
import com.mirage.integration.MinioClient;
import com.mirage.service.AssetService;
import com.mirage.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 资产控制器: 上传 (MinIO) / 查询 / 删除
 */
@Slf4j
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;
    private final MinioClient minioClient;

    /**
     * 上传资产文件到 MinIO 并记录元数据
     *
     * @param projectId 项目ID
     * @param type      资产类型 (PHOTO/SPLAT/...)
     * @param file      文件
     */
    @PostMapping("/upload")
    public R<Asset> upload(@RequestParam Long projectId,
                           @RequestParam String type,
                           @RequestParam(value = "width", required = false) Integer width,
                           @RequestParam(value = "height", required = false) Integer height,
                           @RequestParam("file") MultipartFile file) throws IOException {
        Long userId = UserContext.requireUserId();

        String bucket = minioClient.getDefaultBucket();
        // 生成对象键: {projectId}/{type}/{uuid}.{ext}
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        String objectKey = projectId + "/" + type + "/" + UUID.randomUUID() + ext;

        // 上传到 MinIO
        minioClient.upload(bucket, objectKey, file.getInputStream(),
                file.getSize(), file.getContentType());

        // 记录资产元数据
        Asset asset = assetService.create(projectId, userId, type, bucket, objectKey,
                file.getSize(), file.getContentType(), width, height, null);
        return R.ok("上传成功", asset);
    }

    /**
     * 获取资产下载 (预签名) URL
     */
    @GetMapping("/{id}/download-url")
    public R<Map<String, Object>> getDownloadUrl(@PathVariable Long id) {
        UserContext.requireUserId();
        Asset asset = assetService.getById(id);
        String url = minioClient.getPresignedDownloadUrl(asset.getStorageBucket(), asset.getStorageKey());
        Map<String, Object> result = new HashMap<>();
        result.put("url", url);
        result.put("assetId", asset.getId());
        return R.ok(result);
    }

    @GetMapping("/{id}")
    public R<Asset> get(@PathVariable Long id) {
        UserContext.requireUserId();
        return R.ok(assetService.getById(id));
    }

    @GetMapping
    public R<Page<Asset>> list(@RequestParam Long projectId,
                               @RequestParam(required = false) String type,
                               @RequestParam(defaultValue = "1") int current,
                               @RequestParam(defaultValue = "20") int size) {
        UserContext.requireUserId();
        return R.ok(assetService.pageByProject(projectId, type, current, size));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        UserContext.requireUserId();
        assetService.delete(id);
        return R.ok();
    }
}
