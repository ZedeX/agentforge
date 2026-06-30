-- =====================================================================
-- File: 05-agent-model.sql
-- Domain: agent_model (model-gateway)
-- Source: docs/01-database/database-schema-design.md §5 + Plan 07 T2-T3
-- Plan 07 alignment: cost_per_input_1k / cost_per_output_1k / weight / max_qps / max_concurrency
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_model
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_model;

-- ---------------------------------------------------------------------
-- Table: model_provider  (模型供应商表, doc §5.1, Plan 07 T2)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `model_provider` (
    `id`                   BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `provider_code`        VARCHAR(64)      NOT NULL                COMMENT 'openai/anthropic/gemini/qwen/deepseek',
    `provider_name`        VARCHAR(128)     NOT NULL                COMMENT '显示名',
    `api_base_url`         VARCHAR(512)     NOT NULL                COMMENT 'API Base URL',
    `api_key_ref`          VARCHAR(256)     NOT NULL                COMMENT '密钥引用 (Vault 路径, 禁止明文)',
    `cost_per_input_1k`    DECIMAL(10,6)    NOT NULL DEFAULT 0.0    COMMENT '输入 token 单价 (USD/1k)',
    `cost_per_output_1k`   DECIMAL(10,6)    NOT NULL DEFAULT 0.0    COMMENT '输出 token 单价 (USD/1k)',
    `max_qps`              INT              NOT NULL DEFAULT 100    COMMENT 'QPS 上限',
    `max_concurrency`      INT              NOT NULL DEFAULT 10      COMMENT '并发上限',
    `weight`               INT              NOT NULL DEFAULT 1       COMMENT '路由权重',
    `enabled`              TINYINT          NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
    `created_at`           DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`           DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_code` (`provider_code`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型供应商表';

-- ---------------------------------------------------------------------
-- Table: model_route_rule  (模型路由规则表, doc §5.2, Plan 07 T3)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `model_route_rule` (
    `id`                    BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `scene`                 VARCHAR(32)      NOT NULL                COMMENT 'INTENT/AUDIT/GENERIC',
    `priority`              INT              NOT NULL                COMMENT '优先级 (小先匹配)',
    `from_provider_code`    VARCHAR(64)      NULL                    COMMENT '来源 provider (可空)',
    `primary_provider_code` VARCHAR(64)      NOT NULL                COMMENT '首选 provider code',
    `fallback_provider_code` VARCHAR(64)    NULL                    COMMENT '降级 provider code',
    `cost_ceiling_usd`      DECIMAL(10,4)    NOT NULL DEFAULT 0.0    COMMENT '成本上限 (USD)',
    `enabled`               TINYINT          NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
    `created_at`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`            DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_scene_priority` (`scene`, `priority`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型路由规则表';

-- ---------------------------------------------------------------------
-- Table: model_usage_log  (模型调用计量表, doc §5.3, Plan 07 T12)
-- ShardingSphere 分片: 按月分表, 物理表名格式: model_usage_log_YYYYMM
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `model_usage_log` (
    `id`                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `trace_id`          VARCHAR(64)      NOT NULL                COMMENT '链路 ID',
    `tenant_id`         VARCHAR(64)      NOT NULL                COMMENT '租户 ID',
    `provider_code`     VARCHAR(64)      NOT NULL                COMMENT '供应商',
    `model_name`        VARCHAR(128)     NOT NULL                COMMENT '模型名',
    `scene`             VARCHAR(32)      NOT NULL                COMMENT 'INTENT/AUDIT/GENERIC',
    `input_tokens`      INT              NOT NULL                COMMENT '输入 Token',
    `output_tokens`     INT              NOT NULL                COMMENT '输出 Token',
    `input_cost_usd`    DECIMAL(10,6)    NOT NULL DEFAULT 0.0    COMMENT '输入成本 (USD)',
    `output_cost_usd`   DECIMAL(10,6)    NOT NULL DEFAULT 0.0    COMMENT '输出成本 (USD)',
    `total_cost_usd`    DECIMAL(10,6)    NOT NULL DEFAULT 0.0    COMMENT '总成本 (USD)',
    `latency_ms`        BIGINT           NOT NULL                COMMENT '耗时 (ms)',
    `status`            VARCHAR(16)      NOT NULL                COMMENT 'SUCCESS/ERROR/TIMEOUT',
    `error_code`        VARCHAR(64)      NULL                    COMMENT '错误码',
    `created_at`        BIGINT           NOT NULL                COMMENT '创建时间 (epoch millis)',
    PRIMARY KEY (`id`),
    KEY `idx_trace_id` (`trace_id`),
    KEY `idx_tenant_date` (`tenant_id`, `created_at`),
    KEY `idx_provider_time` (`provider_code`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型调用计量表 (按月分表: model_usage_log_YYYYMM)';
