package com.mirage.common.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 重建任务提交请求
 */
@Data
public class ReconTaskDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotEmpty(message = "输入照片资产ID不能为空")
    private List<Long> sourceAssetIds;

    /** 参数 JSON: iterations, resolution 等 */
    private String paramsJson;
}
