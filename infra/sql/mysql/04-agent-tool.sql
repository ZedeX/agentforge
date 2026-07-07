-- =====================================================================
-- File: 04-agent-tool.sql
-- Domain: agent_tool (tool-engine)
-- Source: docs/01-database/database-schema-design.md §4
-- Sharding: tool_call_log 按 task_id + 月份分表 (16 库 × 12 月)
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_tool
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_tool;

-- ---------------------------------------------------------------------
-- Table: tool_registry  (工具注册表, doc §4.1)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tool_registry` (
    `id`               BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `tool_id`          VARCHAR(32)      NOT NULL                COMMENT '工具业务 ID',
    `name`             VARCHAR(64)      NOT NULL                COMMENT '工具名 (唯一)',
    `display_name`     VARCHAR(128)     NOT NULL                COMMENT '显示名',
    `description`      TEXT             NOT NULL                COMMENT '功能描述 (供模型召回)',
    `scene_tags`       JSON             NOT NULL                COMMENT '场景标签',
    `ability_tags`     JSON             NULL                    COMMENT '能力标签数组 (用于 Agent 匹配)',
    `tool_type`        VARCHAR(16)      NOT NULL                COMMENT 'atomic=原子 composite=复合 agent=Agent型',
    `risk_level`       TINYINT          NOT NULL                COMMENT '1=R1低 2=R2中 3=R3高',
    `input_schema`     JSON             NOT NULL                COMMENT '输入参数 JSON Schema',
    `output_schema`    JSON             NOT NULL                COMMENT '输出结构定义',
    `error_codes`      JSON             NOT NULL                COMMENT '错误码规范',
    `executor_type`    VARCHAR(16)      NOT NULL                COMMENT 'general=通用器 proxy=内网代理 sandbox=沙箱',
    `endpoint`         VARCHAR(255)    NOT NULL                COMMENT '调用地址 (gRPC service/method)',
    `timeout_ms`       INT UNSIGNED     NOT NULL                COMMENT '默认超时',
    `avg_cost_cent`    BIGINT           NOT NULL DEFAULT 0      COMMENT '平均成本 (分)',
    `avg_duration_ms`  INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '平均耗时',
    `undo_action`      JSON             NULL                    COMMENT '补偿动作定义 (写操作必填)',
    `prompt_cache_key` VARCHAR(128)    NULL                    COMMENT 'Prompt 缓存键',
    `status`           TINYINT          NOT NULL DEFAULT 1      COMMENT '1=草稿 2=启用 3=下线',
    `version`          INT UNSIGNED     NOT NULL DEFAULT 1      COMMENT '版本号',
    `created_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`       VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`       VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`          TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version_lock`     INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tool_id` (`tool_id`),
    UNIQUE KEY `uk_name_version` (`name`, `version`),
    KEY `idx_ability_tags` ((CAST(`ability_tags` AS CHAR(64) ARRAY))),
    KEY `idx_risk_level` (`risk_level`),
    KEY `idx_status` (`status`),
    KEY `idx_scene_risk` (`scene_tags`(64), `risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具注册表';

-- ---------------------------------------------------------------------
-- Table: tool_call_log  (工具调用日志表, doc §4.2, 按月分表)
-- ShardingSphere 分片: 分片键 task_id + created_at, 取模 16 库 × 按月分表
-- 物理表名格式: tool_call_log_YYYYMM
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tool_call_log` (
    `id`            BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `call_id`       VARCHAR(32)      NOT NULL                COMMENT '调用 ID',
    `task_id`       VARCHAR(32)      NOT NULL                COMMENT '任务 ID (分片键)',
    `step_no`       INT UNSIGNED     NULL                    COMMENT '步骤号',
    `agent_id`      BIGINT UNSIGNED  NOT NULL                COMMENT 'Agent',
    `tool_id`       VARCHAR(32)      NOT NULL                COMMENT '工具',
    `tool_version`  INT UNSIGNED     NOT NULL                COMMENT '工具版本',
    `input`         JSON             NOT NULL                COMMENT '入参快照 (脱敏后)',
    `output`        JSON             NULL                    COMMENT '输出快照 (截断)',
    `status`        VARCHAR(16)      NOT NULL                COMMENT 'success/failed/timeout/blocked',
    `error_code`    VARCHAR(32)      NULL                    COMMENT '错误码',
    `error_msg`     TEXT             NULL                    COMMENT '错误信息',
    `duration_ms`   INT UNSIGNED     NOT NULL                COMMENT '耗时',
    `cost_cent`     BIGINT           NOT NULL                COMMENT '成本 (分)',
    `token_used`    INT UNSIGNED     NOT NULL                COMMENT 'Token',
    `risk_level`    TINYINT          NOT NULL                COMMENT '风险等级',
    `approved_by`   VARCHAR(64)      NULL                    COMMENT '审批人 (R3)',
    `trace_id`      VARCHAR(64)      NOT NULL                COMMENT '链路 ID',
    `created_at`    DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_call_id` (`call_id`),
    KEY `idx_task_step` (`task_id`, `step_no`),
    KEY `idx_tool_status` (`tool_id`, `status`),
    KEY `idx_created` (`created_at`),
    KEY `idx_risk_level` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用日志表 (按月分表: tool_call_log_YYYYMM)';

-- ---------------------------------------------------------------------
-- Table: tool_quota  (工具配额表, doc §4.3)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tool_quota` (
    `id`               BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `subject_type`     VARCHAR(16)      NOT NULL                COMMENT 'tenant/agent/task',
    `subject_id`      VARCHAR(64)      NOT NULL                COMMENT '主体 ID',
    `tool_id`          VARCHAR(32)      NULL                    COMMENT '工具 ID (NULL=全工具)',
    `daily_limit`      INT UNSIGNED     NOT NULL                COMMENT '日调用量上限',
    `daily_used`       INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '已用',
    `cost_limit_cent`  BIGINT           NOT NULL                COMMENT '日成本上限 (分)',
    `cost_used_cent`   BIGINT           NOT NULL DEFAULT 0      COMMENT '已用成本 (分)',
    `reset_at`         DATETIME(3)      NOT NULL                COMMENT '下次重置时间',
    `created_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`       VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`       VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`          TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`          INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_subject_tool` (`subject_type`, `subject_id`, `tool_id`),
    KEY `idx_subject` (`subject_type`, `subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具配额表';

-- ---------------------------------------------------------------------
-- Table: tool_approval  (高危工具审批表, doc §4.4)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tool_approval` (
    `id`               BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `approval_id`      VARCHAR(32)      NOT NULL                COMMENT '审批单 ID',
    `tool_id`          VARCHAR(32)      NOT NULL                COMMENT '工具',
    `task_id`          VARCHAR(32)      NOT NULL                COMMENT '任务',
    `agent_id`         BIGINT UNSIGNED  NOT NULL                COMMENT 'Agent',
    `input_snapshot`   JSON             NOT NULL                COMMENT '入参快照',
    `applicant`        VARCHAR(64)      NOT NULL                COMMENT '申请人',
    `approver`         VARCHAR(64)      NULL                    COMMENT '审批人',
    `status`           VARCHAR(16)      NOT NULL                COMMENT 'pending/approved/rejected/expired',
    `expire_at`        DATETIME(3)      NOT NULL                COMMENT '过期时间 (限时授权)',
    `reason`           TEXT             NULL                    COMMENT '申请理由',
    `comment`          TEXT             NULL                    COMMENT '审批意见',
    `created_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`       VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`       VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`          TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`          INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_approval_id` (`approval_id`),
    KEY `idx_tool` (`tool_id`),
    KEY `idx_task` (`task_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='高危工具审批表';

-- ---------------------------------------------------------------------
-- Table: outbox_message  (S-04 Outbox 补偿框架, 跨服务写最终一致)
-- Source: agent-common OutboxMessage JPA entity
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `outbox_message` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `aggregate_id`    VARCHAR(64)     NOT NULL                 COMMENT '业务聚合 ID (e.g. traceId)',
    `topic`           VARCHAR(128)    NOT NULL                 COMMENT 'RocketMQ topic (e.g. tool.audit)',
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
