-- =====================================================================
-- File: 10-clickhouse-metrics.sql
-- Domain: ClickHouse 指标存储
-- Source: docs/01-database/database-schema-design.md §8.4
-- Engine: MergeTree
-- 注意: ClickHouse DDL, 由 clickhouse-client 执行, 非 MySQL
-- =====================================================================

-- ---------------------------------------------------------------------
-- Table: agent_metrics_daily  (Agent 指标日表, doc §8.4)
-- 按 DBA 指令: MergeTree + PARTITION BY toYYYYMMDD(date) + ORDER BY (tenant_id, agent_id, date)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_metrics_daily
(
    `date`                 Date           COMMENT '统计日期',
    `tenant_id`            UInt64         COMMENT '租户 ID',
    `agent_id`             String         COMMENT 'Agent ID',
    `task_count`           UInt32         COMMENT '任务总数',
    `success_count`        UInt32         COMMENT '成功任务数',
    `hallucination_count`  UInt32         COMMENT '幻觉检出数',
    `avg_cost_cent`        UInt64         COMMENT '平均成本 (分)',
    `avg_token_used`       UInt32         COMMENT '平均 Token 消耗',
    `avg_duration_ms`      UInt32         COMMENT '平均耗时 (ms)'
)
ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(`date`)
ORDER BY (`tenant_id`, `agent_id`, `date`)
SETTINGS index_granularity = 8192;

-- ---------------------------------------------------------------------
-- Table: agent_drift_metrics_daily  (漂移监测明细表, doc §2.3 治理文档)
-- 用于细粒度漂移分析与告警
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_drift_metrics_daily
(
    `date`                  Date           COMMENT '统计日期',
    `tenant_id`             UInt64         COMMENT '租户 ID',
    `agent_id`              String         COMMENT 'Agent ID',
    `agent_version`         UInt32         COMMENT 'Agent 版本',
    `model`                 String         COMMENT '模型名',
    `tool_call_rate`        Float64        COMMENT '工具调用率',
    `avg_plan_steps`        Float64        COMMENT '平均规划步数',
    `avg_output_length`     UInt32         COMMENT '平均输出长度',
    `refusal_rate`          Float64        COMMENT '拒答率',
    `task_success_rate`     Float64        COMMENT '任务成功率',
    `accuracy_score`        Float64        COMMENT '准确率 (L4-3 评分均值)',
    `hallucination_rate`    Float64        COMMENT '幻觉率',
    `role_consistency`      Float64        COMMENT '角色一致性',
    `soft_violation_rate`   Float64        COMMENT '软违规占比',
    `memory_error_rate`     Float64        COMMENT '错误记忆占比',
    `recall_relevance_avg`  Float64        COMMENT '召回相关性均值',
    `drift_score`           Float64        COMMENT '综合漂移分 (0~1, >0.3 告警)',
    `drift_level`           String         COMMENT 'none | session | task | system | memory'
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(`date`)
ORDER BY (`date`, `tenant_id`, `agent_id`, `agent_version`)
SETTINGS index_granularity = 8192;
