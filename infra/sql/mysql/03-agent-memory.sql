-- =====================================================================
-- File: 03-agent-memory.sql
-- Domain: agent_memory (memory-service)
-- Source: docs/01-database/database-schema-design.md §3
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_memory
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_memory;

-- ---------------------------------------------------------------------
-- Table: memory_long_term  (长期记忆元数据表, doc §3.1)
-- 记忆向量本身存 Milvus, 本表存关系型元数据
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `memory_long_term` (
    `id`                BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `memory_id`         VARCHAR(32)      NOT NULL                COMMENT '记忆业务 ID',
    `tenant_id`         BIGINT UNSIGNED  NOT NULL                COMMENT '租户',
    `user_id`           VARCHAR(64)      NULL                    COMMENT '关联用户 (情景记忆)',
    `agent_id`          BIGINT UNSIGNED  NULL                    COMMENT '关联 Agent',
    `domain`            VARCHAR(64)      NOT NULL                COMMENT '业务域 (order/cs/code...)',
    `memory_type`       VARCHAR(16)      NOT NULL                COMMENT 'episodic=情景 semantic=语义 procedural=流程',
    `content`           TEXT             NOT NULL                COMMENT '原始内容',
    `summary`           VARCHAR(512)    NULL                    COMMENT '摘要',
    `tags`              JSON             NULL                    COMMENT '标签数组',
    `importance_score`  DECIMAL(4,3)     NOT NULL                COMMENT '重要性评分 0~1',
    `tier`              TINYINT          NOT NULL                COMMENT '1=热 2=温 3=冷',
    `vector_id`         VARCHAR(64)      NOT NULL                COMMENT 'Milvus 主键',
    `collection_name`   VARCHAR(64)      NOT NULL                COMMENT '所属 Milvus Collection',
    `source_type`       VARCHAR(16)      NOT NULL                COMMENT 'task=user 任务 user=标注 system=系统',
    `source_task_id`    VARCHAR(32)      NULL                    COMMENT '来源任务',
    `ttl_at`            DATETIME(3)      NULL                    COMMENT '过期时间 (冷记忆归档)',
    `distilled`         TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '是否已蒸馏',
    `parent_memory_id`  VARCHAR(32)      NULL                    COMMENT '蒸馏父记忆',
    `recall_count`      INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '被召回次数',
    `last_recall_at`    DATETIME(3)      NULL                    COMMENT '最近召回时间',
    `valid`             TINYINT(1)       NOT NULL DEFAULT 1      COMMENT '1=有效 0=失效',
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`        VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`        VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`           TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_memory_id` (`memory_id`),
    KEY `idx_domain` (`domain`),
    KEY `idx_memory_type` (`memory_type`),
    KEY `idx_importance_score` (`importance_score`),
    KEY `idx_valid` (`valid`),
    KEY `idx_tenant_type_domain` (`tenant_id`, `memory_type`, `domain`),
    KEY `idx_user_agent` (`user_id`, `agent_id`),
    KEY `idx_tier_valid` (`tier`, `valid`),
    KEY `idx_recall` (`last_recall_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='长期记忆元数据表';

-- ---------------------------------------------------------------------
-- Table: memory_distill_log  (记忆蒸馏日志表, doc §3.2)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `memory_distill_log` (
    `id`                  BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
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
    `created_by`          VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`          VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`             TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`             INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_domain` (`domain`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆蒸馏日志表';

-- ---------------------------------------------------------------------
-- Table: memory_recall_log  (召回日志表, doc §3.3)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `memory_recall_log` (
    `id`                    BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `task_id`               VARCHAR(32)      NOT NULL                COMMENT '触发任务',
    `query`                 TEXT             NOT NULL                COMMENT '召回 query',
    `recall_strategies`     JSON             NOT NULL                COMMENT '使用策略 [vector,keyword,time,tag]',
    `candidate_count`       INT UNSIGNED     NOT NULL                COMMENT '初筛候选数',
    `final_count`           INT UNSIGNED     NOT NULL                COMMENT '重排后返回数',
    `returned_memory_ids`   JSON             NOT NULL                COMMENT '返回的记忆 ID 数组',
    `duration_ms`            INT UNSIGNED     NOT NULL                COMMENT '耗时',
    `created_at`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task` (`task_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆召回日志表';
