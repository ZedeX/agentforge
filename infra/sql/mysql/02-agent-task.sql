-- =====================================================================
-- File: 02-agent-task.sql
-- Domain: agent_task (task-orchestrator + planning)
-- Source: docs/01-database/database-schema-design.md §2
-- Sharding: task_instance 按 tenant_id % 16 分库 (ShardingSphere)
-- Engine: InnoDB / Charset: utf8mb4 / Collate: utf8mb4_unicode_ci
-- =====================================================================

CREATE DATABASE IF NOT EXISTS agent_task
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE agent_task;

-- ---------------------------------------------------------------------
-- Table: task_instance  (任务实例表, doc §2.1, 23 业务字段)
-- ShardingSphere 分片: 分片键 tenant_id, 取模 16 库
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_instance` (
    `id`              BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `task_id`         VARCHAR(32)      NOT NULL                COMMENT '业务任务 ID',
    `tenant_id`       BIGINT UNSIGNED  NOT NULL                COMMENT '租户 ID (分片键, tenant_id % 16 分库)',
    `session_id`      VARCHAR(32)      NULL                    COMMENT '关联会话 (异步任务可能无)',
    `user_id`         VARCHAR(64)      NOT NULL                COMMENT '发起人',
    `title`           VARCHAR(255)    NOT NULL                COMMENT '任务标题',
    `goal`            TEXT             NOT NULL                COMMENT '任务目标描述',
    `complexity`      TINYINT          NOT NULL                COMMENT '1=L1简单 2=L2中等 3=L3复杂',
    `status`          VARCHAR(16)      NOT NULL                COMMENT '状态机见 08-flow 文档',
    `task_schema`     JSON             NOT NULL                COMMENT '标准 Task Schema (目标/交付/约束/资源)',
    `dag_id`          BIGINT UNSIGNED  NULL                    COMMENT '关联 DAG ID',
    `agent_id`        BIGINT UNSIGNED  NULL                    COMMENT '单 Agent 任务关联',
    `priority`        TINYINT          NOT NULL DEFAULT 5      COMMENT '1=低 5=中 9=高',
    `parent_task_id`  VARCHAR(32)      NULL                    COMMENT '父任务 (子任务时)',
    `replan_count`    INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '已重规划次数',
    `cost_limit_cent` BIGINT           NOT NULL DEFAULT 0      COMMENT '成本上限 (分)',
    `cost_used_cent`  BIGINT           NOT NULL DEFAULT 0      COMMENT '已消耗成本 (分)',
    `token_used`      INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '累计 Token',
    `started_at`      DATETIME(3)      NULL                    COMMENT '开始时间',
    `finished_at`     DATETIME(3)      NULL                    COMMENT '结束时间',
    `error_code`      VARCHAR(32)      NULL                    COMMENT '失败错误码',
    `error_msg`       TEXT             NULL                    COMMENT '失败原因',
    `result_summary`  TEXT             NULL                    COMMENT '结果摘要',
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`      VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`      VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`         TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_status` (`status`),
    KEY `idx_complexity` (`complexity`),
    KEY `idx_session` (`session_id`),
    KEY `idx_parent` (`parent_task_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务实例表 (按 tenant_id % 16 分库)';

-- ---------------------------------------------------------------------
-- Table: subtask_instance  (子任务实例表, doc §2.1 子任务维度)
-- 子任务通过 task_instance.parent_task_id 关联, 本表为子任务执行明细
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `subtask_instance` (
    `id`              BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `subtask_id`      VARCHAR(32)      NOT NULL                COMMENT '子任务业务 ID',
    `task_id`         VARCHAR(32)      NOT NULL                COMMENT '父任务 ID',
    `tenant_id`       BIGINT UNSIGNED  NOT NULL                COMMENT '租户 ID',
    `dag_node_id`     VARCHAR(32)      NOT NULL                COMMENT 'DAG 节点 ID',
    `title`           VARCHAR(255)    NOT NULL                COMMENT '子任务标题',
    `agent_id`        BIGINT UNSIGNED  NULL                    COMMENT '执行 Agent',
    `ability_tags`    JSON             NOT NULL                COMMENT '能力标签数组',
    `inputs`          JSON             NULL                    COMMENT '输入参数',
    `outputs`         JSON             NULL                    COMMENT '输出结果',
    `config`          JSON             NULL                    COMMENT '执行配置 (maxRetries/timeoutMs/modelTier)',
    `status`          VARCHAR(16)      NOT NULL                COMMENT 'pending/running/success/failed/skipped',
    `priority`        TINYINT          NOT NULL DEFAULT 5      COMMENT '1=低 5=中 9=高',
    `replan_count`    INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '已重规划次数',
    `cost_limit_cent` BIGINT           NOT NULL DEFAULT 0      COMMENT '成本上限 (分)',
    `cost_used_cent`  BIGINT           NOT NULL DEFAULT 0      COMMENT '已消耗成本 (分)',
    `token_used`      INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '累计 Token',
    `started_at`      DATETIME(3)      NULL                    COMMENT '开始时间',
    `finished_at`     DATETIME(3)      NULL                    COMMENT '结束时间',
    `error_code`      VARCHAR(32)      NULL                    COMMENT '失败错误码',
    `error_msg`       TEXT             NULL                    COMMENT '失败原因',
    `result_summary`  TEXT             NULL                    COMMENT '结果摘要',
    `created_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`      DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`      VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`      VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`         TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`         INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_subtask_id` (`subtask_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_agent` (`agent_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='子任务实例表';

-- ---------------------------------------------------------------------
-- Table: dag_definition  (DAG 定义表, doc §2.2 task_dag)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dag_definition` (
    `id`               BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `dag_id`           BIGINT UNSIGNED  NOT NULL                COMMENT 'DAG 业务 ID',
    `task_id`          VARCHAR(32)      NOT NULL                COMMENT '关联任务',
    `version`          INT UNSIGNED     NOT NULL                COMMENT '版本号 (重规划递增)',
    `nodes`            JSON             NOT NULL                COMMENT '节点数组 (见 doc §2.3)',
    `edges`            JSON             NOT NULL                COMMENT '依赖边数组 (见 doc §2.4)',
    `parallel_batches` JSON             NOT NULL                COMMENT '并行批次划分',
    `source`           VARCHAR(16)      NOT NULL                COMMENT 'template=模板规划, ai=智能规划',
    `template_id`      BIGINT UNSIGNED  NULL                    COMMENT '模板来源 ID',
    `created_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`       VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`       VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`          TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version_lock`     INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dag_id_version` (`dag_id`, `version`),
    KEY `idx_task` (`task_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DAG 定义表';

-- ---------------------------------------------------------------------
-- Table: task_step_log  (步骤执行日志表, doc §2.5)
-- ShardingSphere 分片: 分片键 task_id, 取模 16 库
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_step_log` (
    `id`               BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `task_id`          VARCHAR(32)      NOT NULL                COMMENT '任务 ID (分片键)',
    `step_no`          INT UNSIGNED     NOT NULL                COMMENT '步骤序号',
    `node_id`          VARCHAR(32)      NOT NULL                COMMENT 'DAG 节点 ID',
    `subtask_id`       VARCHAR(32)      NOT NULL                COMMENT '子任务 ID',
    `agent_id`         BIGINT UNSIGNED  NOT NULL                COMMENT '执行 Agent',
    `phase`            VARCHAR(16)      NOT NULL                COMMENT 'think/act/observe/reflect',
    `action_type`      VARCHAR(16)      NULL                    COMMENT 'model_call/tool_call/none',
    `action_target`    VARCHAR(128)     NULL                    COMMENT '模型/工具标识',
    `input_snapshot`   JSON             NULL                    COMMENT '输入快照',
    `output_snapshot`  JSON             NULL                    COMMENT '输出快照',
    `token_used`       INT UNSIGNED     NOT NULL                COMMENT '本步 Token',
    `cost_cent`        BIGINT           NOT NULL                COMMENT '本步成本 (分)',
    `duration_ms`      INT UNSIGNED     NOT NULL                COMMENT '耗时',
    `status`           VARCHAR(16)      NOT NULL                COMMENT 'success/failed/retry/skipped',
    `error`            TEXT             NULL                    COMMENT '错误信息',
    `created_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`       VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`       VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`          TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`          INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_step` (`task_id`, `step_no`),
    KEY `idx_subtask` (`subtask_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务步骤执行日志表 (按 task_id % 16 分库)';

-- ---------------------------------------------------------------------
-- Table: task_state_change  (状态流转审计表, doc §2.6)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_state_change` (
    `id`           BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `task_id`      VARCHAR(32)      NOT NULL                COMMENT '任务 ID',
    `from_status`  VARCHAR(16)      NULL                    COMMENT '原状态',
    `to_status`    VARCHAR(16)      NOT NULL                COMMENT '新状态',
    `trigger`      VARCHAR(32)      NOT NULL                COMMENT 'auto/manual/system',
    `operator`     VARCHAR(64)      NULL                    COMMENT '操作人',
    `reason`       VARCHAR(255)    NULL                    COMMENT '变更原因',
    `trace_id`     VARCHAR(64)      NOT NULL                COMMENT '链路 ID',
    `created_at`   DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_time` (`task_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务状态流转审计表';

-- ---------------------------------------------------------------------
-- Table: task_replan_log  (重规划日志表, doc §2.7)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_replan_log` (
    `id`                BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `task_id`           VARCHAR(32)      NOT NULL                COMMENT '任务 ID',
    `replan_no`         INT UNSIGNED     NOT NULL                COMMENT '第几次重规划',
    `mode`              VARCHAR(16)      NOT NULL                COMMENT 'incremental=增量 full=全量',
    `trigger_reason`    VARCHAR(255)    NOT NULL                COMMENT '触发原因',
    `failed_node_ids`   JSON             NULL                    COMMENT '失败节点',
    `old_dag_version`   INT UNSIGNED     NOT NULL                COMMENT '旧版本',
    `new_dag_version`   INT UNSIGNED     NOT NULL                COMMENT '新版本',
    `cost_cent`         BIGINT           NOT NULL                COMMENT '重规划成本 (分)',
    `created_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`        DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`        VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`        VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`           TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`           INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_task` (`task_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务重规划日志表';

-- ---------------------------------------------------------------------
-- Table: task_template  (任务模板表, doc §2.8, 广播表)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_template` (
    `id`           BIGINT UNSIGNED  NOT NULL                COMMENT '主键, 雪花算法生成',
    `template_id`  VARCHAR(32)      NOT NULL                COMMENT '模板业务 ID',
    `name`         VARCHAR(128)     NOT NULL                COMMENT '模板名',
    `scene_tags`   JSON             NOT NULL                COMMENT '场景标签数组',
    `dag_template` JSON             NOT NULL                COMMENT 'DAG 模板 (含参数占位符)',
    `param_schema` JSON             NOT NULL                COMMENT '参数定义',
    `usage_count`  INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '累计使用次数',
    `success_rate` DECIMAL(5,4)     NOT NULL DEFAULT 0.0000 COMMENT '成功率',
    `status`       TINYINT          NOT NULL DEFAULT 1      COMMENT '1=草稿 2=启用 3=下线',
    `created_at`   DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)          COMMENT '创建时间',
    `updated_at`   DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `created_by`   VARCHAR(64)      NULL                    COMMENT '创建人',
    `updated_by`   VARCHAR(64)      NULL                    COMMENT '更新人',
    `deleted`      TINYINT(1)       NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0=未删 1=已删',
    `version`      INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_id` (`template_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务模板表 (广播表)';
