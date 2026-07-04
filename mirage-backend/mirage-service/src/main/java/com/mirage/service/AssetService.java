package com.mirage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mirage.common.exception.BusinessException;
import com.mirage.common.exception.BizExceptionEnum;
import com.mirage.common.util.SnowflakeId;
import com.mirage.dao.entity.Asset;
import com.mirage.dao.mapper.AssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 资产服务: 资产记录 CRUD (二进制落 MinIO, 由 integration 模块上传)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetMapper assetMapper;
    private final SnowflakeId snowflakeId;

    @Transactional(rollbackFor = Exception.class)
    public Asset create(Long projectId, Long userId, String type, String bucket,
                        String storageKey, Long sizeBytes, String mime,
                        Integer width, Integer height, String metaJson) {
        Asset asset = new Asset();
        asset.setId(snowflakeId.nextId());
        asset.setProjectId(projectId);
        asset.setUserId(userId);
        asset.setType(type);
        asset.setStorageBucket(bucket);
        asset.setStorageKey(storageKey);
        asset.setSizeBytes(sizeBytes);
        asset.setMime(mime);
        asset.setWidth(width);
        asset.setHeight(height);
        asset.setMetaJson(metaJson);
        assetMapper.insert(asset);
        log.info("资产记录创建: assetId={}, type={}, projectId={}", asset.getId(), type, projectId);
        return asset;
    }

    public Asset getById(Long assetId) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(BizExceptionEnum.ASSET_NOT_FOUND);
        }
        return asset;
    }

    public Page<Asset> pageByProject(Long projectId, String type, int current, int size) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getProjectId, projectId)
                .orderByDesc(Asset::getCreatedAt);
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Asset::getType, type);
        }
        return assetMapper.selectPage(new Page<>(current, size), wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long assetId) {
        Asset asset = getById(assetId);
        assetMapper.deleteById(asset.getId());
    }
}
