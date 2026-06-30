-- =====================================================================
-- File: 06-agent-repo.sql
-- Domain: agent_repo (agent-repo)
-- Source: docs/01-database/database-schema-design.md §6 + docs/06-agent-repo
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- Plan: 08 T2-T4 (aligned with POJO design - simplified columns)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_repo
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_repo;

-- ---------------------------------------------------------------------
-- Table: agent_definition  (Agent 定义表, doc §6.1, Plan 08 T2)
-- POJO 字段: agentId / name / description / abilityTags(List) / systemPrompt /
--           agentTier(enum) / maxSteps / maxToken / status(enum) / version /
--           boundTools(List) / boundKnowledgeIds(List) / createdAt(long) / updatedAt(long)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_definition` (
    `id`                   BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键自增',
    `agent_id`             VARCHAR(64)      NOT NULL                COMMENT 'Agent 业务 ID',
    `name`                 VARCHAR(128)     NOT NULL                COMMENT '名称',
    `description`          TEXT             NOT NULL                COMMENT '描述',
    `ability_tags`         TEXT             NOT NULL                COMMENT '能力标签 (JSON 数组, JsonListConverter)',
    `system_prompt`        TEXT             NOT NULL                COMMENT '系统提示词',
    `agent_tier`           VARCHAR(16)      NOT NULL                COMMENT 'Agent 等级 (LITE/STANDARD/ADVANCED)',
    `max_steps`            INT              NOT NULL                COMMENT '最大循环步数熔断',
    `max_token`            INT              NOT NULL                COMMENT 'Token 上限',
    `bound_tools`          TEXT             NOT NULL                COMMENT '绑定工具 ID 数组 (JSON)',
    `bound_knowledge_ids`  TEXT             NOT NULL                COMMENT '绑定知识库 ID 数组 (JSON)',
    `status`               VARCHAR(16)      NOT NULL                COMMENT '状态 (DRAFT/PUBLISHED/DEPRECATED/ARCHIVED)',
    `version`              INT              NOT NULL DEFAULT 1      COMMENT '版本号',
    `created_at`           BIGINT           NOT NULL                COMMENT '创建时间 (epoch millis)',
    `updated_at`           BIGINT           NOT NULL                COMMENT '更新时间 (epoch millis)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_id` (`agent_id`),
    KEY `idx_status` (`status`),
    KEY `idx_agent_tier` (`agent_tier`),
    KEY `idx_version` (`version`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 定义表';

-- ---------------------------------------------------------------------
-- Table: agent_version  (Agent 版本表, doc §6.2, Plan 08 T3)
-- POJO 字段: id / agentId / version / snapshot(String) / changeLog / createdAt(long)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_version` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键自增',
    `agent_id`      VARCHAR(64)      NOT NULL                COMMENT 'Agent ID',
    `version`       INT              NOT NULL                COMMENT '版本号',
    `snapshot`      LONGTEXT         NOT NULL                COMMENT '完整定义快照 (JSON)',
    `change_log`    TEXT             NOT NULL                COMMENT '变更说明',
    `created_at`    BIGINT           NOT NULL                COMMENT '创建时间 (epoch millis)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_version` (`agent_id`, `version`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 版本表';

-- ---------------------------------------------------------------------
-- Table: agent_rating  (Agent 用户评分表, doc 06-agent-repo §3.2, Plan 08 T4)
-- POJO 字段: id / agentId / userId / score(int) / comment / createdAt(long)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_rating` (
    `id`            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键自增',
    `agent_id`      VARCHAR(64)      NOT NULL                COMMENT 'Agent ID',
    `user_id`       VARCHAR(64)      NOT NULL                COMMENT '用户 ID',
    `score`         INT              NOT NULL                COMMENT '评分 [1,5]',
    `comment`       TEXT             NULL                    COMMENT '评价内容',
    `created_at`    BIGINT           NOT NULL                COMMENT '创建时间 (epoch millis)',
    PRIMARY KEY (`id`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_agent_user` (`agent_id`, `user_id`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `chk_score_range` CHECK (`score` BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 用户评分表';

-- ---------------------------------------------------------------------
-- Table: capability  (Agent 能力描述表, doc 06-agent-repo §3.1, Plan 08 T4)
-- POJO 字段: code(@Id natural key) / name / tag(enum) / description / enabled(boolean)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `capability` (
    `code`         VARCHAR(64)     NOT NULL                COMMENT '能力代码 (自然键)',
    `name`         VARCHAR(128)    NOT NULL                COMMENT '能力名称',
    `tag`          VARCHAR(32)     NOT NULL                COMMENT '能力标签 (CapabilityTag enum)',
    `description`  TEXT            NULL                    COMMENT '描述',
    `enabled`      TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '是否启用: 0=禁用 1=启用',
    PRIMARY KEY (`code`),
    KEY `idx_tag` (`tag`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 能力描述表';
