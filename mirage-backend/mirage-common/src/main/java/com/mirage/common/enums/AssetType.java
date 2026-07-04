package com.mirage.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资产类型
 */
@Getter
@AllArgsConstructor
public enum AssetType {

    PHOTO("原始照片"),
    SPLAT("3DGS高斯泼溅模型"),
    EFFECT_IMAGE("特效图像"),
    EFFECT_VIDEO("特效视频"),
    CAMERA_PATH("相机路径"),
    SCENE_SNAPSHOT("场景快照"),
    LORA_MODEL("LoRA模型"),
    CN_MODEL("ControlNet模型");

    private final String desc;
}
