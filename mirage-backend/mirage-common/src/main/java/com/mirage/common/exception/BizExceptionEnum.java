package com.mirage.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务异常枚举
 */
@Getter
@AllArgsConstructor
public enum BizExceptionEnum {

    // 通用错误 1xxxx
    SUCCESS(0, "成功"),
    SYSTEM_ERROR(10000, "系统内部错误"),
    PARAM_INVALID(10001, "参数校验失败"),
    PARAM_MISSING(10002, "缺少必要参数"),
    UNAUTHORIZED(10003, "未登录或登录已过期"),
    FORBIDDEN(10004, "无权限访问"),
    RESOURCE_NOT_FOUND(10005, "资源不存在"),
    METHOD_NOT_ALLOWED(10006, "请求方法不支持"),
    TOO_MANY_REQUESTS(10007, "请求过于频繁"),
    DATA_DUPLICATED(10008, "数据已存在"),

    // 用户/认证 2xxxx
    USER_NOT_FOUND(20001, "用户不存在"),
    USERNAME_EXISTS(20002, "用户名已被注册"),
    PASSWORD_WRONG(20003, "用户名或密码错误"),
    USER_DISABLED(20004, "用户已被禁用"),
    TOKEN_INVALID(20005, "Token无效"),
    TOKEN_EXPIRED(20006, "Token已过期"),

    // 项目/资产 3xxxx
    PROJECT_NOT_FOUND(30001, "项目不存在"),
    ASSET_NOT_FOUND(30002, "资产不存在"),
    ASSET_UPLOAD_FAIL(30003, "资产上传失败"),
    PROJECT_LIMIT_EXCEEDED(30004, "项目数量超限"),

    // 任务 4xxxx
    TASK_NOT_FOUND(40001, "任务不存在"),
    TASK_DISPATCH_FAIL(40002, "任务投递失败"),
    TASK_STATE_INVALID(40003, "任务状态不允许此操作"),
    TASK_FAILED(40004, "任务执行失败"),
    GPU_SLOT_UNAVAILABLE(40005, "GPU槽位不足"),
    TASK_CANCELLED(40006, "任务已取消"),

    // 集成 5xxxx
    COMFYUI_CALL_FAIL(50001, "ComfyUI调用失败"),
    COMFYUI_INTERRUPT_FAIL(50002, "ComfyUI中断失败"),
    MINIO_OPERATION_FAIL(50003, "MinIO操作失败"),
    WORKFLOW_RENDER_FAIL(50004, "工作流渲染失败"),

    // Agent 6xxxx
    AGENT_RUN_NOT_FOUND(60001, "Agent运行记录不存在"),
    AGENT_PLAN_FAIL(60002, "Agent规划失败");

    private final int code;
    private final String message;
}
