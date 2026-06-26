# 数据库表结构设计

> 文档版本：v1.0  |  更新日期：2026-06-26  |  对应 PRD：第七节·工程文件输出要求 3
> 依赖文档：[00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md)

## 0. 设计约定

### 0.1 通用字段规范

所有业务表统一包含以下审计字段：

| 字段名 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | - | 主键，雪花算法生成 |
| `created_at` | DATETIME(3) | CURRENT_TIMESTAMP(3) | 创建时间（毫秒精度） |
| `updated_at` | DATETIME(3) | CURRENT_TIMESTAMP(3) ON UPDATE | 更新时间 |
| `created_by` | VARCHAR(64) | NULL | 创建人（系统自动则为 `system`） |
| `updated_by` | VARCHAR(64) | NULL | 更新人 |
| `deleted` | TINYINT(1) | 0 | 逻辑删除标记：0=未删，1=已删 |
| `version` | INT UNSIGNED | 0 | 乐观锁版本号 |

### 0.2 字符集与排序规则

- 数据库：`utf8mb4` / `utf8mb4_0900_ai_ci`
- 引擎：`InnoDB`
- 主键策略：BIGINT 雪花 ID（避免自增主键分库分表瓶颈）

### 0.3 分库分表策略（ShardingSphere）

| 表 | 分片键 | 分片策略 | 分片数 |
|---|---|---|---|
| `task_instance` | `tenant_id` | 取模 | 16 库 |
| `task_step_log` | `task_id` | 取模 | 16 库 |
| `agent_call_log` | `task_id` + 月份 | 取模 + 按月 | 16 库 × 12 月 |
| `tool_call_log` | `task_id` + 月份 | 取模 + 按月 | 16 库 × 12 月 |

### 0.4 数据库分配

按 [00-overview 文档第 3.1 节](../00-overview/tech-stack-and-architecture.md#31-微服务拆分清单11-个核心服务--2-个横向服务)服务边界划分逻辑库：

| 逻辑库 | 归属服务 | 主要表 |
|---|---|---|
| `agent_session` | session-service | 会话、消息 |
| `agent_task` | task-orchestrator / planning | 任务、DAG、子任务、规划日志 |
| `agent_memory` | memory-service | 长期记忆元数据、蒸馏日志 |
| `agent_tool` | tool-engine | 工具注册、调用日志、配额 |
| `agent_model` | model-gateway | 模型路由、计量、密钥 |
| `agent_repo` | agent-repo | Agent 配置、版本、评分 |
| `agent_knowledge` | knowledge-service | 知识库、文档、切片 |
| `agent_quality` | quality-service | 评测任务、Badcase |
| `agent_risk` | risk-control | 审计、越权记录、内容安全 |

---

## 1. 会话域（agent_session 库 / session-service）

### 1.1 `session` 会话主表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `session_id` | VARCHAR(32) | NO | 业务会话 ID（UUID 去横线） |
| `tenant_id` | BIGINT UNSIGNED | NO | 租户 ID |
| `user_id` | VARCHAR(64) | NO | 用户标识 |
| `agent_id` | BIGINT UNSIGNED | NO | 关联 Agent ID |
| `title` | VARCHAR(255) | YES | 会话标题（首条消息自动生成） |
| `status` | TINYINT | NO | 1=活跃 2=空闲 3=关闭 4=归档 |
| `last_msg_at` | DATETIME(3) | YES | 最后消息时间 |
| `token_used` | INT UNSIGNED | NO | 累计 Token 消耗 |
| `context_summary` | TEXT | YES | 会话摘要（压缩后留存） |
| 通用字段 | - | - | 见 0.1 |

**索引**：
- `uk_session_id` (session_id) 唯一
- `idx_tenant_user_status` (tenant_id, user_id, status)
- `idx_last_msg` (last_msg_at)

### 1.2 `session_message` 消息表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `session_id` | VARCHAR(32) | NO | 会话 ID |
| `msg_id` | VARCHAR(32) | NO | 消息 ID |
| `role` | VARCHAR(16) | NO | user/assistant/system/tool |
| `content` | MEDIUMTEXT | NO | 消息内容（富文本 JSON） |
| `content_type` | VARCHAR(16) | NO | text/markdown/json/stream |
| `tool_calls` | JSON | YES | 工具调用记录（role=assistant 时） |
| `tool_call_id` | VARCHAR(64) | YES | 关联工具调用 ID（role=tool 时） |
| `token_count` | INT UNSIGNED | NO | 本条 Token 数 |
| `step_no` | INT UNSIGNED | YES | 所属推理步序号 |
| `is_compressed` | TINYINT(1) | NO | 是否被压缩归档 |
| 通用字段 | - | - | 见 0.1 |

**索引**：
- `uk_msg_id` (msg_id) 唯一
- `idx_session_step` (session_id, step_no)

---

## 2. 任务域（agent_task 库 / task-orchestrator + planning）

### 2.1 `task_instance` 任务实例表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `task_id` | VARCHAR(32) | NO | 业务任务 ID |
| `tenant_id` | BIGINT UNSIGNED | NO | 租户 ID |
| `session_id` | VARCHAR(32) | YES | 关联会话（异步任务可能无） |
| `user_id` | VARCHAR(64) | NO | 发起人 |
| `title` | VARCHAR(255) | NO | 任务标题 |
| `goal` | TEXT | NO | 任务目标描述 |
| `complexity` | TINYINT | NO | 1=L1简单 2=L2中等 3=L3复杂 |
| `status` | VARCHAR(16) | NO | 状态机见 [08-flow 文档](../08-flow/state-machines-and-sequences.md) |
| `task_schema` | JSON | NO | 标准 Task Schema（目标/交付/约束/资源） |
| `dag_id` | BIGINT UNSIGNED | YES | 关联 DAG ID |
| `agent_id` | BIGINT UNSIGNED | YES | 单 Agent 任务关联 |
| `priority` | TINYINT | NO | 1=低 5=中 9=高 |
| `parent_task_id` | VARCHAR(32) | YES | 父任务（子任务时） |
| `replan_count` | INT UNSIGNED | NO | 已重规划次数 |
| `cost_limit_cent` | BIGINT | NO | 成本上限（分） |
| `cost_used_cent` | BIGINT | NO | 已消耗成本（分） |
| `token_used` | INT UNSIGNED | NO | 累计 Token |
| `started_at` | DATETIME(3) | YES | 开始时间 |
| `finished_at` | DATETIME(3) | YES | 结束时间 |
| `error_code` | VARCHAR(32) | YES | 失败错误码 |
| `error_msg` | TEXT | YES | 失败原因 |
| `result_summary` | TEXT | YES | 结果摘要 |
| 通用字段 | - | - | 见 0.1 |

**索引**：
- `uk_task_id` (task_id) 唯一
- `idx_tenant_status` (tenant_id, status)
- `idx_session` (session_id)
- `idx_parent` (parent_task_id)

### 2.2 `task_dag` DAG 定义表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `dag_id` | BIGINT UNSIGNED | NO | DAG 业务 ID |
| `task_id` | VARCHAR(32) | NO | 关联任务 |
| `version` | INT UNSIGNED | NO | 版本号（重规划递增） |
| `nodes` | JSON | NO | 节点数组（见 2.3） |
| `edges` | JSON | NO | 依赖边数组（见 2.4） |
| `parallel_batches` | JSON | NO | 并行批次划分 |
| `source` | VARCHAR(16) | NO | template=模板规划，ai=智能规划 |
| `template_id` | BIGINT UNSIGNED | YES | 模板来源 ID |
| 通用字段 | - | - | 见 0.1 |

**索引**：
- `uk_dag_id_version` (dag_id, version) 唯一
- `idx_task` (task_id)

### 2.3 DAG 节点 JSON 结构（nodes 字段）

```json
{
  "nodeId": "n1",
  "nodeType": "subtask",
  "subtaskId": "st_001",
  "title": "查询用户订单",
  "agentId": 1001,
  "abilityTags": ["query", "order"],
  "inputs": {"userId": "u_123"},
  "outputs": {},
  "config": {
    "maxRetries": 2,
    "timeoutMs": 30000,
    "modelTier": "middle"
  },
  "dependsOn": ["n0"],
  "status": "pending"
}
```

### 2.4 DAG 依赖边 JSON 结构（edges 字段）

```json
{
  "from": "n0",
  "to": "n1",
  "depType": "data|logic|none",
  "paramMapping": {"n0.outputs.orderId": "n1.inputs.orderId"}
}
```

### 2.5 `task_step_log` 步骤执行日志表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `task_id` | VARCHAR(32) | NO | 任务 ID |
| `step_no` | INT UNSIGNED | NO | 步骤序号 |
| `node_id` | VARCHAR(32) | NO | DAG 节点 ID |
| `subtask_id` | VARCHAR(32) | NO | 子任务 ID |
| `agent_id` | BIGINT UNSIGNED | NO | 执行 Agent |
| `phase` | VARCHAR(16) | NO | think/act/observe/reflect |
| `action_type` | VARCHAR(16) | YES | model_call/tool_call/none |
| `action_target` | VARCHAR(128) | YES | 模型/工具标识 |
| `input_snapshot` | JSON | YES | 输入快照 |
| `output_snapshot` | JSON | YES | 输出快照 |
| `token_used` | INT UNSIGNED | NO | 本步 Token |
| `cost_cent` | BIGINT | NO | 本步成本（分） |
| `duration_ms` | INT UNSIGNED | NO | 耗时 |
| `status` | VARCHAR(16) | NO | success/failed/retry/skipped |
| `error` | TEXT | YES | 错误信息 |
| 通用字段 | - | - | 见 0.1 |

**索引**：
- `uk_task_step` (task_id, step_no) 唯一
- `idx_subtask` (subtask_id)
- `idx_status` (status)

### 2.6 `task_state_change` 状态流转审计表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `task_id` | VARCHAR(32) | NO | 任务 ID |
| `from_status` | VARCHAR(16) | YES | 原状态 |
| `to_status` | VARCHAR(16) | NO | 新状态 |
| `trigger` | VARCHAR(32) | NO | auto/manual/system |
| `operator` | VARCHAR(64) | YES | 操作人 |
| `reason` | VARCHAR(255) | YES | 变更原因 |
| `trace_id` | VARCHAR(64) | NO | 链路 ID |
| `created_at` | DATETIME(3) | NO | - |

**索引**：`idx_task_time` (task_id, created_at)

### 2.7 `task_replan_log` 重规划日志表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `task_id` | VARCHAR(32) | NO | 任务 ID |
| `replan_no` | INT UNSIGNED | NO | 第几次重规划 |
| `mode` | VARCHAR(16) | NO | incremental=增量 full=全量 |
| `trigger_reason` | VARCHAR(255) | NO | 触发原因 |
| `failed_node_ids` | JSON | YES | 失败节点 |
| `old_dag_version` | INT UNSIGNED | NO | 旧版本 |
| `new_dag_version` | INT UNSIGNED | NO | 新版本 |
| `cost_cent` | BIGINT | NO | 重规划成本 |
| 通用字段 | - | - | 见 0.1 |

### 2.8 `task_template` 任务模板表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `template_id` | VARCHAR(32) | NO | 模板业务 ID |
| `name` | VARCHAR(128) | NO | 模板名 |
| `scene_tags` | JSON | NO | 场景标签数组 |
| `dag_template` | JSON | NO | DAG 模板（含参数占位符） |
| `param_schema` | JSON | NO | 参数定义 |
| `usage_count` | INT UNSIGNED | NO | 累计使用次数 |
| `success_rate` | DECIMAL(5,4) | NO | 成功率 |
| `status` | TINYINT | NO | 1=草稿 2=启用 3=下线 |
| 通用字段 | - | - | 见 0.1 |

---

## 3. 记忆域（agent_memory 库 / memory-service）

### 3.1 `memory_long_term` 长期记忆元数据表

记忆向量本身存 Milvus，本表存关系型元数据。

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `memory_id` | VARCHAR(32) | NO | 记忆业务 ID |
| `tenant_id` | BIGINT UNSIGNED | NO | 租户 |
| `user_id` | VARCHAR(64) | YES | 关联用户（情景记忆） |
| `agent_id` | BIGINT UNSIGNED | YES | 关联 Agent |
| `domain` | VARCHAR(64) | NO | 业务域（order/cs/code...） |
| `memory_type` | VARCHAR(16) | NO | episodic=情景 semantic=语义 procedural=流程 |
| `content` | TEXT | NO | 原始内容 |
| `summary` | VARCHAR(512) | YES | 摘要 |
| `tags` | JSON | YES | 标签数组 |
| `importance_score` | DECIMAL(4,3) | NO | 重要性评分 0~1 |
| `tier` | TINYINT | NO | 1=热 2=温 3=冷 |
| `vector_id` | VARCHAR(64) | NO | Milvus 主键 |
| `collection_name` | VARCHAR(64) | NO | 所属 Milvus Collection |
| `source_type` | VARCHAR(16) | NO | task=user 任务 user=标注 system=系统 |
| `source_task_id` | VARCHAR(32) | YES | 来源任务 |
| `ttl_at` | DATETIME(3) | YES | 过期时间（冷记忆归档） |
| `distilled` | TINYINT(1) | NO | 是否已蒸馏 |
| `parent_memory_id` | VARCHAR(32) | YES | 蒸馏父记忆 |
| `recall_count` | INT UNSIGNED | NO | 被召回次数 |
| `last_recall_at` | DATETIME(3) | YES | 最近召回时间 |
| `valid` | TINYINT(1) | NO | 1=有效 0=失效 |
| 通用字段 | - | - | 见 0.1 |

**索引**：
- `uk_memory_id` (memory_id) 唯一
- `idx_tenant_type_domain` (tenant_id, memory_type, domain)
- `idx_user_agent` (user_id, agent_id)
- `idx_tier_valid` (tier, valid)
- `idx_recall` (last_recall_at)

### 3.2 `memory_distill_log` 记忆蒸馏日志表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `distill_task_id` | VARCHAR(32) | NO | 蒸馏任务 ID |
| `domain` | VARCHAR(64) | NO | 业务域 |
| `source_memory_ids` | JSON | NO | 被蒸馏的源记忆 ID 数组 |
| `target_memory_id` | VARCHAR(32) | NO | 生成的新记忆 ID |
| `summary_level` | TINYINT | NO | 1=全局 2=主题 3=细节 |
| `before_token` | INT UNSIGNED | NO | 蒸馏前 Token |
| `after_token` | INT UNSIGNED | NO | 蒸馏后 Token |
| `status` | VARCHAR(16) | NO | running/success/failed |
| 通用字段 | - | - | 见 0.1 |

### 3.3 `memory_recall_log` 召回日志表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `task_id` | VARCHAR(32) | NO | 触发任务 |
| `query` | TEXT | NO | 召回 query |
| `recall_strategies` | JSON | NO | 使用策略 [vector,keyword,time,tag] |
| `candidate_count` | INT UNSIGNED | NO | 初筛候选数 |
| `final_count` | INT UNSIGNED | NO | 重排后返回数 |
| `returned_memory_ids` | JSON | NO | 返回的记忆 ID 数组 |
| `duration_ms` | INT UNSIGNED | NO | 耗时 |
| `created_at` | DATETIME(3) | NO | - |

### 3.4 Redis 短期记忆 Key 设计

| Key 模式 | 类型 | TTL | 说明 |
|---|---|---|---|
| `sm:{sessionId}:ctx` | Hash | 2h | 当前会话上下文（system/recent_msgs/tools） |
| `sm:{sessionId}:steps` | List | 2h | 本轮推理步骤栈 |
| `sm:{sessionId}:token_water` | String | 2h | 当前 Token 水位（百分比） |
| `sm:{sessionId}:draft` | String | 30min | 瞬时记忆草稿 |
| `runtime:{agentInstanceId}:state` | Hash | 30min | Agent 运行时断点状态 |

---

## 4. 工具域（agent_tool 库 / tool-engine）

### 4.1 `tool_registry` 工具注册表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `tool_id` | VARCHAR(32) | NO | 工具业务 ID |
| `name` | VARCHAR(64) | NO | 工具名（唯一） |
| `display_name` | VARCHAR(128) | NO | 显示名 |
| `description` | TEXT | NO | 功能描述（供模型召回） |
| `scene_tags` | JSON | NO | 场景标签 |
| `tool_type` | VARCHAR(16) | NO | atomic=原子 composite=复合 agent=Agent型 |
| `risk_level` | TINYINT | NO | 1=R1低 2=R2中 3=R3高 |
| `input_schema` | JSON | NO | 输入参数 JSON Schema |
| `output_schema` | JSON | NO | 输出结构定义 |
| `error_codes` | JSON | NO | 错误码规范 |
| `executor_type` | VARCHAR(16) | NO | general=通用器 proxy=内网代理 sandbox=沙箱 |
| `endpoint` | VARCHAR(255) | NO | 调用地址（gRPC service/method） |
| `timeout_ms` | INT UNSIGNED | NO | 默认超时 |
| `avg_cost_cent` | BIGINT | NO | 平均成本（分） |
| `avg_duration_ms` | INT UNSIGNED | NO | 平均耗时 |
| `undo_action` | JSON | YES | 补偿动作定义（写操作必填） |
| `prompt_cache_key` | VARCHAR(128) | YES | Prompt 缓存键 |
| `status` | TINYINT | NO | 1=草稿 2=启用 3=下线 |
| `version` | INT UNSIGNED | NO | 版本号 |
| 通用字段 | - | - | 见 0.1 |

**索引**：`uk_tool_id` (tool_id)、`uk_name_version` (name, version)、`idx_scene_risk` (scene_tags(64), risk_level)

### 4.2 `tool_call_log` 工具调用日志表（按月分表）

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `call_id` | VARCHAR(32) | NO | 调用 ID |
| `task_id` | VARCHAR(32) | NO | 任务 ID |
| `step_no` | INT UNSIGNED | YES | 步骤号 |
| `agent_id` | BIGINT UNSIGNED | NO | Agent |
| `tool_id` | VARCHAR(32) | NO | 工具 |
| `tool_version` | INT UNSIGNED | NO | 工具版本 |
| `input` | JSON | NO | 入参快照（脱敏后） |
| `output` | JSON | YES | 输出快照（截断） |
| `status` | VARCHAR(16) | NO | success/failed/timeout/blocked |
| `error_code` | VARCHAR(32) | YES | 错误码 |
| `error_msg` | TEXT | YES | 错误信息 |
| `duration_ms` | INT UNSIGNED | NO | 耗时 |
| `cost_cent` | BIGINT | NO | 成本 |
| `token_used` | INT UNSIGNED | NO | Token |
| `risk_level` | TINYINT | NO | 风险等级 |
| `approved_by` | VARCHAR(64) | YES | 审批人（R3） |
| `trace_id` | VARCHAR(64) | NO | 链路 ID |
| `created_at` | DATETIME(3) | NO | - |

**索引**：`uk_call_id` (call_id)、`idx_task_step` (task_id, step_no)、`idx_tool_status` (tool_id, status)、`idx_created` (created_at)

### 4.3 `tool_quota` 工具配额表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `subject_type` | VARCHAR(16) | NO | tenant/agent/task |
| `subject_id` | VARCHAR(64) | NO | 主体 ID |
| `tool_id` | VARCHAR(32) | YES | 工具 ID（NULL=全工具） |
| `daily_limit` | INT UNSIGNED | NO | 日调用量上限 |
| `daily_used` | INT UNSIGNED | NO | 已用 |
| `cost_limit_cent` | BIGINT | NO | 日成本上限 |
| `cost_used_cent` | BIGINT | NO | 已用成本 |
| `reset_at` | DATETIME(3) | NO | 下次重置时间 |
| 通用字段 | - | - | 见 0.1 |

**索引**：`uk_subject_tool` (subject_type, subject_id, tool_id)

### 4.4 `tool_approval` 高危工具审批表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `approval_id` | VARCHAR(32) | NO | 审批单 ID |
| `tool_id` | VARCHAR(32) | NO | 工具 |
| `task_id` | VARCHAR(32) | NO | 任务 |
| `agent_id` | BIGINT UNSIGNED | NO | Agent |
| `input_snapshot` | JSON | NO | 入参快照 |
| `applicant` | VARCHAR(64) | NO | 申请人 |
| `approver` | VARCHAR(64) | YES | 审批人 |
| `status` | VARCHAR(16) | NO | pending/approved/rejected/expired |
| `expire_at` | DATETIME(3) | NO | 过期时间（限时授权） |
| `reason` | TEXT | YES | 申请理由 |
| `comment` | TEXT | YES | 审批意见 |
| 通用字段 | - | - | 见 0.1 |

---

## 5. 模型网关域（agent_model 库 / model-gateway）

### 5.1 `model_provider` 模型供应商表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `provider_code` | VARCHAR(32) | NO | openai/anthropic/qwen/deepseek/llama_local |
| `name` | VARCHAR(64) | NO | 显示名 |
| `base_url` | VARCHAR(255) | NO | API Base |
| `api_key_ref` | VARCHAR(128) | NO | 密钥引用（Vault 路径，禁止明文） |
| `protocol` | VARCHAR(16) | NO | openai=OpenAI兼容 anthropic=Claude原生 custom=自定义 |
| `supported_models` | JSON | NO | 支持的模型列表 |
| `status` | TINYINT | NO | 1=启用 0=停用 |
| 通用字段 | - | - | 见 0.1 |

### 5.2 `model_route_rule` 模型路由规则表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `rule_id` | VARCHAR(32) | NO | 规则 ID |
| `scene` | VARCHAR(32) | NO | intent=意图识别 planning=规划 tool_call=工具 summary=汇总 audit=终审 |
| `tier` | VARCHAR(16) | NO | light=轻量 middle=中等 strong=强 |
| `preferred_model` | VARCHAR(64) | NO | 首选模型 |
| `fallback_models` | JSON | YES | 降级模型链 |
| `priority` | INT UNSIGNED | NO | 优先级（小先匹配） |
| `condition` | JSON | YES | 匹配条件（租户/业务域等） |
| `status` | TINYINT | NO | 1=启用 0=停用 |
| 通用字段 | - | - | 见 0.1 |

### 5.3 `model_usage_log` 模型调用计量表（按月分表）

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `call_id` | VARCHAR(32) | NO | 调用 ID |
| `task_id` | VARCHAR(32) | YES | 任务 |
| `provider_code` | VARCHAR(32) | NO | 供应商 |
| `model` | VARCHAR(64) | NO | 模型名 |
| `scene` | VARCHAR(32) | NO | 场景 |
| `input_tokens` | INT UNSIGNED | NO | 输入 Token |
| `output_tokens` | INT UNSIGNED | NO | 输出 Token |
| `cache_hit` | TINYINT(1) | NO | 是否命中 Prompt 缓存 |
| `cost_cent` | BIGINT | NO | 成本（分） |
| `duration_ms` | INT UNSIGNED | NO | 耗时 |
| `trace_id` | VARCHAR(64) | NO | 链路 ID |
| `created_at` | DATETIME(3) | NO | - |

**索引**：`idx_task` (task_id)、`idx_model_time` (model, created_at)、`idx_scene_time` (scene, created_at)

---

## 6. Agent 仓库域（agent_repo 库 / agent-repo）

### 6.1 `agent_definition` Agent 定义表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `agent_id` | VARCHAR(32) | NO | Agent 业务 ID |
| `name` | VARCHAR(128) | NO | 名称 |
| `description` | TEXT | NO | 描述 |
| `ability_tags` | JSON | NO | 能力标签 |
| `scene_tags` | JSON | NO | 场景标签 |
| `system_prompt` | TEXT | NO | 系统提示词 |
| `core_constraints` | TEXT | NO | 核心约束区（压缩优先保留） |
| `business_config` | JSON | NO | 业务配置区 |
| `model_tier` | VARCHAR(16) | NO | light/middle/strong |
| `max_steps` | INT UNSIGNED | NO | 最大循环步数熔断 |
| `max_token` | INT UNSIGNED | NO | Token 上限 |
| `bound_tools` | JSON | NO | 绑定工具 ID 数组 |
| `bound_knowledge_ids` | JSON | NO | 绑定知识库 |
| `reflection_mode` | VARCHAR(16) | NO | none=禁用 single=单轮 multi=多轮 |
| `status` | TINYINT | NO | 1=草稿 2=启用 3=下线 |
| `version` | INT UNSIGNED | NO | 版本号 |
| 通用字段 | - | - | 见 0.1 |

### 6.2 `agent_version` Agent 版本表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `agent_id` | VARCHAR(32) | NO | Agent ID |
| `version` | INT UNSIGNED | NO | 版本号 |
| `snapshot` | JSON | NO | 完整定义快照 |
| `change_log` | TEXT | NO | 变更说明 |
| `published_by` | VARCHAR(64) | NO | 发布人 |
| `published_at` | DATETIME(3) | NO | 发布时间 |
| `is_stable` | TINYINT(1) | NO | 是否稳定版（漂移回滚用） |

**索引**：`uk_agent_version` (agent_id, version)

### 6.3 `agent_score` Agent 动态评分表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `agent_id` | VARCHAR(32) | NO | Agent |
| `dimension` | VARCHAR(32) | NO | success_rate/accuracy/hallucination/latency |
| `score` | DECIMAL(5,4) | NO | 得分 0~1 |
| `sample_count` | INT UNSIGNED | NO | 样本量 |
| `period_start` | DATE | NO | 统计周期开始 |
| `period_end` | DATE | NO | 统计周期结束 |
| `created_at` | DATETIME(3) | NO | - |

**索引**：`idx_agent_dim_period` (agent_id, dimension, period_end)

---

## 7. 知识库域（agent_knowledge 库 / knowledge-service）

### 7.1 `knowledge_base` 知识库表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `kb_id` | VARCHAR(32) | NO | 知识库业务 ID |
| `name` | VARCHAR(128) | NO | 名称 |
| `domain` | VARCHAR(64) | NO | 业务域 |
| `description` | TEXT | YES | 描述 |
| `milvus_collection` | VARCHAR(64) | NO | Milvus Collection 名 |
| `embedding_model` | VARCHAR(64) | NO | 向量化模型 |
| `chunk_strategy` | JSON | NO | 切片策略 |
| `status` | TINYINT | NO | 1=启用 0=停用 |
| 通用字段 | - | - | 见 0.1 |

### 7.2 `knowledge_document` 知识文档表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `doc_id` | VARCHAR(32) | NO | 文档 ID |
| `kb_id` | VARCHAR(32) | NO | 知识库 |
| `title` | VARCHAR(255) | NO | 标题 |
| `source_type` | VARCHAR(16) | NO | file/url/manual |
| `source_url` | VARCHAR(512) | YES | 来源 URL |
| `object_key` | VARCHAR(255) | YES | MinIO 对象 Key |
| `file_type` | VARCHAR(16) | YES | pdf/docx/md/html |
| `version` | INT UNSIGNED | NO | 版本 |
| `chunk_count` | INT UNSIGNED | NO | 切片数 |
| `status` | VARCHAR(16) | NO | pending/parsing/ready/failed |
| `parsed_at` | DATETIME(3) | YES | 解析完成时间 |
| `acl` | JSON | NO | 权限控制（用户/角色可见性） |
| 通用字段 | - | - | 见 0.1 |

### 7.3 `knowledge_chunk` 知识切片表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `chunk_id` | VARCHAR(32) | NO | 切片 ID |
| `doc_id` | VARCHAR(32) | NO | 文档 ID |
| `kb_id` | VARCHAR(32) | NO | 知识库 |
| `seq_no` | INT UNSIGNED | NO | 文档内序号 |
| `content` | TEXT | NO | 切片内容 |
| `vector_id` | VARCHAR(64) | NO | Milvus 主键 |
| `metadata` | JSON | NO | 元数据（页码/标题/章节） |
| `quality_score` | DECIMAL(4,3) | YES | 质量评分 |
| 通用字段 | - | - | 见 0.1 |

**索引**：`uk_chunk_id` (chunk_id)、`idx_doc_seq` (doc_id, seq_no)

---

## 8. 质量评估域（agent_quality 库 / quality-service，ClickHouse 存明细）

### 8.1 MySQL：`eval_task` 评测任务表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `eval_id` | VARCHAR(32) | NO | 评测 ID |
| `type` | VARCHAR(16) | NO | online=在线抽样 offline=离线回归 drift=漂移检测 |
| `agent_id` | VARCHAR(32) | YES | 被测 Agent |
| `baseline_id` | BIGINT UNSIGNED | YES | 基准集 ID |
| `sample_count` | INT UNSIGNED | NO | 样本量 |
| `metrics` | JSON | NO | 指标结果 |
| `status` | VARCHAR(16) | NO | running/success/failed |
| `started_at` | DATETIME(3) | YES | - |
| `finished_at` | DATETIME(3) | YES | - |
| 通用字段 | - | - | 见 0.1 |

### 8.2 MySQL：`eval_baseline` 黄金基准集表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `baseline_id` | VARCHAR(32) | NO | 基准 ID |
| `name` | VARCHAR(128) | NO | 名称 |
| `baseline_type` | VARCHAR(16) | NO | behavior=行为 effect=效果 alignment=对齐 |
| `agent_id` | VARCHAR(32) | YES | 关联 Agent |
| `version` | INT UNSIGNED | NO | 版本 |
| `sample_count` | INT UNSIGNED | NO | 样本量 |
| `golden_metrics` | JSON | NO | 基线指标 |
| 通用字段 | - | - | 见 0.1 |

### 8.3 MySQL：`badcase` 异常案例表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `case_id` | VARCHAR(32) | NO | 案例 ID |
| `task_id` | VARCHAR(32) | YES | 关联任务 |
| `agent_id` | VARCHAR(32) | NO | Agent |
| `category` | VARCHAR(32) | NO | hallucination/drift/tool_error/plan_error |
| `severity` | TINYINT | NO | 1=低 2=中 3=高 |
| `description` | TEXT | NO | 问题描述 |
| `reproduction` | TEXT | YES | 复现步骤 |
| `root_cause` | TEXT | YES | 根因分析 |
| `fix_action` | TEXT | YES | 修复动作 |
| `status` | VARCHAR(16) | NO | open/analyzing/fixed/closed |
| 通用字段 | - | - | 见 0.1 |

### 8.4 ClickHouse：`agent_metrics_daily` Agent 指标日表

```sql
CREATE TABLE agent_metrics_daily (
    `date` Date,
    `tenant_id` UInt64,
    `agent_id` String,
    `task_total` UInt32,
    `task_success` UInt32,
    `tool_call_total` UInt32,
    `tool_call_success` UInt32,
    `tool_select_error` UInt32,
    `hallucination_count` UInt32,
    `avg_token` UInt32,
    `avg_cost_cent` UInt64,
    `p95_latency_ms` UInt32
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, tenant_id, agent_id);
```

---

## 9. 风控合规域（agent_risk 库 / risk-control）

### 9.1 `audit_log` 审计日志表

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `audit_id` | VARCHAR(32) | NO | 审计 ID |
| `subject_type` | VARCHAR(16) | NO | user/agent/tool/task |
| `subject_id` | VARCHAR(64) | NO | 主体 ID |
| `action` | VARCHAR(32) | NO | 调用/写操作/越权拦截 |
| `resource_type` | VARCHAR(32) | NO | 资源类型 |
| `resource_id` | VARCHAR(64) | NO | 资源 ID |
| `risk_level` | TINYINT | NO | 风险等级 |
| `result` | VARCHAR(16) | NO | allow/deny/warn |
| `detail` | JSON | NO | 详情 |
| `trace_id` | VARCHAR(64) | NO | 链路 ID |
| `created_at` | DATETIME(3) | NO | - |

**索引**：`idx_subject_time` (subject_type, subject_id, created_at)、`idx_resource` (resource_type, resource_id)

### 9.2 `permission_policy` 权限策略表（RBAC+ABAC）

| 字段名 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | BIGINT UNSIGNED | NO | 主键 |
| `policy_id` | VARCHAR(32) | NO | 策略 ID |
| `subject_type` | VARCHAR(16) | NO | role=角色 user=用户 agent |
| `subject_id` | VARCHAR(64) | NO | 主体 |
| `resource_type` | VARCHAR(32) | NO | tool/knowledge/agent |
| `resource_id` | VARCHAR(64) | NO | 资源 |
| `action` | VARCHAR(16) | NO | read/write/execute |
| `effect` | VARCHAR(8) | NO | allow/deny |
| `conditions` | JSON | YES | ABAC 条件（时间/IP/参数） |
| `expire_at` | DATETIME(3) | YES | 临时权限过期时间 |
| 通用字段 | - | - | 见 0.1 |

---

## 10. 向量库设计（Milvus）

### 10.1 Collection 规划

| Collection 名 | 用途 | 维度 | 索引 | Partition Key |
|---|---|---|---|---|
| `mem_episodic` | 情景记忆 | 1024 | HNSW (M=16, efC=256) | `domain` |
| `mem_semantic` | 语义记忆 | 1024 | HNSW | `domain` |
| `mem_procedural` | 流程记忆 | 1024 | HNSW | `domain` |
| `kb_chunks_{kbId}` | 知识切片 | 1024 | HNSW | `doc_id` |
| `code_snippet` | 代码片段 | 768 | HNSW + IVF | `language` |
| `tool_index` | 工具语义索引 | 1024 | HNSW | `scene_tag` |

### 10.2 Collection Schema 示例（mem_episodic）

```json
{
  "fields": [
    {"name": "vector_id", "type": "VarChar", "max_length": 64, "is_primary": true},
    {"name": "embedding", "type": "FloatVector", "dim": 1024},
    {"name": "tenant_id", "type": "Int64"},
    {"name": "domain", "type": "VarChar", "max_length": 64},
    {"name": "user_id", "type": "VarChar", "max_length": 64},
    {"name": "agent_id", "type": "Int64"},
    {"name": "memory_id", "type": "VarChar", "max_length": 32},
    {"name": "importance_score", "type": "Float"},
    {"name": "tier", "type": "Int8"},
    {"name": "created_at", "type": "Int64"},
    {"name": "valid", "type": "Bool"}
  ],
  "index": {
    "field": "embedding",
    "type": "HNSW",
    "params": {"M": 16, "efConstruction": 256}
  }
}
```

### 10.3 召回参数基线

| 召回类型 | top_k | ef（搜索） | 备注 |
|---|---|---|---|
| 短期高频 | 20 | 64 | 召回后重排取 5 |
| 长期深度 | 50 | 128 | 召回后重排取 10 |
| 工具召回 | 30 | 64 | 按场景标签前置过滤 |

---

## 11. 图库设计（Neo4j）

### 11.1 节点类型

| 节点标签 | 属性 | 用途 |
|---|---|---|
| `Project` | id, name, language, version | 代码项目 |
| `Module` | id, name, path | 模块 |
| `File` | id, path, language, sha | 文件 |
| `Class` | id, name, modifiers, startLine | 类 |
| `Function` | id, name, signature, return_type | 函数 |
| `Interface` | id, name | 接口 |
| `Dependency` | id, name, version | 第三方依赖 |

### 11.2 关系类型

| 关系 | from → to | 属性 |
|---|---|---|
| `CONTAINS` | Project → Module / Module → File / File → Class/Function | - |
| `CALLS` | Function → Function | callSite, line |
| `IMPLEMENTS` | Class → Interface | - |
| `EXTENDS` | Class → Class | - |
| `DEPENDS_ON` | Module → Dependency | scope=compile/runtime |
| `IMPORTS` | File → File | line |

### 11.3 索引

```cypher
CREATE INDEX project_name IF NOT EXISTS FOR (n:Project) ON (n.name);
CREATE INDEX function_name IF NOT EXISTS FOR (n:Function) ON (n.name);
CREATE INDEX class_name IF NOT EXISTS FOR (n:Class) ON (n.name);
```

---

## 12. DDL 脚本与初始化

所有 DDL 脚本存放于 `infra/sql/`：

```
infra/sql/
├── 01-agent-session.sql      # 会话域
├── 02-agent-task.sql         # 任务域
├── 03-agent-memory.sql       # 记忆域
├── 04-agent-tool.sql         # 工具域
├── 05-agent-model.sql        # 模型网关域
├── 06-agent-repo.sql         # Agent 仓库域
├── 07-agent-knowledge.sql    # 知识库域
├── 08-agent-quality.sql      # 质量评估域
├── 09-agent-risk.sql         # 风控合规域
├── 10-milvus-collections.json # Milvus Collection 初始化
├── 11-neo4j-constraints.cypher # Neo4j 约束与索引
├── 12-clickhouse-tables.sql  # ClickHouse 表
└── 99-init-data.sql          # 初始化数据（路由规则/默认模板等）
```

初始化脚本示例（`infra/sql/02-agent-task.sql` 片段）：

```sql
CREATE DATABASE IF NOT EXISTS agent_task DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE agent_task;

CREATE TABLE IF NOT EXISTS task_instance (
  id              BIGINT UNSIGNED NOT NULL COMMENT '主键',
  task_id         VARCHAR(32)     NOT NULL COMMENT '业务任务ID',
  tenant_id       BIGINT UNSIGNED NOT NULL COMMENT '租户ID',
  session_id      VARCHAR(32)     NULL     COMMENT '关联会话',
  user_id         VARCHAR(64)     NOT NULL COMMENT '发起人',
  title           VARCHAR(255)    NOT NULL COMMENT '任务标题',
  goal            TEXT            NOT NULL COMMENT '任务目标',
  complexity      TINYINT         NOT NULL COMMENT '1=L1 2=L2 3=L3',
  status          VARCHAR(16)     NOT NULL COMMENT '状态',
  task_schema     JSON            NOT NULL COMMENT 'Task Schema',
  dag_id          BIGINT UNSIGNED NULL     COMMENT 'DAG ID',
  agent_id        BIGINT UNSIGNED NULL     COMMENT '关联Agent',
  priority        TINYINT         NOT NULL DEFAULT 5,
  parent_task_id  VARCHAR(32)     NULL,
  replan_count    INT UNSIGNED    NOT NULL DEFAULT 0,
  cost_limit_cent BIGINT          NOT NULL DEFAULT 0,
  cost_used_cent  BIGINT          NOT NULL DEFAULT 0,
  token_used      INT UNSIGNED    NOT NULL DEFAULT 0,
  started_at      DATETIME(3)     NULL,
  finished_at     DATETIME(3)     NULL,
  error_code      VARCHAR(32)     NULL,
  error_msg       TEXT            NULL,
  result_summary  TEXT            NULL,
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  created_by      VARCHAR(64)     NULL,
  updated_by      VARCHAR(64)     NULL,
  deleted         TINYINT(1)      NOT NULL DEFAULT 0,
  version         INT UNSIGNED    NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_id (task_id),
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_session (session_id),
  KEY idx_parent (parent_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务实例表';
```

完整 DDL 脚本将在编码实现阶段产出，本设计文档定义表结构契约。

---

## 13. 数据治理

### 13.1 数据生命周期

| 数据类型 | 保留期 | 归档/清理策略 |
|---|---|---|
| 会话消息 | 90 天热 | 90 天后摘要化归档至对象存储 |
| 任务步骤日志 | 30 天热 | 30 天后转 ClickHouse 冷存 |
| 工具/模型调用日志 | 90 天 | 按月分表，6 个月后归档 MinIO |
| 长期记忆 | 永久 | 按 tier 分级，冷记忆 1 年后归档 |
| 审计日志 | 2 年 | 合规要求，2 年后合规归档 |
| Badcase | 永久 | 持续回流优化 |

### 13.2 数据一致性

- **强一致**：任务状态机、配额扣减、权限策略 → MySQL + 乐观锁
- **最终一致**：记忆写入、调用日志归集 → RocketMQ 事务消息
- **会话态**：Redis 主存 + MySQL 异步持久化，崩溃后从 MySQL 恢复最近 5 轮

### 13.3 数据脱敏

调用日志的 `input`/`output` 字段在落库前经过 `risk-control` 的脱敏过滤器，识别并替换：
- 手机号、身份证、银行卡（正则匹配）
- API Key、Token（模式匹配）
- 业务敏感字段（按 domain 配置）
