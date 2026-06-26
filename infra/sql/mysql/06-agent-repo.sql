-- =====================================================================
-- File: 06-agent-repo.sql
-- Domain: agent_repo (agent-repo)
-- Source: docs/01-database/database-schema-design.md §6
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_repo
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_repo;

-- ---------------------------------------------------------------------
-- Table: agent_definition  (Agent 定义表, doc §6.1)
-- 含 system_prompt / core_constraints / reflection_mode / max_steps
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_definition` (
    `id`                   BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `agent_id`             VARCHAR(32)      NOT NULL                COMMENT 'Agent 业务 ID',
    `name`                 VARCHAR(128)    NOT NULL                COMMENT '名称',
    `description`          TEXT             NOT NULL                COMMENT '描述',
    `ability_tags`         JSON             NOT NULL                COMMENT '能力标签',
    `scene_tags`           JSON             NOT NULL                COMMENT '场景标签',
    `system_prompt`        TEXT             NOT NULL                COMMENT '系统提示词',
    `core_constraints`     TEXT             NOT NULL                COMMENT '核心约束区 (压缩优先保留)',
    `business_config`      JSON             NOT NULL                COMMENT '业务配置区',
    `model_tier`           VARCHAR(16)      NOT NULL                COMMENT 'light/middle/strong',
    `max_steps`            INT UNSIGNED     NOT NULL                COMMENT '最大循环步数熔断',
    `max_token`            INT UNSIGNED     NOT NULL                COMMENT 'Token 上限',
    `bound_tools`          JSON             NOT NULL                COMMENT '绑定工具 ID 数组',
    `bound_knowledge_ids`  JSON             NOT NULL                COMMENT '绑定知识库',
    `reflection_mode`      VARCHAR(16)      NOT NULL                COMMENT 'none=禁用 single=单轮 multi=多轮',
    `status`               TINYINT          NOT NULL DEFAULT 1      COMMENT '1=草稿 2=启用 3=下线',
    `version`              INT UNSIGNED     NOT NULL DEFAULT 1      COMMENT '版本号',
    `created_at`           DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`           DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`           VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`           VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`              TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version_lock`         INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_id` (`agent_id`),
    KEY `idx_status` (`status`),
    KEY `idx_ability_tags` ((CAST(`ability_tags` AS CHAR(64) ARRAY))),
    KEY `idx_version` (`version`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 定义表';

-- ---------------------------------------------------------------------
-- Table: agent_version  (Agent 版本表, doc §6.2, 含 is_stable)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_version` (
    `id`            BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `agent_id`      VARCHAR(32)      NOT NULL                COMMENT 'Agent ID',
    `version`       INT UNSIGNED     NOT NULL                COMMENT '版本号',
    `snapshot`      JSON             NOT NULL                COMMENT '完整定义快照',
    `change_log`    TEXT             NOT NULL                COMMENT '变更说明',
    `published_by`  VARCHAR(64)      NOT NULL                COMMENT '发布人',
    `published_at`  DATETIME(3)      NOT NULL                COMMENT '发布时间',
    `is_stable`     TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '是否稳定版 (漂移回滚用)',
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`    VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`    VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`       TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version_lock`  INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_version` (`agent_id`, `version`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_is_stable` (`is_stable`),
    KEY `idx_published_at` (`published_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 版本表';

-- ---------------------------------------------------------------------
-- Table: agent_metrics  (Agent 动态评分表, doc §6.3 agent_score)
-- 对齐 doc §6.3, 表名按 DBA 要求使用 agent_metrics
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_metrics` (
    `id`             BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `agent_id`       VARCHAR(32)      NOT NULL                COMMENT 'Agent',
    `dimension`      VARCHAR(32)      NOT NULL                COMMENT 'success_rate/accuracy/hallucination/latency',
    `score`          DECIMAL(5,4)     NOT NULL                COMMENT '得分 0~1',
    `sample_count`   INT UNSIGNED     NOT NULL                COMMENT '样本量',
    `period_start`   DATE             NOT NULL                COMMENT '统计周期开始',
    `period_end`     DATE             NOT NULL                COMMENT '统计周期结束',
    `created_at`     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_agent_dim_period` (`agent_id`, `dimension`, `period_end`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 动态评分表';
