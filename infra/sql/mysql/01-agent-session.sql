-- =====================================================================
-- File: 01-agent-session.sql
-- Domain: agent_session (session-service)
-- Source: docs/01-database/database-schema-design.md §1
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_session
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_session;

-- ---------------------------------------------------------------------
-- Table: session  (会话主表, doc §1.1)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `session` (
    `id`              BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `session_id`      VARCHAR(32)      NOT NULL                COMMENT '业务会话 ID (UUID 去横线)',
    `tenant_id`       BIGINT UNSIGNED  NOT NULL                COMMENT '租户 ID',
    `user_id`         VARCHAR(64)      NOT NULL                COMMENT '用户标识',
    `agent_id`        BIGINT UNSIGNED  NOT NULL                COMMENT '关联 Agent ID',
    `title`           VARCHAR(255)    NULL                    COMMENT '会话标题 (首条消息自动生成)',
    `status`          TINYINT          NOT NULL                COMMENT '1=活跃 2=空闲 3=关闭 4=归档',
    `last_msg_at`     DATETIME(3)      NULL                    COMMENT '最后消息时间',
    `token_used`      INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '累计 Token 消耗',
    `context_summary` TEXT             NULL                    COMMENT '会话摘要 (压缩后留存)',
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`      VARCHAR(64)      NULL                    COMMENT '创建人 (系统自动则为 system)',
    `updated_by`      VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`         TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_tenant_user_status` (`tenant_id`, `user_id`, `status`),
    KEY `idx_last_msg` (`last_msg_at`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话主表';

-- ---------------------------------------------------------------------
-- Table: session_message  (消息表, doc §1.2)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `session_message` (
    `id`             BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `session_id`     VARCHAR(32)      NOT NULL                COMMENT '会话 ID',
    `msg_id`         VARCHAR(32)      NOT NULL                COMMENT '消息 ID',
    `role`           VARCHAR(16)      NOT NULL                COMMENT 'user/assistant/system/tool',
    `content`        MEDIUMTEXT       NOT NULL                COMMENT '消息内容 (富文本 JSON)',
    `content_type`   VARCHAR(16)      NOT NULL                COMMENT 'text/markdown/json/stream',
    `tool_calls`     JSON             NULL                    COMMENT '工具调用记录 (role=assistant 时)',
    `tool_call_id`   VARCHAR(64)      NULL                    COMMENT '关联工具调用 ID (role=tool 时)',
    `token_count`    INT UNSIGNED     NOT NULL                COMMENT '本条 Token 数',
    `step_no`        INT UNSIGNED     NULL                    COMMENT '所属推理步序号',
    `is_compressed`  TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '是否被压缩归档',
    `created_at`     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`     VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`     VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`        TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`        INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_msg_id` (`msg_id`),
    KEY `idx_session_step` (`session_id`, `step_no`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话消息表';
