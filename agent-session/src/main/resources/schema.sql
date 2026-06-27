-- agent_session 逻辑库 schema（对齐 doc 01-database §1）
-- 引擎：InnoDB；字符集：utf8mb4 / utf8mb4_0900_ai_ci

CREATE DATABASE IF NOT EXISTS agent_session
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE agent_session;

-- 1.1 会话主表
CREATE TABLE IF NOT EXISTS `session` (
    `id`              BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`      VARCHAR(32)       NOT NULL                COMMENT '业务会话 ID（UUID 去横线）',
    `tenant_id`       BIGINT UNSIGNED  NOT NULL                COMMENT '租户 ID',
    `user_id`         VARCHAR(64)       NOT NULL                COMMENT '用户标识',
    `agent_id`        BIGINT UNSIGNED   NOT NULL                COMMENT '关联 Agent ID',
    `title`           VARCHAR(255)      DEFAULT NULL            COMMENT '会话标题',
    `status`          TINYINT           NOT NULL DEFAULT 1      COMMENT '1活跃 2空闲 3关闭 4归档',
    `last_msg_at`     DATETIME(3)       DEFAULT NULL            COMMENT '最后消息时间',
    `token_used`      INT UNSIGNED      NOT NULL DEFAULT 0      COMMENT '累计 Token 消耗',
    `context_summary` TEXT              DEFAULT NULL            COMMENT '会话摘要',
    `created_at`      DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `created_by`      VARCHAR(64)      DEFAULT NULL,
    `updated_by`      VARCHAR(64)      DEFAULT NULL,
    `deleted`         TINYINT(1)        NOT NULL DEFAULT 0,
    `version`         INT UNSIGNED      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_tenant_user_status` (`tenant_id`, `user_id`, `status`),
    KEY `idx_last_msg` (`last_msg_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话主表';

-- 1.2 消息表
CREATE TABLE IF NOT EXISTS `session_message` (
    `id`             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `session_id`     VARCHAR(32)      NOT NULL                COMMENT '会话 ID',
    `msg_id`         VARCHAR(32)      NOT NULL                COMMENT '消息 ID',
    `role`           VARCHAR(16)      NOT NULL                COMMENT 'user/assistant/system/tool',
    `content`        MEDIUMTEXT       NOT NULL                COMMENT '消息内容（富文本 JSON）',
    `content_type`   VARCHAR(16)      NOT NULL DEFAULT 'text' COMMENT 'text/markdown/json/stream',
    `tool_calls`     JSON             DEFAULT NULL            COMMENT '工具调用记录（role=assistant 时）',
    `tool_call_id`   VARCHAR(64)      DEFAULT NULL            COMMENT '关联工具调用 ID（role=tool 时）',
    `token_count`    INT UNSIGNED     NOT NULL DEFAULT 0,
    `step_no`        INT UNSIGNED     DEFAULT NULL,
    `is_compressed`  TINYINT(1)       NOT NULL DEFAULT 0,
    `created_at`     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `created_by`     VARCHAR(64)      DEFAULT NULL,
    `updated_by`     VARCHAR(64)      DEFAULT NULL,
    `deleted`        TINYINT(1)       NOT NULL DEFAULT 0,
    `version`        INT UNSIGNED     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_id` (`msg_id`),
    KEY `idx_session_step` (`session_id`, `step_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息表';
