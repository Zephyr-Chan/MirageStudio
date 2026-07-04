-- MirageStudio 数据库 Schema
-- 元数据落 MySQL，二进制落 MinIO，易变状态落 Redis

CREATE DATABASE IF NOT EXISTS mirage_studio DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mirage_studio;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL COMMENT '雪花ID',
    `username`      VARCHAR(64)  NOT NULL COMMENT '用户名',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt密码哈希',
    `email`         VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `role`          VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色: USER/ADMIN',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 项目表
CREATE TABLE IF NOT EXISTS `project` (
    `id`             BIGINT       NOT NULL COMMENT '雪花ID',
    `user_id`        BIGINT       NOT NULL COMMENT '所属用户',
    `name`           VARCHAR(128) NOT NULL COMMENT '项目名称',
    `description`    TEXT         DEFAULT NULL COMMENT '项目描述',
    `cover_asset_id` BIGINT       DEFAULT NULL COMMENT '封面资产ID',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/ARCHIVED/DELETED',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目表';

-- 3. 资产表 (按 project_id 分片)
CREATE TABLE IF NOT EXISTS `asset` (
    `id`             BIGINT       NOT NULL COMMENT '雪花ID',
    `project_id`     BIGINT       NOT NULL COMMENT '所属项目',
    `user_id`        BIGINT       NOT NULL COMMENT '上传用户',
    `type`           VARCHAR(32)  NOT NULL COMMENT '类型: PHOTO/SPLAT/EFFECT_IMAGE/EFFECT_VIDEO/CAMERA_PATH/SCENE_SNAPSHOT/LORA_MODEL/CN_MODEL',
    `storage_bucket` VARCHAR(128) NOT NULL COMMENT 'MinIO桶名',
    `storage_key`    VARCHAR(512) NOT NULL COMMENT 'MinIO对象键',
    `size_bytes`     BIGINT       DEFAULT 0 COMMENT '文件大小',
    `mime`           VARCHAR(128) DEFAULT NULL COMMENT 'MIME类型',
    `width`          INT          DEFAULT NULL COMMENT '宽度',
    `height`         INT          DEFAULT NULL COMMENT '高度',
    `meta_json`      JSON         DEFAULT NULL COMMENT '扩展元数据',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted`        TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_project_type` (`project_id`, `type`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产表';

-- 4. 重建任务表 (按 project_id 分片)
CREATE TABLE IF NOT EXISTS `reconstruction_task` (
    `id`                BIGINT      NOT NULL COMMENT '雪花ID',
    `project_id`        BIGINT      NOT NULL,
    `user_id`           BIGINT      NOT NULL,
    `source_asset_ids`  JSON        NOT NULL COMMENT '输入照片资产ID列表',
    `status`            VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/QUEUED/RUNNING/SUCCESS/FAILED/CANCELLED',
    `params_json`       JSON        DEFAULT NULL COMMENT '参数: iterations, resolution, etc.',
    `splat_asset_id`    BIGINT      DEFAULT NULL COMMENT '产出的.splat资产ID',
    `error_msg`         TEXT        DEFAULT NULL,
    `progress`          INT         NOT NULL DEFAULT 0 COMMENT '进度0-100',
    `started_at`        DATETIME    DEFAULT NULL,
    `finished_at`       DATETIME    DEFAULT NULL,
    `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_project_status` (`project_id`, `status`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='3DGS重建任务表';

-- 5. 特效任务表 (按 project_id 分片)
CREATE TABLE IF NOT EXISTS `effect_task` (
    `id`                      BIGINT      NOT NULL COMMENT '雪花ID',
    `project_id`              BIGINT      NOT NULL,
    `user_id`                 BIGINT      NOT NULL,
    `source_snapshot_asset_id` BIGINT     DEFAULT NULL COMMENT '输入截屏资产ID',
    `template_id`             BIGINT      DEFAULT NULL COMMENT '工作流模板ID',
    `params_json`             JSON        DEFAULT NULL COMMENT '参数: prompt, seed, cn_strength, etc.',
    `status`                  VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `result_asset_id`         BIGINT      DEFAULT NULL COMMENT '产出资产ID',
    `comfyui_prompt_id`       VARCHAR(128) DEFAULT NULL COMMENT 'ComfyUI prompt_id',
    `error_msg`               TEXT        DEFAULT NULL,
    `progress`                INT         NOT NULL DEFAULT 0,
    `started_at`              DATETIME    DEFAULT NULL,
    `finished_at`             DATETIME    DEFAULT NULL,
    `created_at`              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`                 TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_project_status` (`project_id`, `status`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='特效任务表';

-- 6. 渲染任务表
CREATE TABLE IF NOT EXISTS `render_job` (
    `id`                   BIGINT      NOT NULL,
    `project_id`           BIGINT      NOT NULL,
    `user_id`              BIGINT      NOT NULL,
    `camera_path_asset_id` BIGINT      DEFAULT NULL,
    `frame_count`          INT         NOT NULL DEFAULT 0,
    `fps`                  INT         NOT NULL DEFAULT 30,
    `resolution`           VARCHAR(16) DEFAULT '1920x1080',
    `effect_template_id`   BIGINT      DEFAULT NULL,
    `effect_params_json`   JSON        DEFAULT NULL,
    `status`               VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `output_video_asset_id` BIGINT     DEFAULT NULL,
    `started_at`           DATETIME    DEFAULT NULL,
    `finished_at`          DATETIME    DEFAULT NULL,
    `created_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`              TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_project_status` (`project_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渲染任务表';

-- 7. 工作流模板表
CREATE TABLE IF NOT EXISTS `workflow_template` (
    `id`                BIGINT       NOT NULL,
    `name`              VARCHAR(128) NOT NULL,
    `category`          VARCHAR(64)  NOT NULL DEFAULT 'STYLE' COMMENT '类别: STYLE/INPAINT/COLOR/VIDEO',
    `template_json`     JSON         NOT NULL COMMENT 'ComfyUI API格式工作流JSON (含占位符)',
    `param_schema_json` JSON         DEFAULT NULL COMMENT '参数schema (驱动前端动态表单)',
    `thumbnail_url`     VARCHAR(512) DEFAULT NULL,
    `enabled`           TINYINT      NOT NULL DEFAULT 1,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_category_enabled` (`category`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ComfyUI工作流模板表';

-- 8. Agent运行记录表
CREATE TABLE IF NOT EXISTS `agent_run` (
    `id`            BIGINT       NOT NULL,
    `project_id`    BIGINT       NOT NULL,
    `user_id`       BIGINT       NOT NULL,
    `goal_text`     TEXT         NOT NULL COMMENT '用户自然语言目标',
    `plan_json`     JSON         DEFAULT NULL COMMENT 'Agent ReAct规划步骤',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    `result_summary` TEXT        DEFAULT NULL,
    `total_tokens`  INT          NOT NULL DEFAULT 0,
    `total_steps`   INT          NOT NULL DEFAULT 0,
    `started_at`    DATETIME     DEFAULT NULL,
    `finished_at`   DATETIME     DEFAULT NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_project_status` (`project_id`, `status`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent运行记录表';

-- 9. Agent步骤表
CREATE TABLE IF NOT EXISTS `agent_step` (
    `id`           BIGINT       NOT NULL,
    `agent_run_id` BIGINT       NOT NULL,
    `step_index`   INT          NOT NULL COMMENT '步骤序号',
    `thought`      TEXT         DEFAULT NULL COMMENT 'Agent思考',
    `action`       VARCHAR(128) DEFAULT NULL COMMENT '调用的工具名',
    `action_input` JSON         DEFAULT NULL COMMENT '工具输入参数',
    `observation`  TEXT         DEFAULT NULL COMMENT '工具返回观察',
    `status`       VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_run_step` (`agent_run_id`, `step_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent步骤表';

-- 10. 微调任务表
CREATE TABLE IF NOT EXISTS `finetune_job` (
    `id`                   BIGINT       NOT NULL,
    `project_id`           BIGINT       NOT NULL,
    `user_id`              BIGINT       NOT NULL,
    `model_type`           VARCHAR(32)  NOT NULL COMMENT 'LORA/CONTROLNET',
    `base_model`           VARCHAR(128) NOT NULL COMMENT '基础模型名',
    `dataset_asset_ids`    JSON         NOT NULL COMMENT '训练数据集资产ID',
    `params_json`          JSON         DEFAULT NULL COMMENT 'rank, lr, steps等',
    `status`               VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    `output_model_asset_id` BIGINT      DEFAULT NULL,
    `metrics_json`         JSON         DEFAULT NULL COMMENT 'loss曲线等',
    `started_at`           DATETIME     DEFAULT NULL,
    `finished_at`          DATETIME     DEFAULT NULL,
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`              TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_project_status` (`project_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微调任务表';

-- 11. 任务日志表
CREATE TABLE IF NOT EXISTS `task_log` (
    `id`        BIGINT       NOT NULL AUTO_INCREMENT,
    `task_type` VARCHAR(32)  NOT NULL COMMENT 'RECON/EFFECT/RENDER/FINETUNE/AGENT',
    `task_id`   BIGINT       NOT NULL,
    `level`     VARCHAR(16)  NOT NULL DEFAULT 'INFO' COMMENT 'INFO/WARN/ERROR/DEBUG',
    `message`   TEXT         NOT NULL,
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task` (`task_type`, `task_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务日志表';

-- 初始工作流模板：赛博朋克风格化 (SD1.5 + ControlNet + LoRA)
INSERT INTO `workflow_template` (`id`, `name`, `category`, `template_json`, `param_schema_json`, `enabled`) VALUES
(1, '赛博朋克风格化', 'STYLE',
'{"placeholder_note": "ComfyUI API format JSON with {{prompt}}, {{seed}}, {{input_image}}, {{cn_strength}} placeholders", "3": {"class_type": "KSampler", "inputs": {"seed": "{{seed}}", "cfg": 7.5, "steps": 20, "sampler_name": "dpmpp_2m", "scheduler": "karras", "denoise": 0.75}}}',
'[{"key":"prompt","label":"风格提示词","type":"text","default":"cyberpunk city, neon lights, rain, cinematic"},{"key":"seed","label":"随机种子","type":"number","default":42},{"key":"cn_strength","label":"ControlNet强度","type":"slider","min":0,"max":2,"step":0.1,"default":1.0}]',
1);
