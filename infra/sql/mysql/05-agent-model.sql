-- =====================================================================
-- File: 05-agent-model.sql
-- Domain: agent_model (model-gateway)
-- Source: docs/01-database/database-schema-design.md §5
-- Sharding: model_usage_log 按月分表 (16 库 × 12 月)
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_model
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_model;

-- ---------------------------------------------------------------------
-- Table: model_provider  (模型供应商表, doc §5.1)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `model_provider` (
    `id`               BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `provider_code`    VARCHAR(32)      NOT NULL                COMMENT 'openai/anthropic/qwen/deepseek/llama_local',
    `name`             VARCHAR(64)      NOT NULL                COMMENT '显示名',
    `base_url`         VARCHAR(255)    NOT NULL                COMMENT 'API Base',
    `api_key_ref`      VARCHAR(128)    NOT NULL                COMMENT '密钥引用 (Vault 路径, 禁止明文)',
    `protocol`         VARCHAR(16)      NOT NULL                COMMENT 'openai=OpenAI兼容 anthropic=Claude原生 custom=自定义',
    `supported_models` JSON             NOT NULL                COMMENT '支持的模型列表',
    `status`           TINYINT          NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
    `created_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`       VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`       VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`          TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`          INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_code` (`provider_code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型供应商表';

-- ---------------------------------------------------------------------
-- Table: model_route_rule  (模型路由规则表, doc §5.2)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `model_route_rule` (
    `id`               BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `rule_id`          VARCHAR(32)      NOT NULL                COMMENT '规则 ID',
    `scene`            VARCHAR(32)      NOT NULL                COMMENT 'intent=意图识别 planning=规划 tool_call=工具 summary=汇总 audit=终审',
    `tier`             VARCHAR(16)      NOT NULL                COMMENT 'light=轻量 middle=中等 strong=强',
    `preferred_model`  VARCHAR(64)      NOT NULL                COMMENT '首选模型',
    `fallback_models`  JSON             NULL                    COMMENT '降级模型链',
    `priority`         INT UNSIGNED     NOT NULL                COMMENT '优先级 (小先匹配)',
    `condition`        JSON             NULL                    COMMENT '匹配条件 (租户/业务域等)',
    `status`           TINYINT          NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
    `created_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`       VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`       VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`          TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`          INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_id` (`rule_id`),
    KEY `idx_scene_tier` (`scene`, `tier`),
    KEY `idx_priority` (`priority`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型路由规则表';

-- ---------------------------------------------------------------------
-- Table: model_usage_log  (模型调用计量表, doc §5.3, 按月分表)
-- ShardingSphere 分片: 按月分表, 物理表名格式: model_usage_log_YYYYMM
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `model_usage_log` (
    `id`             BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `call_id`        VARCHAR(32)      NOT NULL                COMMENT '调用 ID',
    `task_id`        VARCHAR(32)      NULL                    COMMENT '任务',
    `provider_code`  VARCHAR(32)      NOT NULL                COMMENT '供应商',
    `model`          VARCHAR(64)      NOT NULL                COMMENT '模型名',
    `scene`          VARCHAR(32)      NOT NULL                COMMENT '场景',
    `input_tokens`   INT UNSIGNED     NOT NULL                COMMENT '输入 Token',
    `output_tokens`  INT UNSIGNED     NOT NULL                COMMENT '输出 Token',
    `cache_hit`      TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '是否命中 Prompt 缓存',
    `cost_cent`      BIGINT           NOT NULL                COMMENT '成本 (分)',
    `duration_ms`    INT UNSIGNED     NOT NULL                COMMENT '耗时',
    `trace_id`       VARCHAR(64)      NOT NULL                COMMENT '链路 ID',
    `created_at`     DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_call_id` (`call_id`),
    KEY `idx_task` (`task_id`),
    KEY `idx_model_time` (`model`, `created_at`),
    KEY `idx_scene_time` (`scene`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型调用计量表 (按月分表: model_usage_log_YYYYMM)';
