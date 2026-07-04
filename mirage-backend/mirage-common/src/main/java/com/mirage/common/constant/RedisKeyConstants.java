package com.mirage.common.constant;

/**
 * Redis Key 常量定义
 * 二进制落 MinIO, 元数据落 MySQL, 易变状态落 Redis
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    /** 重建任务流: XADD stream:recon * taskType RECON taskId ... */
    public static final String STREAM_RECON = "stream:recon";

    /** 特效任务流: XADD stream:effect * taskType EFFECT taskId ... */
    public static final String STREAM_EFFECT = "stream:effect";

    /** 渲染任务流 */
    public static final String STREAM_RENDER = "stream:render";

    /** 微调任务流 */
    public static final String STREAM_FINETUNE = "stream:finetune";

    /** Agent任务流 */
    public static final String STREAM_AGENT = "stream:agent";

    /**
     * 任务实时状态 Hash
     * task:status:{taskId} -> { status, progress, message, ... }
     */
    public static final String TASK_STATUS_PREFIX = "task:status:";

    /**
     * GPU 可用槽位计数器
     * DECR/INCR 原子操作
     */
    public static final String GPU_SLOTS_AVAILABLE = "gpu:slots:available";

    /**
     * GPU 槽位占用映射
     * gpu:slot:holders -> Hash { slotIndex -> taskId }
     */
    public static final String GPU_SLOT_HOLDERS = "gpu:slot:holders";

    /** 用户登录 Token 黑名单 token:blacklist:{token} */
    public static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /** 用户登录会话 session:user:{userId} */
    public static final String SESSION_USER_PREFIX = "session:user:";

    /** 工作流模板缓存 workflow:template:{id} */
    public static final String WORKFLOW_TEMPLATE_PREFIX = "workflow:template:";

    /** ComfyUI prompt_id -> taskId 映射 comfyui:prompt:{promptId} */
    public static final String COMFYUI_PROMPT_PREFIX = "comfyui:prompt:";

    /**
     * 构建任务状态 Key
     */
    public static String taskStatus(String taskId) {
        return TASK_STATUS_PREFIX + taskId;
    }

    /**
     * 构建任务状态 Key (Long 类型)
     */
    public static String taskStatus(Long taskId) {
        return TASK_STATUS_PREFIX + taskId;
    }
}
