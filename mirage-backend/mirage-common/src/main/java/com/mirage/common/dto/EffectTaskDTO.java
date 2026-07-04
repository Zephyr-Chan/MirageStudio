package com.mirage.common.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 特效任务提交请求
 */
@Data
public class EffectTaskDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 输入场景快照资产ID */
    private Long sourceSnapshotAssetId;

    /** 工作流模板ID */
    private Long templateId;

    /** 参数 JSON: prompt, seed, cn_strength 等 */
    private String paramsJson;
}
