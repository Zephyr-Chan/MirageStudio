package com.mirage.common.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务状态响应
 */
@Data
@Builder
public class TaskStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private String taskId;

    /** 任务类型: RECON / EFFECT / RENDER / FINETUNE / AGENT */
    private String taskType;

    /** 状态 */
    private String status;

    /** 进度 0-100 */
    private Integer progress;

    /** 提示信息 */
    private String message;

    /** 错误信息 */
    private String errorMsg;

    /** 更新时间戳(毫秒) */
    private Long updatedAt;
}
