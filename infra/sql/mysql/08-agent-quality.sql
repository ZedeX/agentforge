-- =====================================================================
-- File: 08-agent-quality.sql
-- Domain: agent_quality (quality-service, ClickHouse 存明细)
-- Source: docs/01-database/database-schema-design.md §8 + §9.1 audit_log
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_quality
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_quality;

-- ---------------------------------------------------------------------
-- Table: eval_task  (评测任务表, doc §8.1)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `eval_task` (
    `id`            BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `eval_id`       VARCHAR(32)      NOT NULL                COMMENT '评测 ID',
    `type`          VARCHAR(16)      NOT NULL                COMMENT 'online=在线抽样 offline=离线回归 drift=漂移检测',
    `agent_id`      VARCHAR(32)      NULL                    COMMENT '被测 Agent',
    `baseline_id`   BIGINT UNSIGNED  NULL                    COMMENT '基准集 ID',
    `sample_count`  INT UNSIGNED     NOT NULL                COMMENT '样本量',
    `metrics`       JSON             NOT NULL                COMMENT '指标结果',
    `status`        VARCHAR(16)      NOT NULL                COMMENT 'running/success/failed',
    `started_at`    DATETIME(3)      NULL                    COMMENT '开始时间',
    `finished_at`   DATETIME(3)      NULL                    COMMENT '结束时间',
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`    VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`    VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`       TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_eval_id` (`eval_id`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评测任务表';

-- ---------------------------------------------------------------------
-- Table: eval_baseline  (黄金基准集表, doc §8.2)
-- baseline_type: behavior=行为 effect=效果 alignment=对齐
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `eval_baseline` (
    `id`              BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `baseline_id`     VARCHAR(32)      NOT NULL                COMMENT '基准 ID',
    `name`            VARCHAR(128)    NOT NULL                COMMENT '名称',
    `baseline_type`   VARCHAR(16)      NOT NULL                COMMENT 'behavior=行为 effect=效果 alignment=对齐 (含 quality/drift 扩展)',
    `agent_id`        VARCHAR(32)      NULL                    COMMENT '关联 Agent',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 1      COMMENT '版本',
    `sample_count`    INT UNSIGNED     NOT NULL                COMMENT '样本量',
    `golden_metrics`  JSON             NOT NULL                COMMENT '基线指标',
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`      VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`      VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`         TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version_lock`    INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_baseline_id` (`baseline_id`),
    KEY `idx_baseline_type` (`baseline_type`),
    KEY `idx_agent_id` (`agent_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黄金基准集表';

-- ---------------------------------------------------------------------
-- Table: badcase  (异常案例表, doc §8.3)
-- 含 category / severity / root_cause / fix_action 字段
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `badcase` (
    `id`            BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `case_id`       VARCHAR(32)      NOT NULL                COMMENT '案例 ID',
    `task_id`       VARCHAR(32)      NULL                    COMMENT '关联任务',
    `agent_id`      VARCHAR(32)      NOT NULL                COMMENT 'Agent',
    `category`      VARCHAR(32)      NOT NULL                COMMENT 'hallucination/drift/tool_error/plan_error',
    `severity`      TINYINT          NOT NULL                COMMENT '1=低 2=中 3=高',
    `description`   TEXT             NOT NULL                COMMENT '问题描述',
    `reproduction`  TEXT             NULL                    COMMENT '复现步骤',
    `root_cause`    TEXT             NULL                    COMMENT '根因分析',
    `fix_action`    TEXT             NULL                    COMMENT '修复动作',
    `status`        VARCHAR(16)      NOT NULL                COMMENT 'open/analyzing/fixed/closed',
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`    VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`    VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`       TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`       INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_case_id` (`case_id`),
    KEY `idx_category` (`category`),
    KEY `idx_severity` (`severity`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常案例表';

-- ---------------------------------------------------------------------
-- Table: audit_log  (审计日志表, doc §9.1)
-- 按用户要求置于 agent_quality 库 (doc 原属 agent_risk, 此处按 DBA 指令归并)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id`            BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `audit_id`      VARCHAR(32)      NOT NULL                COMMENT '审计 ID',
    `subject_type`  VARCHAR(16)      NOT NULL                COMMENT 'user/agent/tool/task',
    `subject_id`    VARCHAR(64)      NOT NULL                COMMENT '主体 ID',
    `action`        VARCHAR(32)      NOT NULL                COMMENT '调用/写操作/越权拦截',
    `resource_type` VARCHAR(32)     NOT NULL                COMMENT '资源类型',
    `resource_id`   VARCHAR(64)      NOT NULL                COMMENT '资源 ID',
    `risk_level`    TINYINT          NOT NULL                COMMENT '风险等级',
    `result`        VARCHAR(16)      NOT NULL                COMMENT 'allow/deny/warn',
    `detail`        JSON             NOT NULL                COMMENT '详情',
    `trace_id`      VARCHAR(64)      NOT NULL                COMMENT '链路 ID',
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_audit_id` (`audit_id`),
    KEY `idx_subject` (`subject_type`, `subject_id`),
    KEY `idx_resource` (`resource_type`, `resource_id`),
    KEY `idx_action` (`action`),
    KEY `idx_subject_time` (`subject_type`, `subject_id`, `created_at`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';
