package com.mirage.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 项目创建/更新请求
 */
@Data
public class ProjectDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称过长")
    private String name;

    @Size(max = 2000, message = "项目描述过长")
    private String description;

    /** 封面资产ID */
    private Long coverAssetId;
}
