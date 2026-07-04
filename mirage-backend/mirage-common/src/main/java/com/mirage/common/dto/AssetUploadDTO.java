package com.mirage.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 资产上传请求元数据 (配合文件流使用)
 */
@Data
public class AssetUploadDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "资产类型不能为空")
    private String type;

    /** MinIO 桶名, 不传则使用默认桶 */
    private String storageBucket;

    /** 文件名(含扩展名) */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /** MIME 类型 */
    private String mime;

    /** 宽度 */
    private Integer width;

    /** 高度 */
    private Integer height;

    /** 扩展元数据 JSON */
    private String metaJson;
}
