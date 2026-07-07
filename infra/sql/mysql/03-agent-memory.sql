-- =====================================================================
-- File: 03-agent-memory.sql
-- Domain: agent_memory (memory-service)
-- Source: docs/01-database/database-schema-design.md §2.1 + Plan 03 T2
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_memory
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_memory;

-- ---------------------------------------------------------------------
-- Table: memory_record  (长期记忆元数据表, Plan 03 T2 / doc §2.1)
-- 记忆向量本身存 Milvus, 本表存关系型元数据
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `memory_record` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `memory_id`         VARCHAR(32)      NOT NULL                COMMENT '记忆业务 ID (UUID)',
    `tenant_id`         VARCHAR(64)      NOT NULL                COMMENT '租户',
    `user_id`           VARCHAR(64)      NULL                    COMMENT '关联用户 (情景记忆)',
    `type`              VARCHAR(16)      NOT NULL                COMMENT 'EPISODIC/SEMANTIC/PROCEDURAL/REFLECTIVE',
    `status`            VARCHAR(16)      NOT NULL                COMMENT 'RAW/ACTIVE/DISTILLED/ARCHIVED',
    `content`           TEXT             NOT NULL                COMMENT '原始内容',
    `summary`           VARCHAR(512)     NULL                    COMMENT '蒸馏后摘要',
    `topic`             VARCHAR(128)     NULL                    COMMENT '主题',
    `keywords`          VARCHAR(2048)    NULL                    COMMENT '关键词 JSON 数组',
    `source_task_id`    VARCHAR(32)      NULL                    COMMENT '来源任务 ID',
    `outcome`           VARCHAR(16)      NULL                    COMMENT 'SUCCESS/FAILURE/PARTIAL/TIMEOUT',
    `importance_score`  DECIMAL(4,3)     NOT NULL                COMMENT '重要性评分 0~1',
    `importance_level`  VARCHAR(8)       NULL                    COMMENT 'HIGH/MEDIUM/LOW',
    `content_hash`      VARCHAR(64)      NULL                    COMMENT '内容 SHA-256 (去重用)',
    `vector_id`         VARCHAR(64)      NULL                    COMMENT 'Milvus 向量主键',
    `parent_memory_id`  VARCHAR(32)      NULL                    COMMENT '蒸馏来源父记忆 ID',
    `child_memory_ids`  VARCHAR(2048)    NULL                    COMMENT '蒸馏产物子记忆 ID JSON 数组',
    `ttl_expire_at`     DATETIME(3)      NULL                    COMMENT 'TTL 过期时间',
    `distill_count`     INT              NOT NULL DEFAULT 0      COMMENT '蒸馏次数',
    `recall_count`      INT              NOT NULL DEFAULT 0      COMMENT '被召回次数',
    `last_recalled_at`  DATETIME(3)      NULL                    COMMENT '最近召回时间',
    `metadata`          VARCHAR(4096)    NULL                    COMMENT '元数据 JSON',
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NULL                    COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_memory_id` (`memory_id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_topic` (`topic`),
    KEY `idx_content_hash` (`content_hash`),
    KEY `idx_ttl_expire` (`ttl_expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='长期记忆元数据表';

-- ---------------------------------------------------------------------
-- Table: memory_extract_log  (记忆提取日志表, Plan 03 T2)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `memory_extract_log` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `task_id`           VARCHAR(32)      NOT NULL                COMMENT '来源任务 ID',
    `extract_count`     INT              NOT NULL                COMMENT '提取条数',
    `failed_count`      INT              NOT NULL                COMMENT '失败条数',
    `duration_ms`       BIGINT           NOT NULL                COMMENT '耗时 (毫秒)',
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆提取日志表';

-- ---------------------------------------------------------------------
-- Table: memory_distill_log  (记忆蒸馏日志表, doc §3.2 — 骨架阶段扩展保留)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `memory_distill_log` (
    `id`                  BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `distill_task_id`     VARCHAR(32)      NOT NULL                COMMENT '蒸馏任务 ID',
    `domain`              VARCHAR(64)      NOT NULL                COMMENT '业务域',
    `source_memory_ids`   JSON             NOT NULL                COMMENT '被蒸馏的源记忆 ID 数组',
    `target_memory_id`    VARCHAR(32)      NOT NULL                COMMENT '生成的新记忆 ID',
    `summary_level`       TINYINT          NOT NULL                COMMENT '1=全局 2=主题 3=细节',
    `before_token`        INT UNSIGNED     NOT NULL                COMMENT '蒸馏前 Token',
    `after_token`         INT UNSIGNED     NOT NULL                COMMENT '蒸馏后 Token',
    `status`              VARCHAR(16)      NOT NULL                COMMENT 'running/success/failed',
    `created_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`          DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_domain` (`domain`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆蒸馏日志表';

-- ---------------------------------------------------------------------
-- Table: memory_recall_log  (召回日志表, doc §3.3 — 骨架阶段扩展保留)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `memory_recall_log` (
    `id`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `task_id`               VARCHAR(32)      NOT NULL                COMMENT '触发任务',
    `query`                 TEXT             NOT NULL                COMMENT '召回 query',
    `recall_strategies`     JSON             NOT NULL                COMMENT '使用策略 [vector,keyword,time,tag]',
    `candidate_count`       INT UNSIGNED     NOT NULL                COMMENT '初筛候选数',
    `final_count`           INT UNSIGNED     NOT NULL                COMMENT '重排后返回数',
    `returned_memory_ids`   JSON             NOT NULL                COMMENT '返回的记忆 ID 数组',
    `duration_ms`           INT UNSIGNED     NOT NULL                COMMENT '耗时',
    `created_at`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task` (`task_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆召回日志表';

-- ---------------------------------------------------------------------
-- Table: outbox_message  (S-04 Outbox 补偿框架, 跨服务写最终一致)
-- Source: agent-common OutboxMessage JPA entity
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `outbox_message` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `aggregate_id`    VARCHAR(64)     NOT NULL                 COMMENT '业务聚合 ID (e.g. memoryId)',
    `topic`           VARCHAR(128)    NOT NULL                 COMMENT 'RocketMQ topic (e.g. memory.vector.insert)',
    `payload`         TEXT            NOT NULL                 COMMENT '消息体 JSON',
    `status`          VARCHAR(16)     NOT NULL                 COMMENT 'PENDING/SENT/FAILED/DEAD',
    `retry_count`     INT             NOT NULL DEFAULT 0       COMMENT '已重试次数',
    `next_retry_at`   DATETIME(3)     NOT NULL                 COMMENT '下次重试时间',
    `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `sent_at`         DATETIME(3)     NULL                     COMMENT '投递成功时间',
    PRIMARY KEY (`id`),
    KEY `idx_outbox_status_next_retry` (`status`, `next_retry_at`),
    KEY `idx_outbox_aggregate` (`aggregate_id`),
    KEY `idx_outbox_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox 补偿消息表 (S-04)';

-- ---------------------------------------------------------------------
-- Table: consume_log  (S-04 消费幂等表, 复用 S-03 event_consume_log 模式)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `consume_log` (
    `event_id`        VARCHAR(64)     NOT NULL                 COMMENT '事件 ID (= outbox_message.id)',
    `consumed_at`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消费时间',
    PRIMARY KEY (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox 消费幂等日志 (S-04)';
