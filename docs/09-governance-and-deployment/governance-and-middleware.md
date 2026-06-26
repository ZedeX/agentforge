# 治理体系 + 中间件集成 + 部署配置 详细设计

> 文档版本：v1.0  |  更新日期：2026-06-26  |  对应 PRD：第四节/第五节/第六节/第七节交付物5,7
>
> 本文档合并覆盖 PRD 第四节（大模型幻觉治理体系）、第五节（静默漂移管控体系）、第六节（成本管控与模型选型体系），以及第七节交付物 5（配置文件与部署说明）、交付物 7（基础中间件集成方案）。
>
> **依赖文档**：
> - [00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md)（技术栈版本、微服务拆分、横向体系落地方式）
> - [01-database/database-schema-design.md](../01-database/database-schema-design.md)（数据表契约：`model_route_rule` / `eval_baseline` / `badcase` / `audit_log` / `permission_policy` / `agent_metrics_daily` / `agent_version` / `tool_quota` / `model_usage_log` / `model_provider` / Milvus Collections / Neo4j 约束）
> - [02-api/api-specification.md](../02-api/api-specification.md)（质量治理 API、风控审计 API、限流熔断、RocketMQ Topic 清单）
> - [PRD.md](../../PRD.md) 第四节/第五节/第六节/第七节

## 0. 文档导览

| 篇 | 章 | 主题 | 对应 PRD |
|---|---|---|---|
| 第一篇 治理体系 | §1 | 大模型幻觉治理体系 | PRD 第四节 |
| 第一篇 治理体系 | §2 | 静默漂移管控体系 | PRD 第五节 |
| 第一篇 治理体系 | §3 | 成本管控与模型选型 | PRD 第六节 |
| 第二篇 中间件集成 | §4 ~ §11 | MySQL / Milvus / Redis / RocketMQ / ES / Neo4j / MinIO / 可观测 | PRD 第七节交付物 7 |
| 第三篇 配置与部署 | §12 ~ §15 | Nacos / Vault / Dockerfile / K8s / 部署拓扑 / 运维脚本 | PRD 第七节交付物 5 |

技术栈版本统一引用 [00-overview 文档第 2.1 节](../00-overview/tech-stack-and-architecture.md#21-技术栈总览)：MySQL 8.0.36、Milvus 2.4、Redis 7.2、RocketMQ 5.3、ES 8.13、Neo4j 5.18、MinIO RELEASE.2024-03、SkyWalking 9.7、Prometheus 2.51、K8s 1.29。

---

# 第一篇 治理体系

## 1. 大模型幻觉治理体系（PRD 第四节）

### 1.1 幻觉分类与影响域

| 分类 | 典型表现 | 影响域 | 关联治理层 |
|---|---|---|---|
| 事实性幻觉 | 编造订单号、虚构政策条款、伪造引用来源 | 业务正确性、合规风险 | L1/L2/L3/L4 |
| 工具调用幻觉 | 选错工具名、参数类型非法、虚构工具返回 | 任务执行链路 | L3/L5 |
| 规划与逻辑幻觉 | 子任务遗漏、推导自相矛盾、执行路径不可行 | 多步任务成功率 | L2/L4/L5 |
| 记忆幻觉 | 错误召回历史、混淆跨会话、虚构用户偏好 | 个性化体验 | L5 |
| 格式幻觉 | 字段缺失、结构错乱、不满足 Schema 约定 | 下游解析与展示 | L4 |

### 1.2 六层全链路治理架构

治理动作与执行服务映射如下，确保每一层均有明确落地组件：

```
┌────────────────────────────────────────────────────────────────────────┐
│  L6 长效闭环         quality-service + risk-control (Badcase 归集)      │
├────────────────────────────────────────────────────────────────────────┤
│  L5 Agent 专项       memory-service / planning-service / tool-engine    │
├────────────────────────────────────────────────────────────────────────┤
│  L4 多层级输出校验    quality-service (规则/事实/终审 三级)              │
├────────────────────────────────────────────────────────────────────────┤
│  L3 知识与工具锚定    knowledge-service + tool-engine + model-gateway   │
├────────────────────────────────────────────────────────────────────────┤
│  L2 推理过程自校验    agent-runtime (Reflexion / 分步自检 / 拒答)        │
├────────────────────────────────────────────────────────────────────────┤
│  L1 模型选型与前置约束  model-gateway (model_route_rule 路由 + Prompt)    │
└────────────────────────────────────────────────────────────────────────┘
```

#### 1.2.1 第一层：模型选型与前置约束（源头防控）

**a. 分层模型路由**

依据 [数据库文档 §5.2 `model_route_rule` 表](../01-database/database-schema-design.md#52-model_route_rule-模型路由规则表) 的 `scene` / `tier` 字段，由 `model-gateway` 在 `Chat()` 入口执行路由匹配。规则优先级 `priority` 字段升序，匹配条件 `condition` 支持 `tenantId` / `domain` 维度：

```yaml
# model_route_rule 初始化数据示例（节选）
- rule_id: rl_intent_light_default
  scene: intent
  tier: light
  preferred_model: qwen-turbo
  fallback_models: ["deepseek-chat", "gpt-3.5-turbo"]
  priority: 100
  condition: {}
  status: 1

- rule_id: rl_planning_strong_default
  scene: planning
  tier: strong
  preferred_model: gpt-4o
  fallback_models: ["claude-3-5-sonnet", "qwen-max"]
  priority: 100
  condition: {}

- rule_id: rl_audit_strong_fin
  scene: audit
  tier: strong
  preferred_model: claude-3-5-sonnet
  fallback_models: ["gpt-4o"]
  priority: 50
  condition: {"domain": "finance"}
  status: 1
```

路由伪代码：

```java
public ModelRoute resolve(String scene, String tier, TraceContext ctx) {
    List<ModelRouteRule> rules = routeRuleCache.findBySceneTier(scene, tier);
    for (ModelRouteRule r : rules) {
        if (conditionMatcher.match(r.getCondition(), ctx)) {
            return new ModelRoute(r.getPreferredModel(), r.getFallbackModels());
        }
    }
    return defaultRoute;
}
```

**b. 强约束系统提示词模板**

`agent_definition.core_constraints` 字段（[数据库文档 §6.1](../01-database/database-schema-design.md#61-agent_definition-agent-定义表)）固化为独立区段，与 `business_config` 分离，触发上下文压缩时优先保留。模板示例：

```text
# 核心约束（不可省略）
1. 涉及金额、订单号、合同编号等事实性数据，必须通过工具查询获取，禁止基于上下文推断。
2. 输出中所有事实陈述必须标注 [来源:知识库/工具名/任务输入]，无来源视为疑似幻觉。
3. 信息不足以回答时，必须输出 `NEED_MORE_INFO` 并说明缺失项，禁止编造。
4. 工具调用入参必须严格匹配 inputSchema 类型与必填项，禁止使用未知工具名。
5. 输出结构必须符合约定 Schema（参考 §output_schema），缺失字段判定为格式幻觉。

# 业务配置（可压缩）
- 默认分页大小：{{businessConfig.defaultPageSize}}
- 输出语言：{{businessConfig.outputLang}}
```

**c. 低温度参数**

事实类与规划类场景强制低温度，由 `model-gateway` 在 `ModelParams` 注入：

| scene | temperature | top_p | 备注 |
|---|---|---|---|
| intent | 0.1 | 0.8 | 意图识别求稳定 |
| planning | 0.2 | 0.9 | 规划需可控创造性 |
| tool_call | 0.0 | 0.7 | 工具调用零随机 |
| summary | 0.3 | 0.9 | 汇总允许轻度发挥 |
| audit | 0.0 | 0.6 | 终审最严格 |

#### 1.2.2 第二层：推理过程自校验（生成中抑制）

**a. 分步推理 + 中间自检**

`agent-runtime` 在 ReAct 循环的 `think` 阶段插入自检子步，仅当自检通过才进入 `act`。自检 prompt 模板：

```text
[SELF_CHECK]
当前思考：{{think_content}}
请按以下三问自检：
1. 事实性：本步引用的事实是否来自上下文/工具/知识库？若否，标记 SUSPECT。
2. 完备性：是否遗漏了用户问题中的关键约束？
3. 一致性：是否与前序步骤结论矛盾？
输出 JSON：{"pass": true|false, "reason": "...", "fix_hint": "..."}
```

自检失败触发 Reflexion 重试（受 `agent_definition.reflection_mode` 控制：`none` / `single` / `multi`，最多 2 轮）。

**b. Reflexion 反思机制**

参考 [02-api 文档 §5](../02-api/api-specification.md#5-模型网关-apigrpc内部) `ModelParams.enable_cot` 字段，强制开启思维链。多轮反思由 `agent-runtime` 维护反思缓冲区，每次反思后注入提示词：

```text
[REFLECTION #{{n}}]
上一轮输出被判定为：{{issue}}
修正建议：{{fix_hint}}
请基于修正建议重新生成。
```

**c. 来源标注**

`agent_definition.system_prompt` 强制要求输出中所有事实陈述携带 `[来源:xxx]` 标记；`quality-service` 在 L4 阶段扫描无来源事实陈述计为疑似幻觉。

**d. 拒答机制**

模型输出包含 `NEED_MORE_INFO` 关键字时，`agent-runtime` 直接短路终止当前步并触发用户追问或工具补查：

```java
if (response.content.contains("NEED_MORE_INFO")) {
    return StepResult.needsMoreInfo(extractMissing(response.content));
}
```

#### 1.2.3 第三层：外部知识与工具锚定（核心手段）

**a. RAG 强制召回**

事实类与代码类任务在 `model-gateway.Chat()` 前由 `memory-service.Recall()` + `knowledge-service.Retrieve()` 强制注入知识，召回为空则直接拒答：

| 任务类型 | 召回强制 | 空召回行为 |
|---|---|---|
| 事实查询（金额、订单） | 是 | 拒答并提示 NEED_MORE_INFO |
| 代码生成 | 是（召回代码片段） | 拒答并要求补充上下文 |
| 闲聊/文案 | 否 | 正常生成 |
| 规划 | 是（召回历史同场景模板） | 降级到通用智能规划 |

召回结果通过 `RecalledMemory.source_type` + `source_task_id` 字段（[数据库文档 §3.1](../01-database/database-schema-design.md#31-memory_long_term-长期记忆元数据表)）携带溯源信息，注入 Prompt 时保留来源标签。

**b. 工具网关前置校验**

依据 [ADR-005](../00-overview/tech-stack-and-architecture.md#adr-005工具调用统一走-grpc-网关而非直连)，所有工具调用必经 `tool-engine.ToolGateway.Invoke`。前置校验链：

```java
public ToolInvokeResponse invoke(ToolInvokeRequest req) {
    // 1. 工具存在性 + 版本有效性
    ToolRegistry tool = registry.find(req.getToolId(), req.getToolVersion());
    if (tool == null) return blocked("TOOL_NOT_FOUND");
    // 2. JSON Schema 校验（参数类型、必填、枚举）
    ValidationResult v = schemaValidator.validate(req.getInputJson(), tool.getInputSchema());
    if (!v.isValid()) return blocked("PARAM_INVALID", v.getErrors());
    // 3. RBAC + ABAC 权限校验（risk-control.preCheck）
    PermissionResult p = riskControl.preCheck(req.getTrace(), tool, req.getInputJson());
    if (p.isDenied()) return blocked("FORBIDDEN");
    // 4. 配额校验（tool_quota）
    if (!quotaManager.tryAcquire(tool, req.getTrace())) return blocked("QUOTA_EXCEEDED");
    // 5. 风险等级处理（R3 走审批）
    if (tool.getRiskLevel() == 3) return awaitApproval(req, tool);
    // 6. 执行
    return executor.execute(req, tool);
}
```

**c. 多源交叉验证**

核心数据（订单、账户、金额）采用至少两个数据源交叉验证：

```yaml
cross_validation:
  enabled_scenes: [order_query, account_balance, payment_amount]
  sources:
    - tool: query_order_db
      weight: 1.0
    - tool: query_order_cache
      weight: 0.9
  strategy: majority  # majority | all_match | weighted
  mismatch_action: block_and_alert
```

**d. 结构化注入**

工具返回结果以结构化 JSON 注入 Prompt，并显式标记为「事实基准」：

```text
[FACT_BASE]
{{tool_output_json}}
[/FACT_BASE]
请基于上述事实基准回答，禁止引用未在 FACT_BASE 中的具体数值。
```

#### 1.2.4 第四层：多层级输出校验（后置兜底）

由 `quality-service` 实现，对接 [02-api 文档 §9 质量治理 API](../02-api/api-specification.md#9-质量与治理-apirest)。三级校验对应 PRD 第三节「异常容错与质量保障体系」：

| 级别 | 校验类型 | 实现方式 | 适用场景 | 成本 |
|---|---|---|---|---|
| L4-1 | 规则化硬校验 | JSON Schema + 正则 + 关键字 | 全量输出 | 零成本，必执行 |
| L4-2 | 事实一致性校验 | 语义比对输出 vs 工具/知识库返回 | 工具类任务必执行 | 中（embedding 比对） |
| L4-3 | 综合质量终审 | 强模型（tier=strong）全维度评分 | 高风险场景 + 子任务 | 高（强模型调用） |

L4-1 规则示例：

```yaml
hard_rules:
  - id: format_schema
    type: json_schema
    schema_ref: output_schema
  - id: source_coverage
    type: regex
    pattern: "\\[来源:.*\\]"
    min_occurrences: 1
  - id: no_banned_words
    type: keyword_blacklist
    words: ["保证", "绝对", "100%"]
```

L4-2 事实一致性校验伪代码：

```java
public ConsistencyResult checkConsistency(Output output, List<Fact> facts) {
    Embedding outEmb = embeddingClient.embed(output.getFactStatements());
    for (Fact f : facts) {
        double sim = cosine(outEmb, f.getEmbedding());
        if (sim < 0.75) result.addInconsistency(f, sim);
    }
    return result;
}
```

L4-3 终审 prompt 模板：

```text
[AUDIT]
任务目标：{{task.goal}}
事实基准：{{facts}}
模型输出：{{output}}
请从以下维度评分（0-1）：
1. 事实准确度
2. 完备性
3. 逻辑一致性
4. 格式合规
输出 JSON：{"scores": {...}, "overall": 0.xx, "pass": true|false, "issues": [...]}
```

#### 1.2.5 第五层：Agent 专属专项治理

**a. 记忆幻觉治理（memory-service）**

| 治理动作 | 落地实现 |
|---|---|
| 写入校验 | `WriteLongTerm` 入口校验：内容不得为空、必须有 `source_task_id`、embedding 与同 domain 已有记忆相似度 > 0.95 触发合并而非新增（[数据库文档 §3.1](../01-database/database-schema-design.md#31-memory_long_term-长期记忆元数据表) `valid` 字段） |
| 召回带源 | `RecalledMemory` 强制返回 `source_type` / `source_task_id`，注入 Prompt 时保留 |
| 定期蒸馏 | XXL-Job 定时任务触发 `TriggerDistill`，按 `domain` 聚合同主题记忆（[数据库文档 §3.2 `memory_distill_log`](../01-database/database-schema-design.md#32-memory_distill_log-记忆蒸馏日志表)） |
| 分域隔离 | Milvus Collection 按 `domain` Partition（[数据库文档 §10.1](../01-database/database-schema-design.md#101-collection-规划)），避免跨域召回污染 |

**b. 规划幻觉治理（planning-service）**

`PlanningService.ValidatePlan` 在 DAG 生成后强制执行五维校验：

```java
public ValidateResponse validate(Dag dag) {
    ValidateResponse r = new ValidateResponse();
    // 1. 完备性：所有子任务输出可追溯到目标交付物
    r.add(checkCompleteness(dag));
    // 2. 原子性：每个节点是否原子（不可再分）
    r.add(checkAtomicity(dag));
    // 3. 效率：是否存在冗余路径
    r.add(checkEfficiency(dag));
    // 4. 成本：预估总成本是否超 cost_limit_cent
    r.add(checkCost(dag));
    // 5. 容错：R3 工具节点是否有兜底
    r.add(checkFaultTolerance(dag));
    // DAG 合法性：环检测
    if (hasCycle(dag)) r.addError("DAG_CYCLE_DETECTED");
    return r;
}
```

执行偏差校验由 `task-orchestrator` 在每个子任务完成回调 `ReportSubtaskResult` 时比对实际 vs 预期输出 Schema：

```java
if (!schemaMatch(actualOutput, expectedSchema)) {
    triggerReplan(taskId, ReplanMode.INCREMENTAL, "OUTPUT_SCHEMA_MISMATCH");
}
```

**c. 工具幻觉治理（tool-engine）**

| 治理动作 | 落地实现 |
|---|---|
| 前置拦截 | ToolGateway.Invoke 入口校验工具存在性、参数 Schema、权限、配额（见 §1.2.3 b） |
| 语义对齐 | 模型生成的工具调用意图与 `tool_registry.description` embedding 相似度 < 0.6 判定为选错工具，返回 `TOOL_MISMATCH` |
| 结果覆盖 | 工具实际返回结果（`output_json`）为唯一事实基准，模型后续输出中如有与工具结果冲突的事实陈述，L4-2 一致性校验直接打回 |

#### 1.2.6 第六层：长效闭环优化

**a. Badcase 归集**

依据 [数据库文档 §8.3 `badcase` 表](../01-database/database-schema-design.md#83-mysqlbadcase-异常案例表)，由 `quality-service` 在以下场景自动归集：

| 触发源 | category | severity |
|---|---|---|
| L4 校验失败 | hallucination | 由 L4-3 评分决定 |
| 用户反馈 `rating=negative` | 视具体 case 自动分类 | 默认中 |
| 漂移告警（见 §2） | drift | 高 |
| 工具调用失败累计超阈值 | tool_error | 中 |
| 重规划触发 | plan_error | 中 |

API：[02-api 文档 §9.2](../02-api/api-specification.md#92-badcase-管理) `POST /api/v1/badcases` 上报，`POST /api/v1/badcases/{caseId}/analyze` 提交根因分析。

**b. 迭代提示词规则知识库**

`badcase.root_cause` + `fix_action` 字段沉淀后，由人工运营定期提炼规则更新到：
- `agent_definition.system_prompt` / `core_constraints`（发布新版本，触发 `agent_version` 版本递增）
- L4-1 规则化硬校验配置
- 工具 `input_schema` 约束

**c. 幻觉率指标追踪**

写入 ClickHouse `agent_metrics_daily` 表（[数据库文档 §8.4](../01-database/database-schema-design.md#84-clickhouseagent_metrics_daily-agent-指标日表)）的 `hallucination_count` 字段，按 `tenant_id` / `agent_id` / `date` 维度聚合。Grafana Dashboard 配置幻觉率告警阈值：日幻觉率 > 5% 触发 P2 告警，> 10% 触发 P1 告警并自动暂停 Agent（置 `agent_definition.status=3`）。

### 1.3 分级治理策略矩阵

| 场景风险等级 | 适用业务 | 治理层级 | 终审方式 | 抽样率 |
|---|---|---|---|---|
| 高风险 | 金融、法律、医疗、生产操作 | L1 + L2 + L3 + L4 + L5 + L6（全六级） | 强模型 + 人工终审 | 100% |
| 中风险 | 调研、代码、数据分析 | L1 + L2 + L3 + L4 + L5 | 自动治理 | 10% 抽样人工复核 |
| 低风险 | 文案、闲聊、信息整理 | L1 + L4-1 | 基础规则校验 | 1% 抽样 |

分级标识在 `task_instance.task_schema.constraints` 中携带 `risk_level` 字段，由 `task-orchestrator` 在 DAG 分发时透传给 `agent-runtime`，运行时据此选择治理策略：

```yaml
# task_schema.constraints 示例
constraints:
  risk_level: high
  governance:
    layers: [L1, L2, L3, L4, L5, L6]
    final_audit: model_strong + human
    sampling_rate: 1.0
```

---

## 2. 静默漂移管控体系（PRD 第五节）

### 2.1 漂移分类与监测面

| 漂移类型 | 监测主体 | 触发动作 | 关联服务 |
|---|---|---|---|
| 行为漂移 | 工具调用率、规划步数、输出风格长度、拒答率 | 同向趋势连续 3 日超阈值 → 任务级纠偏 | quality-service / observability |
| 效果漂移 | 任务完成率、准确率、幻觉率 | 7 日滑动均值下降 > 5% → 系统级纠偏 | quality-service |
| 对齐漂移 | 角色一致性、软违规内容占比 | 单次软违规 > 阈值 → 任务级；连续趋势 → 系统级 | risk-control |
| 记忆漂移 | 错误记忆占比、召回相关性趋势 | 错误率 > 5% → 记忆漂移专项 | memory-service |
| 模型漂移 | 底层大模型版本更新后整体能力分布 | 适配验证失败 → 回滚稳定版 | model-gateway / quality-service |

### 2.2 四层管控闭环

#### 2.2.1 第一层：基准锚定体系

**a. 三类黄金基准集**

依据 [数据库文档 §8.2 `eval_baseline` 表](../01-database/database-schema-design.md#82-mysqleval_baseline-黄金基准集表)，`baseline_type` 字段区分三类：

| baseline_type | 用途 | 样本规模建议 | golden_metrics 示例 |
|---|---|---|---|
| behavior | 行为基准（工具调用率、规划步数分布） | 200+ | `{"tool_call_rate": [0.3, 0.5], "avg_steps": [3, 6]}` |
| effect | 效果基准（成功率、准确率、幻觉率） | 100+ | `{"success_rate": 0.92, "accuracy": 0.88}` |
| alignment | 对齐基准（角色一致性、合规边界） | 50+ | `{"role_consistency": 0.95, "soft_violation_rate": 0.02}` |

API：[02-api 文档 §9.3](../02-api/api-specification.md#93-治理配置) `GET/PUT /api/v1/governance/baselines`。

**b. 全要素版本化**

`agent_version` 表（[数据库文档 §6.2](../01-database/database-schema-design.md#62-agent_version-agent-版本表)）的 `is_stable` 字段标记当前稳定版本，作为系统级漂移回滚目标。所有影响 Agent 行为的变更（system_prompt / core_constraints / business_config / bound_tools / model_tier）必须发布新版本：

```sql
-- 发布新版本
INSERT INTO agent_version (agent_id, version, snapshot, change_log, published_by, published_at, is_stable)
VALUES ('ag_1001', 3, '{}', '更新工具召回策略', 'u_dev', NOW(), 0);

-- 验证通过后置为稳定版
UPDATE agent_version SET is_stable = 1 WHERE agent_id = 'ag_1001' AND version = 3;
UPDATE agent_version SET is_stable = 0 WHERE agent_id = 'ag_1001' AND version <> 3;
```

**c. 核心约束固化**

`agent_definition.core_constraints` 与 `business_config` 字段分离（[数据库文档 §6.1](../01-database/database-schema-design.md#61-agent_definition-agent-定义表)），`memory-service.LoadShortTerm` 返回的 `ShortTermMemory.core_constraints` 字段独立传输（[02-api 文档 §4](../02-api/api-specification.md#4-记忆管理-apigrpc内部) `ShortTermMemory` 消息）。压缩策略（见 §6 后续章节）优先保留 `core_constraints`，仅压缩 `business_config`。

#### 2.2.2 第二层：多维度监测体系

**a. 行为指标监控**

采集到 ClickHouse `agent_metrics_daily`（[数据库文档 §8.4](../01-database/database-schema-design.md#84-clickhouseagent_metrics_daily-agent-指标日表)）已有字段：`tool_call_total` / `tool_call_success` / `tool_select_error` / `avg_token`。扩展漂移监测明细表 `agent_drift_metrics_daily`（见 §2.3）。

监测规则：连续 3 日同向趋势（持续上升或下降）且偏离基准 ±15% 触发任务级纠偏告警。

**b. 效果质量监控**

| 监测方式 | 频率 | 实现 |
|---|---|---|
| 在线抽样评测 | 实时，1% 任务 | `quality-service` 异步消费 `task.subtask.done`，按 1% 抽样触发 L4-3 终审 |
| 定期全量回归 | 每周 | XXL-Job 触发 `eval_task`（type=offline），全量跑 `eval_baseline` 样本 |
| 隐性反馈挖掘 | 每日 | 聚合用户反馈 `rating=negative` + 重规划次数 + 任务失败率 |

**c. 对齐合规监控**

`risk-control` 异步审计工具调用、模型输出，计算角色一致性（输出风格 embedding 与基准比对）与软违规占比（命中软规则但未硬拦截的次数占比）。阈值：单次软违规 > 5% 触发任务级纠偏，7 日滑动均值 > 3% 触发系统级纠偏。

**d. 记忆质量监控**

`memory-service` 每日 XXL-Job 触发记忆抽检：

```sql
-- 错误记忆占比（valid=0 的记忆占该 domain 总记忆比例）
SELECT domain, COUNT(CASE WHEN valid=0 THEN 1 END) * 1.0 / COUNT(*) AS error_rate
FROM memory_long_term
WHERE tenant_id = ? AND updated_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY domain;
```

阈值：error_rate > 5% 触发记忆漂移专项纠偏。

#### 2.2.3 第三层：分级自动纠偏

| 漂移级别 | 触发条件 | 纠偏动作 | 实现组件 |
|---|---|---|---|
| 会话级（轻度） | 单会话内行为偏差 | 每轮注入 `core_constraints` 摘要抵消上下文稀释 | memory-service.LoadShortTerm |
| 任务级（中度） | L4 校验连续打回 / 行为指标单日偏离 > 30% | 切换更严格 Prompt 模板（增补约束）+ L4-3 强制终审 | agent-runtime + quality-service |
| 系统级（重度） | 7 日滑动均值下降 > 5% / 模型版本更新失败 | 自动回滚 `agent_version.is_stable=1` 版本 + 切换备用模型 | agent-repo + model-gateway |
| 记忆漂移专项 | error_rate > 5% | 错误记忆 `valid=0` 标记失效、过期记忆 `ttl_at` 自动归档、低相关记忆 `importance_score` 降权 | memory-service |

**会话级纠偏 Prompt 注入示例**：

```text
[DRIFT_CORRECTION]
检测到本会话存在偏离角色设定的趋势，请严格遵循以下核心约束：
{{core_constraints_summary}}
若发现继续偏离，将主动终止会话。
[/DRIFT_CORRECTION]
```

**系统级回滚伪代码**：

```java
public void rollbackToStable(String agentId, String reason) {
    AgentVersion stable = versionRepo.findStable(agentId);
    if (stable == null) {
        alert("NO_STABLE_VERSION: " + agentId);
        return;
    }
    // 切换 Agent 当前指向稳定版本
    agentRepo.updateCurrentVersion(agentId, stable.getVersion());
    // 切换备用兜底模型
    modelGateway.switchFallback(agentId);
    // 触发告警
    alertService.notify(P1, "AGENT_DRIFT_ROLLBACK: " + agentId + " reason=" + reason);
}
```

#### 2.2.4 第四层：根因治理与长效闭环

**a. 标准化漂移归因**

`badcase` 表 `category=drift` 记录关联变更：

```sql
SELECT b.case_id, b.description, b.root_cause, b.fix_action,
       av.version, av.change_log, av.published_at, av.published_by
FROM badcase b
LEFT JOIN agent_version av ON b.agent_id = av.agent_id
   AND av.published_at <= b.created_at
WHERE b.category = 'drift'
ORDER BY b.created_at DESC;
```

**b. 灰度发布 AB 对比**

`agent_version` 新版本发布采用灰度策略，10% → 30% → 100% 阶梯放量：

```yaml
canary_release:
  stages:
    - weight: 10
      duration: 24h
      rollback_on: { success_rate_drop: 0.05, hallucination_rate_rise: 0.02 }
    - weight: 30
      duration: 48h
      rollback_on: { success_rate_drop: 0.03, hallucination_rate_rise: 0.01 }
    - weight: 100
      promote_to_stable: true
```

**c. 定期记忆蒸馏校验**

XXL-Job 每周触发全量记忆蒸馏 + 校验：

```java
public void weeklyDistillCheck() {
    List<String> domains = memoryService.listAllDomains();
    for (String d : domains) {
        DistillAck ack = memoryService.triggerDistill(
            DistillRequest.newBuilder()
                .setDomain(d)
                .setSummaryLevel(2)  // 主题级蒸馏
                .build());
        // 蒸馏后错误记忆占比仍 > 5% 触发告警
        if (memoryService.errorRate(d) > 0.05) {
            alertService.notify(P2, "MEMORY_DISTILL_FAIL: " + d);
        }
    }
}
```

**d. 模型版本提前适配验证**

`model-gateway` 监听供应商模型版本更新通知（订阅供应商 webhook），新版本接入前必须通过 `eval_baseline` 全量回归：

```java
public boolean preAdaptValidation(String newModelVersion) {
    EvalTask task = qualityService.createEval(
        EvalRequest.builder().type("offline").baselineId(...).modelVersion(newModelVersion).build());
    EvalResult r = qualityService.waitResult(task.getEvalId());
    return r.getSuccessRate() >= 0.90 && r.getHallucinationRate() <= 0.05;
}
```

### 2.3 漂移监测指标 ClickHouse 字段设计

在 `agent_metrics_daily` 之外，新增漂移监测明细表，用于细粒度分析与告警：

```sql
-- ClickHouse DDL，存放于 infra/sql/12-clickhouse-tables.sql
CREATE TABLE agent_drift_metrics_daily (
    `date`             Date,
    `tenant_id`        UInt64,
    `agent_id`         String,
    `agent_version`    UInt32,
    `model`            String,
    -- 行为漂移指标
    `tool_call_rate`         Float64,  -- 工具调用率（调用工具的任务占比）
    `avg_plan_steps`         Float64,  -- 平均规划步数
    `avg_output_length`      UInt32,   -- 平均输出长度
    `refusal_rate`           Float64,  -- 拒答率
    -- 效果漂移指标
    `task_success_rate`      Float64,  -- 任务成功率
    `accuracy_score`         Float64,  -- 准确率（L4-3 评分均值）
    `hallucination_rate`     Float64,  -- 幻觉率
    -- 对齐漂移指标
    `role_consistency`      Float64,  -- 角色一致性
    `soft_violation_rate`   Float64,  -- 软违规占比
    -- 记忆漂移指标
    `memory_error_rate`     Float64,  -- 错误记忆占比
    `recall_relevance_avg`  Float64,  -- 召回相关性均值
    -- 偏离基准程度
    `drift_score`           Float64,  -- 综合漂移分（0~1，>0.3 告警）
    `drift_level`           String    -- none | session | task | system | memory
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, tenant_id, agent_id, agent_version);
```

`drift_score` 计算规则：

```text
drift_score = 0.4 * (1 - task_success_rate / baseline.success_rate)
            + 0.3 * (hallucination_rate / baseline.hallucination_rate - 1)
            + 0.2 * |tool_call_rate - baseline.tool_call_rate| / baseline.tool_call_rate
            + 0.1 * memory_error_rate
```

Grafana Dashboard 配置漂移趋势面板，告警规则：
- `drift_score > 0.3` 持续 3 日 → P3 告警，触发任务级纠偏
- `drift_score > 0.5` 单日 → P2 告警，触发系统级纠偏
- `drift_score > 0.7` 单日 → P1 告警，自动回滚稳定版

---

## 3. 成本管控与模型选型（PRD 第六节）

### 3.1 全链路分环节模型选型标准

依据 [数据库文档 §5.2 `model_route_rule`](../01-database/database-schema-design.md#52-model_route_rule-模型路由规则表) 的 `scene` / `tier` 字段，全链路分环节选型标准如下：

| 链路环节 | scene | tier | 核心诉求 | 推荐模型 |
|---|---|---|---|---|
| 意图识别 / 任务路由 | intent | light | 低成本、低延迟、高并发 | qwen-turbo / gpt-3.5-turbo / deepseek-chat |
| 任务规划 / 复杂推理 | planning | strong | 高准确率、低幻觉、强逻辑 | gpt-4o / claude-3-5-sonnet / qwen-max |
| 工具调用 / 单步执行 | tool_call | middle | 平衡效果与成本 | qwen-plus / gpt-4o-mini / claude-3-haiku |
| 结果汇总 / 格式校验 | summary | middle | 低成本、格式合规 | qwen-plus / gpt-4o-mini |
| 质量终审 / 高风险场景 | audit | strong | 零容错、效果兜底 | gpt-4o / claude-3-5-sonnet |

路由调用示例（[02-api 文档 §5](../02-api/api-specification.md#5-模型网关-apigrpc内部) `ChatRequest`）：

```protobuf
// 工具调用环节
ChatRequest req = ChatRequest.newBuilder()
    .setScene("tool_call")
    .setTier("middle")
    .setEnablePromptCache(true)
    .setParams(ModelParams.newBuilder().setTemperature(0.0).setTopP(0.7))
    .build();
```

### 3.2 核心成本测算规则

**a. 单任务总成本公式**

```
单任务总成本 = Σ 模型调用成本 + Σ 工具调用成本 + 基础设施分摊成本

其中：
模型调用成本 = Σ (input_tokens × input_price + output_tokens × output_price)
工具调用成本 = Σ tool_registry.avg_cost_cent
基础设施成本 ≈ 任务执行时长 × 单位时间基础设施单价（CPU/内存/存储分摊）
```

**b. Token 消耗基准**

依据 [00-overview 文档 §1.2](../00-overview/tech-stack-and-architecture.md#12-非功能性指标基线)「单任务平均 Token ≤ 30K（中等任务基线）」，按任务复杂度三级建立基准：

| 复杂度 | task_instance.complexity | 模型调用次数预估 | Token 消耗基准 | 成本上限建议（分） |
|---|---|---|---|---|
| 简单（L1） | 1 | 1~3 | 5K | 50 |
| 中等（L2） | 2 | 3~8 | 30K | 500 |
| 复杂（L3） | 3 | 8~20 | 100K | 5000 |

`task_instance.cost_limit_cent` 字段在任务创建时按上表自动填充，调用方也可显式指定。

**c. 中文 Token 密度修正**

中文 Token 密度为英文的 1.5~2 倍，`model-gateway.CountTokens` 接口对中文文本按 1.7 倍系数估算（精确计数依赖各供应商 tokenizer）。

**d. 主流模型计费参考表**（回填自 [10-supplement §2](../10-supplement/detail-mrd-gap-fill.md#2-主流模型详细计费参考表)，2026 年公开 API 官方价，实际以 `model_provider` 表配置为准）

**d-1. 海外主流模型（美元/百万 Token）**

| 模型 | 输入单价 | 输出单价 | 最大上下文 | 档位 | 核心备注 |
|---|---|---|---|---|---|
| GPT-4o | $5.00 | $15.00 | 128K | strong | 综合能力均衡，工具调用成熟 |
| GPT-4o Mini | $0.15 | $0.60 | 128K | light | 性价比极高，轻量任务首选 |
| Claude 3.5 Sonnet | $3.00 | $15.00 | 200K | strong | 长上下文、代码能力突出 |
| Claude 3 Opus | $15.00 | $75.00 | 200K | strong+ | 顶级推理能力，高成本 |
| Claude 3 Haiku | $0.25 | $1.25 | 200K | light | 低延迟低成本，轻量任务适配 |
| Gemini 1.5 Pro | $1.25 | $5.00 | 1M | middle | 超长上下文性价比高 |
| Gemini 1.5 Flash | $0.075 | $0.30 | 1M | light | 超低成本，长文档处理首选 |

**d-2. 国内主流模型（元/千 Token，同档位约为海外的 1/3~1/2）**

| 档位 | 代表模型 | 输入单价 | 输出单价 |
|---|---|---|---|
| 强模型 | 通义千问 Max / 文心一言 4.0 / DeepSeek V3 | 0.02 ~ 0.05 | 0.06 ~ 0.15 |
| 中等模型 | 通义千问 Plus / 文心 ERNIE Speed / DeepSeek Chat | 0.005 ~ 0.015 | 0.01 ~ 0.04 |
| 轻量模型 | 通义千问 Turbo / 文心 ERNIE Lite / DeepSeek Lite | 0.001 ~ 0.003 | 0.002 ~ 0.006 |

> 计费规则要点：①输入/输出分开计费（输出为输入 3~5 倍）；②函数调用/工具调用不额外收费；③流式与普通调用同价；④单次调用 Token 向上取整，高频小请求有小幅溢价。

**e. 代码场景 Token 消耗测算**（回填自 [10-supplement §3](../10-supplement/detail-mrd-gap-fill.md#3-代码场景-token-消耗量测算表)）

代码是 Agent 场景中 Token 消耗最高的场景，单任务全链路 Token 构成：固定开销 30%~60%（系统 Prompt + 召回历史代码 + 依赖定义）+ 动态开销 40%~70%（用户需求 + 当前代码上下文 + 报错栈 + 工具结果 + 输出代码）+ 累加开销（ReAct 多轮 + 反思修正）。

| 任务等级 | 场景示例 | 输入 Token | 输出 Token | 单轮总消耗 | 多轮调试总消耗 |
|---|---|---|---|---|---|
| 简单 | 单函数编写、单行 Bug 修复 | 3K ~ 5K | 1K ~ 2K | 4K ~ 7K | 8K ~ 15K（2 轮） |
| 中等 | 单模块开发、代码重构、单文件调试 | 10K ~ 20K | 4K ~ 8K | 14K ~ 28K | 40K ~ 80K（3 轮） |
| 复杂 | 多文件依赖调试、架构改造、全项目排错 | 30K ~ 90K | 10K ~ 30K | 40K ~ 120K | 120K ~ 300K（3-5 轮） |

补充换算参考：1KB 纯 Python 代码 ≈ 300~500 Token；128K 上下文窗口下代码任务建议预留 ≤30% 给历史代码召回（即 ≤38K Token）。管控手段参见 [07-code-retrieval §3.5](../07-code-retrieval/code-retrieval-system.md) Token 感知分级裁剪与 `agent_definition.max_steps` 熔断。

**f. BPE 分词规则与计费细节**（回填自 [10-supplement §4](../10-supplement/detail-mrd-gap-fill.md#4-bpe-分词规则与计费细节)）

**f-1. BPE 分词计数规则**

| 文本类型 | 换算关系 | 示例 |
|---|---|---|
| 英文 | 约 4 个字符 ≈ 1 Token | "hello world" ≈ 3 Token |
| 中文 | 约 1.3~2 个汉字 ≈ 1 Token | "你好世界" ≈ 2~3 Token |
| 代码 | 标识符按驼峰/下划线拆分 | `getUserOrder` ≈ 3~4 Token |

支撑 §c 中文密度修正的底层规则；`model-gateway.CountTokens` 对中文按 1.7 倍系数估算，精确计数依赖各供应商 tokenizer。

**f-2. Prompt 缓存折扣机制**

| 供应商 | 缓存命中计费 | 适用场景 | 降本幅度 |
|---|---|---|---|
| Anthropic | 命中部分按原价 10%（1 折） | 固定系统 Prompt、工具描述 | 输入成本降 90% |
| OpenAI | 命中部分按原价 50%（5 折） | 固定 Prompt 前缀 | 输入成本降 50% |

Agent 场景固定开销占 30%~60%，开启 `ChatRequest.enable_prompt_cache` 后整体可降本 30%+。配置位置：`model_route_rule.preferred_model` 对应供应商需支持缓存；`tool_registry.prompt_cache_key` 标记可缓存工具。

**f-3. 超长上下文加价**

| 供应商 | 阈值 | 超出部分加价 | 影响场景 |
|---|---|---|---|
| OpenAI | 128K | +50%~100% | 长文档分析、大代码库检索 |
| Anthropic | 200K | 部分模型阶梯加价 | 超长上下文任务 |

管控建议：`agent_definition.max_token` 硬上限触发 [04-memory §6](../04-memory/memory-system-design.md) 四级水位压缩；代码检索单次 Token 预算默认 8K（见 [07-code-retrieval §7.3](../07-code-retrieval/code-retrieval-system.md)）；长文档任务优先选 Gemini 1.5 Pro（1M 上下文，加价区间更宽）。

### 3.3 六大降本手段

| 降本手段 | 落地实现 | 预期收益 |
|---|---|---|
| 分层路由降本 | 按 §3.1 选型表，意图/汇总用 light，规划/终审用 strong | 节省 40%+ 模型成本 |
| 上下文压缩降本 | 见 [00-overview 文档 ADR-002](../00-overview/tech-stack-and-architecture.md#adr-002agent-运行时无状态化状态外置到-redis--mysql) + PRD 第二节四级水位线压缩 | 节省 30%+ 输入 Token |
| 缓存复用降本 | `model-gateway` 启用 `enable_prompt_cache`，`tool_registry.prompt_cache_key` 字段标记可缓存工具 | 命中后输入成本降 90% |
| 记忆精准召回降本 | `memory-service.Recall` 多路融合 + 相关性阈值过滤，避免召回冗余 | 节省 20%+ 召回 Token |
| 流程优化降本 | 模板化规划（`task_template`）+ 增量重规划优先 + 子任务并行批次 | 节省 30%+ 步数 |
| 提示词精简降本 | `agent_definition.system_prompt` 与 `core_constraints` 分离，工具 Schema 最小化 | 节省 15%+ 输入 Token |

**分层路由降本示例配置**（写入 `model_route_rule` 表）：

```sql
INSERT INTO model_route_rule (rule_id, scene, tier, preferred_model, fallback_models, priority, status) VALUES
('rl_intent_default', 'intent', 'light', 'qwen-turbo', '["deepseek-chat","gpt-3.5-turbo"]', 100, 1),
('rl_planning_default', 'planning', 'strong', 'gpt-4o', '["claude-3-5-sonnet","qwen-max"]', 100, 1),
('rl_tool_call_default', 'tool_call', 'middle', 'qwen-plus', '["gpt-4o-mini","claude-3-haiku"]', 100, 1),
('rl_summary_default', 'summary', 'middle', 'qwen-plus', '["gpt-4o-mini"]', 100, 1),
('rl_audit_default', 'audit', 'strong', 'claude-3-5-sonnet', '["gpt-4o"]', 100, 1);
```

### 3.4 成本熔断机制

依据 [数据库文档 §4.3 `tool_quota` 表](../01-database/database-schema-design.md#43-tool_quota-工具配额表) + [§5.3 `model_usage_log` 表](../01-database/database-schema-design.md#53-model_usage_log-模型调用计量表按月分表)，实现分级成本熔断：

| 熔断维度 | 配额表字段 | 阈值来源 | 触发动作 |
|---|---|---|---|
| 按工具 | `tool_quota.tool_id` + `daily_limit` / `cost_limit_cent` | 工具级配置 | 调用拒绝，返回 `QUOTA_EXCEEDED` |
| 按业务线（tenant） | `tool_quota.subject_type=tenant` | 租户级配置 | 降级到 light 模型 + 告警 |
| 按单任务 | `task_instance.cost_used_cent` vs `cost_limit_cent` | 任务创建时设定 | 任务终止，返回 `COST_BUDGET_EXCEEDED` |

**单任务成本熔断伪代码**：

```java
public void checkTaskBudget(TaskInstance task) {
    if (task.getCostUsedCent() >= task.getCostLimitCent()) {
        taskOrchestrator.cancelTask(task.getTaskId(), "COST_BUDGET_EXCEEDED");
        alertService.notify(P2, "TASK_BUDGET_EXHAUSTED: " + task.getTaskId());
    }
    // 预警阈值 80%
    if (task.getCostUsedCent() >= task.getCostLimitCent() * 0.8) {
        alertService.notify(P3, "TASK_BUDGET_WARNING: " + task.getTaskId());
    }
}
```

**按工具配额检查伪代码**：

```java
public boolean tryAcquire(ToolRegistry tool, TraceContext ctx) {
    ToolQuota q = quotaRepo.find("tenant", ctx.getTenantId(), tool.getToolId());
    if (q == null) return true;
    long newUsed = q.getDailyUsed() + 1;
    long newCost = q.getCostUsedCent() + tool.getAvgCostCent();
    if (newUsed > q.getDailyLimit() || newCost > q.getCostLimitCent()) {
        return false;
    }
    return quotaRepo.casIncrement(q.getId(), newUsed, newCost);
}
```

API 错误码：[02-api 文档 §0.5](../02-api/api-specification.md#5-错误码规范) `COST_BUDGET_EXCEEDED` / `RATE_LIMITED`。

---

# 第二篇 基础中间件集成方案

## 4. MySQL 集成

### 4.1 HikariCP 连接池配置

各微服务统一使用 HikariCP 连接池，配置写入 Nacos 共享配置 `datasource-common.yml`：

```yaml
spring:
  datasource:
    type: com.zaxxer.hikari.HikariDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      pool-name: ${spring.application.name}-pool
      minimum-idle: 5
      maximum-pool-size: 20
      idle-timeout: 30000
      max-lifetime: 1800000
      connection-timeout: 30000
      connection-test-query: SELECT 1
      leak-detection-threshold: 60000
      data-source-properties:
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
        useLocalSessionState: true
        rewriteBatchedStatements: true
        cacheResultSetMetadata: true
        cacheServerConfiguration: true
        elideSetAutoCommits: true
        maintainTimeStats: false
```

### 4.2 ShardingSphere 分库分表

依据 [数据库文档 §0.3 分库分表策略](../01-database/database-schema-design.md#03-分库分表策略shardingsphere)，`task_instance` 按 `tenant_id` 取模 16 库，`tool_call_log` / `model_usage_log` 按月分表。

`agent-task-orchestrator` 服务 ShardingSphere-JDBC 配置示例：

```yaml
spring:
  shardingsphere:
    mode:
      type: Standalone
      repository:
        type: JDBC
    datasource:
      names: ds0,ds1,ds2,ds3,ds4,ds5,ds6,ds7,ds8,ds9,ds10,ds11,ds12,ds13,ds14,ds15
      ds0:
        type: com.zaxxer.hikari.HikariDataSource
        jdbc-url: jdbc:mysql://mysql-master-0:3306/agent_task_0?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=true
        username: ${MYSQL_USER}
        password: ${MYSQL_PASSWORD}
        # ... 其余 HikariCP 配置同 §4.1
      ds1:
        type: com.zaxxer.hikari.HikariDataSource
        jdbc-url: jdbc:mysql://mysql-master-1:3306/agent_task_1?...
        # ... 同 ds0
      # ... ds2 ~ ds15 同理
    rules:
      sharding:
        tables:
          task_instance:
            actual-data-nodes: ds$->{0..15}.task_instance
            database-strategy:
              standard:
                sharding-column: tenant_id
                sharding-algorithm-name: tenant-mod
            key-generate-strategy:
              column: id
              key-generator-name: snowflake
          task_step_log:
            actual-data-nodes: ds$->{0..15}.task_step_log
            database-strategy:
              standard:
                sharding-column: task_id
                sharding-algorithm-name: task-mod
          tool_call_log:
            actual-data-nodes: ds$->{0..15}.tool_call_log_$->{202601..202612}
            database-strategy:
              standard:
                sharding-column: task_id
                sharding-algorithm-name: task-mod
            table-strategy:
              standard:
                sharding-column: created_at
                sharding-algorithm-name: monthly-interval
          model_usage_log:
            actual-data-nodes: ds$->{0..15}.model_usage_log_$->{202601..202612}
            table-strategy:
              standard:
                sharding-column: created_at
                sharding-algorithm-name: monthly-interval
        sharding-algorithms:
          tenant-mod:
            type: MOD
            props:
              sharding-count: 16
          task-mod:
            type: MOD
            props:
              sharding-count: 16
          monthly-interval:
            type: INTERVAL
            props:
              datetime-pattern: yyyyMM
              datetime-lower: 202601
              datetime-upper: 202612
              sharding-suffix-pattern: yyyyMM
        key-generators:
          snowflake:
            type: SNOWFLAKE
            props:
              worker-id: ${HOSTNAME_HASH}
        binding-tables:
          - task_instance,task_step_log
        broadcast-tables:
          - task_template
    props:
      sql-show: false
```

### 4.3 读写分离

主从读写分离在 ShardingSphere 中通过 `readwrite-splitting` 规则启用：

```yaml
spring:
  shardingsphere:
    rules:
      readwrite-splitting:
        data-sources:
          ds0_rw:
            write-data-source-name: ds0
            read-data-source-names: ds0_read1,ds0_read2
            load-balancer-name: round-robin
          # ... ds1 ~ ds15 同理
        load-balancers:
          round-robin:
            type: ROUND_ROBIN
```

**读写分离策略**：
- 强制走主库场景：写操作、事务内读、`task_instance` 状态机读、配额扣减校验
- 走从库场景：历史查询、统计聚合、审计查询
- 通过 HintManager 强制主库：

```java
@MasterRoute
public TaskInstance queryForUpdate(String taskId) {
    return taskRepo.findByTaskId(taskId);
}
```

### 4.4 MyBatis-Plus 集成

统一使用 MyBatis-Plus 3.5.x 作为 ORM，配置：

```yaml
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  type-aliases-package: com.agentplatform.*.entity
  global-config:
    db-config:
      id-type: assign_id
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
      table-underline: true
    banner: false
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: false
    default-enum-type-handler: com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler
```

雪花 ID worker 通过环境变量 `HOSTNAME_HASH` 注入避免冲突。

---

## 5. Milvus 集成

### 5.1 Collection 初始化脚本

依据 [数据库文档 §10.1 Collection 规划](../01-database/database-schema-design.md#101-collection-规划)，编写初始化脚本 `infra/sql/10-milvus-collections.json`：

```json
[
  {
    "collection_name": "mem_episodic",
    "description": "情景记忆向量库",
    "dimension": 1024,
    "index_type": "HNSW",
    "metric_type": "IP",
    "index_params": {"M": 16, "efConstruction": 256},
    "partition_key_field": "domain",
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
    ]
  },
  {
    "collection_name": "mem_semantic",
    "description": "语义记忆向量库",
    "dimension": 1024,
    "index_type": "HNSW",
    "metric_type": "IP",
    "index_params": {"M": 16, "efConstruction": 256},
    "partition_key_field": "domain",
    "fields": "same_as_mem_episodic"
  },
  {
    "collection_name": "mem_procedural",
    "description": "流程记忆向量库",
    "dimension": 1024,
    "index_type": "HNSW",
    "metric_type": "IP",
    "index_params": {"M": 16, "efConstruction": 256},
    "partition_key_field": "domain"
  },
  {
    "collection_name": "kb_chunks_default",
    "description": "知识切片向量库（默认）",
    "dimension": 1024,
    "index_type": "HNSW",
    "metric_type": "IP",
    "index_params": {"M": 16, "efConstruction": 256},
    "partition_key_field": "doc_id",
    "fields": [
      {"name": "vector_id", "type": "VarChar", "max_length": 64, "is_primary": true},
      {"name": "embedding", "type": "FloatVector", "dim": 1024},
      {"name": "tenant_id", "type": "Int64"},
      {"name": "kb_id", "type": "VarChar", "max_length": 32},
      {"name": "doc_id", "type": "VarChar", "max_length": 32},
      {"name": "chunk_id", "type": "VarChar", "max_length": 32},
      {"name": "content", "type": "VarChar", "max_length": 8192},
      {"name": "quality_score", "type": "Float"}
    ]
  },
  {
    "collection_name": "code_snippet",
    "description": "代码片段向量库",
    "dimension": 768,
    "index_type": "HNSW",
    "metric_type": "IP",
    "index_params": {"M": 32, "efConstruction": 512},
    "partition_key_field": "language",
    "fields": [
      {"name": "vector_id", "type": "VarChar", "max_length": 64, "is_primary": true},
      {"name": "embedding", "type": "FloatVector", "dim": 768},
      {"name": "tenant_id", "type": "Int64"},
      {"name": "project_id", "type": "VarChar", "max_length": 32},
      {"name": "language", "type": "VarChar", "max_length": 32},
      {"name": "file_path", "type": "VarChar", "max_length": 512},
      {"name": "symbol_type", "type": "VarChar", "max_length": 32},
      {"name": "symbol_name", "type": "VarChar", "max_length": 128},
      {"name": "content", "type": "VarChar", "max_length": 16384}
    ]
  },
  {
    "collection_name": "tool_index",
    "description": "工具语义索引向量库",
    "dimension": 1024,
    "index_type": "HNSW",
    "metric_type": "IP",
    "index_params": {"M": 16, "efConstruction": 256},
    "partition_key_field": "scene_tag",
    "fields": [
      {"name": "vector_id", "type": "VarChar", "max_length": 64, "is_primary": true},
      {"name": "embedding", "type": "FloatVector", "dim": 1024},
      {"name": "tenant_id", "type": "Int64"},
      {"name": "tool_id", "type": "VarChar", "max_length": 32},
      {"name": "name", "type": "VarChar", "max_length": 64},
      {"name": "scene_tag", "type": "VarChar", "max_length": 64}
    ]
  }
]
```

### 5.2 连接配置（Java SDK）

`memory-service` 通过 `milvus-sdk-java` 2.4.x 接入：

```yaml
milvus:
  host: milvus-cluster.agent-platform-prod
  port: 19530
  database: default
  username: ${MILVUS_USER}
  password: ${MILVUS_PASSWORD}
  pool:
    max-connect-count: 10
    idle-timeout: 30000
  connect-timeout: 5000
  keep-alive-time: 60000
```

```java
@Configuration
public class MilvusConfig {
    @Value("${milvus.host}")
    private String host;
    @Value("${milvus.port}")
    private int port;
    @Value("${milvus.username}")
    private String username;
    @Value("${milvus.password}")
    private String password;

    @Bean(destroyMethod = "close")
    public MilvusServiceClient milvusClient() {
        ConnectParam connectParam = ConnectParam.newBuilder()
            .withHost(host)
            .withPort(port)
            .withAuthorization(username, password)
            .withConnectTimeout(5, TimeUnit.SECONDS)
            .withIdleTimeout(30, TimeUnit.SECONDS)
            .build();
        return new MilvusServiceClient(connectParam);
    }
}
```

### 5.3 索引参数调优

| Collection | M | efConstruction | ef（搜索） | 调优理由 |
|---|---|---|---|---|
| `mem_episodic` / `mem_semantic` / `mem_procedural` | 16 | 256 | 64~128 | 记忆召回精度优先，但不需极高 |
| `kb_chunks_*` | 16 | 256 | 64 | 知识召回 + ACL 过滤后 top_k=20 |
| `code_snippet` | 32 | 512 | 128 | 代码符号召回精度要求高 |
| `tool_index` | 16 | 256 | 64 | 工具数量有限，召回精度足够 |

**搜索参数调优建议**：
- 召回阶段 `top_k` 适当放大（如目标 5，召回 20），重排后再裁剪
- `ef` 搜索参数动态调整：长期深度召回 ef=128，短期高频召回 ef=64
- `metric_type=IP` 配合归一化后的向量使用（cosine 相似度等价于 IP）

召回参数基线见 [数据库文档 §10.3](../01-database/database-schema-design.md#103-召回参数基线)。

---

## 6. Redis 集成

### 6.1 Cluster 模式配置

Redis 7.2 Cluster 模式，6 主 6 从，分片槽位默认 16384：

```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - redis-0.redis.agent-platform-prod:6379
          - redis-1.redis.agent-platform-prod:6379
          - redis-2.redis.agent-platform-prod:6379
          - redis-3.redis.agent-platform-prod:6379
          - redis-4.redis.agent-platform-prod:6379
          - redis-5.redis.agent-platform-prod:6379
        max-redirects: 3
        topology-refresh-period: 30s
      password: ${REDIS_PASSWORD}
      timeout: 3s
      lettuce:
        pool:
          max-active: 50
          max-idle: 20
          min-idle: 5
          max-wait: 2000ms
        shutdown-timeout: 100ms
        cluster:
          refresh:
            adaptive: true
            period: 30s
```

### 6.2 短期记忆 Key TTL 策略

依据 [数据库文档 §3.4 Redis 短期记忆 Key 设计](../01-database/database-schema-design.md#34-redis-短期记忆-key-设计)：

| Key 模式 | 类型 | TTL | 用途 | 过期处理 |
|---|---|---|---|---|
| `sm:{sessionId}:ctx` | Hash | 2h | 当前会话上下文 | 过期前 5 分钟触发持久化到 MySQL |
| `sm:{sessionId}:steps` | List | 2h | 本轮推理步骤栈 | 同上 |
| `sm:{sessionId}:token_water` | String | 2h | 当前 Token 水位百分比 | 同上 |
| `sm:{sessionId}:draft` | String | 30min | 瞬时记忆草稿 | 自动丢弃 |
| `runtime:{agentInstanceId}:state` | Hash | 30min | Agent 运行时断点状态 | 过期触发崩溃恢复流程 |
| `lock:tool:{toolId}` | String | 30s | 工具分布式锁 | 自动释放 |
| `rate:tenant:{tenantId}:chat` | String | 1s | 租户级 QPS 计数 | 自动重置 |

**Redisson Key 过期监听**（用于持久化触发）：

```java
@RedisListener(channel = "__keyevent@0__:expired")
public void onKeyExpired(String key) {
    if (key.startsWith("sm:") && key.endsWith(":ctx")) {
        String sessionId = extractSessionId(key);
        sessionService.persistToMySQL(sessionId);
    }
}
```

### 6.3 Redisson 分布式锁

工具调用、Agent 实例创建、配额扣减等场景使用 Redisson 分布式锁：

```yaml
redisson:
  config: |
    clusterServersConfig:
      nodeAddresses:
        - redis://redis-0.redis.agent-platform-prod:6379
        - redis://redis-1.redis.agent-platform-prod:6379
        - redis://redis-2.redis.agent-platform-prod:6379
        - redis://redis-3.redis.agent-platform-prod:6379
        - redis://redis-4.redis.agent-platform-prod:6379
        - redis://redis-5.redis.agent-platform-prod:6379
      password: ${REDIS_PASSWORD}
      scanInterval: 2000
      readMode: SLAVE
      subscriptionMode: SLAVE
    threads: 16
    nettyThreads: 32
    codec: !<org.redisson.codec.JsonJacksonCodec> {}
```

```java
@Service
public class ToolLockService {
    @Autowired
    private RedissonClient redisson;

    public boolean tryAcquireToolLock(String toolId, String callId, long waitMs) {
        RLock lock = redisson.getLock("lock:tool:" + toolId + ":" + callId);
        try {
            return lock.tryLock(waitMs, 30_000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
```

**锁使用场景**：
- 工具调用防重入（同一 callId 禁止并发执行）
- Agent 实例创建（同一 sessionId 同时只允许一个 Agent 实例）
- 配额扣减（原子操作，避免超卖）

---

## 7. RocketMQ 集成

### 7.1 Topic 与消费组规划

依据 [02-api 文档 §11 Topic 清单](../02-api/api-specification.md#11-异步事件规范rocketmq-topics) 扩展：

| Topic | 类型 | 生产者 | 消费组 | 消费者 | 用途 |
|---|---|---|---|---|---|
| `task.subtask.execute` | Transaction | task-orchestrator | CG_RUNTIME_EXECUTE | agent-runtime | 分发子任务执行 |
| `task.subtask.done` | Normal | agent-runtime | CG_ORCH_DONE | task-orchestrator | 子任务完成上报 |
| `task.state.change` | Normal | task-orchestrator | CG_SESSION_STATE / CG_OBS_STATE | session-service / observability | 任务状态变更广播 |
| `tool.call.audit` | Normal | tool-engine | CG_RISK_AUDIT | risk-control | 工具调用审计 |
| `memory.write` | Normal | agent-runtime | CG_MEM_WRITE | memory-service | 记忆写入请求 |
| `quality.badcase` | Normal | quality-service | CG_OBS_BADCASE | observability | Badcase 事件 |
| `governance.drift.alert` | Normal | quality-service | CG_OBS_DRIFT / CG_RISK_DRIFT | observability / risk-control | 漂移告警 |
| `memory.distill.request` | Normal | XXL-Job | CG_MEM_DISTILL | memory-service | 记忆蒸馏触发 |

**Topic 创建规范**：
- 所有 Topic 在 Nacos 配置中声明，避免硬编码
- 消费组命名：`CG_{服务缩写}_{动作}`
- 死信队列：`DLQ.{topic}`，由 observability 监听并告警

### 7.2 事务消息配置

`task.subtask.execute` 使用事务消息确保子任务分发与 DB 状态变更一致：

```java
@Service
@RocketMQTransactionListener
public class SubtaskExecuteTransactionListener implements RocketMQLocalTransactionListener {

    @Autowired
    private TaskService taskService;

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            SubtaskExecuteMsg payload = parse(msg);
            // 本地事务：更新 task_step_log 状态为 DISPATCHED
            taskService.markDispatched(payload.getTaskId(), payload.getSubtaskId());
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("local tx failed", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(MessageExt msg) {
        // 回查：检查 task_step_log 中是否已记录
        SubtaskExecuteMsg payload = parse(msg);
        StepLog log = taskService.findStepLog(payload.getTaskId(), payload.getSubtaskId());
        if (log != null && "DISPATCHED".equals(log.getStatus())) {
            return RocketMQLocalTransactionState.COMMIT;
        }
        return RocketMQLocalTransactionState.ROLLBACK;
    }
}
```

```yaml
rocketmq:
  name-server: rocketmq-namesrv.agent-platform-prod:9876
  producer:
    group: PG_TASK_ORCHESTRATOR
    send-message-timeout: 5000
    retry-times-when-send-failed: 2
    retry-times-when-send-async-failed: 3
    max-message-size: 4194304  # 4MB
    transaction-message: true
  consumer:
    group: CG_RUNTIME_EXECUTE
    consume-mode: CONCURRENTLY
    consume-thread-max: 64
    pull-batch-size: 32
    consume-timeout: 30m
```

### 7.3 消费者幂等

依据 [02-api 文档 §11.2](../02-api/api-specification.md#112-幂等与重试)，所有消费者落 `event_consume_log` 表去重：

```sql
CREATE TABLE event_consume_log (
  id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  event_id VARCHAR(64) NOT NULL,
  consumer_group VARCHAR(64) NOT NULL,
  topic VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,  -- PROCESSING | SUCCESS | FAILED
  consumed_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_event_consumer (event_id, consumer_group)
) ENGINE=InnoDB;
```

```java
@RocketMQMessageListener(topic = "task.subtask.execute", consumerGroup = "CG_RUNTIME_EXECUTE")
public class SubtaskExecuteConsumer implements RocketMQListener<SubtaskExecuteMsg> {

    @Autowired
    private EventConsumeLogRepo consumeLogRepo;
    @Autowired
    private AgentRuntimeService runtimeService;

    @Override
    @Transactional
    public void onMessage(SubtaskExecuteMsg msg) {
        // 幂等校验
        if (consumeLogRepo.existsByEventId(msg.getEventId(), "CG_RUNTIME_EXECUTE")) {
            log.info("duplicate event ignored: {}", msg.getEventId());
            return;
        }
        consumeLogRepo.save(new EventConsumeLog(msg.getEventId(), "CG_RUNTIME_EXECUTE",
            "task.subtask.execute", "PROCESSING"));
        try {
            runtimeService.executeSubtask(msg);
            consumeLogRepo.markSuccess(msg.getEventId(), "CG_RUNTIME_EXECUTE");
        } catch (Exception e) {
            consumeLogRepo.markFailed(msg.getEventId(), "CG_RUNTIME_EXECUTE");
            throw e;  // 触发 RocketMQ 重试
        }
    }
}
```

重试策略遵循 RocketMQ 默认：1s、5s、10s、30s、1m、5m、10m...最多 16 次，16 次后入死信队列触发告警。

---

## 8. Elasticsearch 集成

### 8.1 索引 Mapping

ES 8.13 用于代码标识符分词、知识库全文检索、审计日志检索。

**a. 代码标识符索引 `code_identifier`**：

```json
PUT /code_identifier
{
  "settings": {
    "index": {
      "number_of_shards": 3,
      "number_of_replicas": 1,
      "analysis": {
        "tokenizer": {
          "code_identifier_tokenizer": {
            "type": "pattern",
            "pattern": "([_A-Z]+|[a-z]+|[0-9]+)"
          }
        },
        "analyzer": {
          "code_identifier_analyzer": {
            "type": "custom",
            "tokenizer": "code_identifier_tokenizer",
            "filter": ["lowercase", "edge_ngram_filter"]
          },
          "code_search_analyzer": {
            "type": "custom",
            "tokenizer": "code_identifier_tokenizer",
            "filter": ["lowercase"]
          }
        },
        "filter": {
          "edge_ngram_filter": {
            "type": "edge_ngram",
            "min_gram": 2,
            "max_gram": 20
          }
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "project_id": {"type": "keyword"},
      "symbol_type": {"type": "keyword"},
      "symbol_name": {
        "type": "text",
        "analyzer": "code_identifier_analyzer",
        "search_analyzer": "code_search_analyzer",
        "fields": {
          "keyword": {"type": "keyword"}
        }
      },
      "file_path": {"type": "keyword"},
      "language": {"type": "keyword"},
      "signature": {"type": "text", "analyzer": "standard"},
      "start_line": {"type": "integer"},
      "end_line": {"type": "integer"},
      "vector_id": {"type": "keyword"},
      "updated_at": {"type": "date"}
    }
  }
}
```

**b. 知识库全文索引 `kb_fulltext`**：

```json
PUT /kb_fulltext
{
  "settings": {
    "index": {
      "number_of_shards": 3,
      "number_of_replicas": 1,
      "analysis": {
        "analyzer": {
          "ik_smart_analyzer": {
            "type": "custom",
            "tokenizer": "ik_smart",
            "filter": ["lowercase", "asciifolding"]
          },
          "ik_max_word_analyzer": {
            "type": "custom",
            "tokenizer": "ik_max_word",
            "filter": ["lowercase", "asciifolding"]
          }
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "kb_id": {"type": "keyword"},
      "doc_id": {"type": "keyword"},
      "chunk_id": {"type": "keyword"},
      "tenant_id": {"type": "long"},
      "title": {
        "type": "text",
        "analyzer": "ik_max_word_analyzer",
        "search_analyzer": "ik_smart_analyzer",
        "fields": {"keyword": {"type": "keyword"}}
      },
      "content": {
        "type": "text",
        "analyzer": "ik_max_word_analyzer",
        "search_analyzer": "ik_smart_analyzer"
      },
      "metadata": {
        "type": "object",
        "properties": {
          "page": {"type": "integer"},
          "section": {"type": "keyword"}
        }
      },
      "acl_roles": {"type": "keyword"},
      "vector_id": {"type": "keyword"},
      "updated_at": {"type": "date"}
    }
  }
}
```

**c. 审计日志索引 `audit_log`**（按月滚动）：

```json
PUT /audit_log-2026.06
{
  "settings": {
    "index.number_of_shards": 1,
    "index.number_of_replicas": 1
  },
  "mappings": {
    "properties": {
      "audit_id": {"type": "keyword"},
      "subject_type": {"type": "keyword"},
      "subject_id": {"type": "keyword"},
      "action": {"type": "keyword"},
      "resource_type": {"type": "keyword"},
      "resource_id": {"type": "keyword"},
      "risk_level": {"type": "byte"},
      "result": {"type": "keyword"},
      "detail": {"type": "object", "enabled": true},
      "trace_id": {"type": "keyword"},
      "created_at": {"type": "date"}
    }
  }
}
```

通过 ILM（Index Lifecycle Management）实现自动滚动：

```json
PUT _ilm/policy/audit_log_policy
{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": {"max_age": "30d", "max_size": "50gb"}
        }
      },
      "warm": {
        "min_age": "90d",
        "actions": {"forcemerge": {"max_num_segments": 1}}
      },
      "delete": {
        "min_age": "730d",
        "actions": {"delete": {}}
      }
    }
  }
}
```

### 8.2 与 Milvus 协同

知识检索采用「双路召回 + 融合重排」：

```
用户查询
   ├── ES 全文检索（关键词/精确符号） → Top-20
   ├── Milvus 向量检索（语义相似） → Top-20
   └── 合并去重 → 重排序（语义40% + 关键词30% + 结构20% + 热度10%） → Top-5
```

实现伪代码：

```java
public List<KnowledgeChunk> hybridRetrieve(KnowledgeQuery query) {
    CompletableFuture<List<Chunk>> esFuture = CompletableFuture.supplyAsync(() ->
        esClient.searchByFulltext(query));
    CompletableFuture<List<Chunk>> milvusFuture = CompletableFuture.supplyAsync(() ->
        milvusClient.searchByVector(query));
    List<Chunk> merged = mergeAndDedupe(esFuture.join(), milvusFuture.join());
    return reranker.rerank(merged, query, 5);  // 融合重排，输出 Top-5
}
```

---

## 9. Neo4j 集成

### 9.1 连接配置

Neo4j 5.18 Community，通过 `spring-data-neo4j` 6.x 接入：

```yaml
spring:
  neo4j:
    uri: bolt://neo4j-cluster.agent-platform-prod:7687
    authentication:
      username: ${NEO4J_USER}
      password: ${NEO4J_PASSWORD}
    pool:
      connection-liveness-check-timeout: 30s
      max-connection-pool-size: 50
      connection-acquisition-timeout: 60s
```

```java
@Configuration
@EnableNeo4jRepositories(basePackages = "com.agentplatform.code.repo")
public class Neo4jConfig {
    @Bean
    public Driver driver(Neo4jProperties props) {
        return GraphDatabase.driver(props.getUri(),
            AuthTokens.basic(props.getAuthentication().getUsername(),
                             props.getAuthentication().getPassword()),
            Config.builder()
                .withMaxConnectionPoolSize(50)
                .withConnectionAcquisitionTimeout(60, TimeUnit.SECONDS)
                .build());
    }
}
```

### 9.2 约束与索引初始化

依据 [数据库文档 §11.3 索引](../01-database/database-schema-design.md#113-索引)，编写初始化脚本 `infra/sql/11-neo4j-constraints.cypher`：

```cypher
// 唯一性约束
CREATE CONSTRAINT project_id_unique IF NOT EXISTS FOR (n:Project) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT module_id_unique IF NOT EXISTS FOR (n:Module) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT file_id_unique IF NOT EXISTS FOR (n:File) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT class_id_unique IF NOT EXISTS FOR (n:Class) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT function_id_unique IF NOT EXISTS FOR (n:Function) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT interface_id_unique IF NOT EXISTS FOR (n:Interface) REQUIRE n.id IS UNIQUE;
CREATE CONSTRAINT dependency_id_unique IF NOT EXISTS FOR (n:Dependency) REQUIRE n.id IS UNIQUE;

// 查询性能索引
CREATE INDEX project_name IF NOT EXISTS FOR (n:Project) ON (n.name);
CREATE INDEX function_name IF NOT EXISTS FOR (n:Function) ON (n.name);
CREATE INDEX class_name IF NOT EXISTS FOR (n:Class) ON (n.name);
CREATE INDEX file_path IF NOT EXISTS FOR (n:File) ON (n.path);

// 全文索引（支持代码符号模糊搜索）
CREATE FULLTEXT INDEX symbol_fulltext IF NOT EXISTS
FOR (n:Class|Function|Interface) ON EACH [n.name, n.signature];

// 业务字段索引（按 project 维度查询）
CREATE INDEX module_project IF NOT EXISTS FOR (n:Module) ON (n.project_id);
```

调用链追溯查询示例：

```cypher
// 查询函数 A 的所有调用方（向上追溯）
MATCH (caller:Function)-[:CALLS*1..5]->(target:Function {name: $functionName})
RETURN caller.name, caller.file_path, caller.start_line, length(p) AS depth
LIMIT 50;
```

---

## 10. MinIO 集成

### 10.1 Bucket 规划

| Bucket | 用途 | 访问权限 | 生命周期 |
|---|---|---|---|
| `agent-platform-attachments` | 用户上传附件 | 私有 | 90 天后归档 |
| `agent-platform-documents` | 知识库原始文档 | 私有 | 永久 |
| `agent-platform-archives` | 归档数据（会话、日志） | 私有 | 2 年后删除 |
| `agent-platform-snapshots` | Agent 版本快照、配置备份 | 私有 | 永久 |
| `agent-platform-temp` | 临时文件（中转、沙箱产物） | 私有 | 7 天自动删除 |

Bucket 创建与生命周期策略脚本：

```bash
mc alias set agentplatform http://minio.agent-platform-prod:9000 ${MINIO_ROOT_USER} ${MINIO_ROOT_PASSWORD}

for bucket in attachments documents archives snapshots temp; do
  mc mb agentplatform/agent-platform-${bucket} --ignore-existing
done

# 临时桶 7 天自动删除
mc ilm rule add agentplatform/agent-platform-temp --expire-days 7
# 归档桶 2 年后删除
mc ilm rule add agentplatform/agent-platform-archives --expire-days 730
```

### 10.2 S3 SDK 配置

通过 AWS S3 SDK 兼容接入：

```yaml
s3:
  endpoint: http://minio.agent-platform-prod:9000
  region: us-east-1
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  path-style-access: true
  bucket-prefix: agent-platform
  client:
    max-connections: 50
    connection-timeout: 5000
    socket-timeout: 30000
```

```java
@Configuration
public class S3Config {
    @Value("${s3.endpoint}")
    private String endpoint;
    @Value("${s3.region}")
    private String region;
    @Value("${s3.access-key}")
    private String accessKey;
    @Value("${s3.secret-key}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .serviceConfiguration(s -> s.pathStyleAccessEnabled(true))
            .overrideConfiguration(o -> o.retryPolicy(r -> r.numRetries(3)))
            .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .build();
    }
}
```

**预签名 URL 上传**（用于附件上传，避免直接暴露 AK/SK）：

```java
public String generateUploadUrl(String bucket, String objectKey, Duration expire) {
    PutObjectRequest req = PutObjectRequest.builder()
        .bucket(bucket).key(objectKey).build();
    PutObjectPresignRequest presignReq = PutObjectPresignRequest.builder()
        .signatureDuration(expire)
        .putObjectRequest(req).build();
    return s3Presigner.presignPutObject(presignReq).url().toString();
}
```

---

## 11. 可观测组件集成

### 11.1 SkyWalking Agent 配置

Java Agent 字节码增强，自动埋点 gRPC / HTTP / RocketMQ：

```yaml
# infra/skywalking/agent.config.serviceName=agent-platform-${spring.application.name}
# infra/skywalking/application.yml 示例配置
agent.service_name: agent-platform-${spring.application.name}
agent.namespace: agent-platform
collector.backend_service: skywalking-oap.agent-platform-prod:11800
agent.sample_n_per_3_secs: 5000  # 5000 trace/3s
agent.ignore_suffix: .jpg,.png,.css,.js,.html
agent.cache_size: 10000
logging.level: INFO
logging.file_name: /var/log/agent-platform/skywalking.log

# 自定义 TraceID 透传
plugin.mount: agent-platform-custom-plugin
plugin.toolites_trace_context: org.apache.skywalking.apm.plugin.trace.SimpleDateFormatCallback
```

应用启动参数：

```dockerfile
ENV JAVA_OPTS="$JAVA_OPTS -javaagent:/skywalking/skywalking-agent.jar"
```

**自定义 TraceID 透传**（与 `X-Trace-Id` Header 互认）：

```java
@Component
public class CustomTraceContext implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method,
                              Object[] allArguments, Class<?>[] argumentsTypes,
                              MethodInterceptResult result) {
        // 从 HTTP/gRPC Header 提取 X-Trace-Id，与 SkyWalking TraceID 绑定
        HttpServletRequest req = (HttpServletRequest) allArguments[0];
        String traceId = req.getHeader("X-Trace-Id");
        if (traceId != null) {
            MDC.put("traceId", traceId);
            ContextManager.getGlobalTraceId();
        }
    }
}
```

### 11.2 Prometheus 指标暴露

每个服务暴露 `/actuator/prometheus` 端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info,metrics
      base-path: /actuator
  endpoint:
    prometheus:
      enabled: true
    health:
      show-details: when-authorized
  metrics:
    tags:
      application: ${spring.application.name}
      namespace: agent-platform
    distribution:
      percentiles-histogram:
        http.server.requests: true
        grpc.server.requests: true
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99
  prometheus:
    metrics:
      export:
        enabled: true
        step: 30s
```

**业务指标自定义埋点**（Micrometer + Counter）：

```java
@Component
public class BusinessMetrics {
    private final Counter taskSubmitted;
    private final Counter taskSucceeded;
    private final Counter hallucinationDetected;
    private final Timer taskDuration;

    public BusinessMetrics(MeterRegistry registry) {
        this.taskSubmitted = Counter.builder("agent.task.submitted")
            .description("Tasks submitted")
            .tag("status", "submitted")
            .register(registry);
        this.taskSucceeded = Counter.builder("agent.task.completed")
            .description("Tasks completed successfully")
            .tag("status", "success")
            .register(registry);
        this.hallucinationDetected = Counter.builder("agent.hallucination.detected")
            .description("Hallucination cases detected by L4")
            .tag("severity", "high")
            .register(registry);
        this.taskDuration = Timer.builder("agent.task.duration")
            .description("Task execution duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    public void recordTaskSubmit(String tenantId) {
        taskSubmitted.increment();
    }
    public void recordTaskSuccess(long durationMs) {
        taskSucceeded.increment();
        taskDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }
    public void recordHallucination() {
        hallucinationDetected.increment();
    }
}
```

Grafana 预置 Agent 平台 Dashboard，覆盖：任务成功率、幻觉率、工具调用成功率、模型成本、Token 消耗、漂移趋势、配额使用率。

### 11.3 Loki / ELK 日志采集

Logback 输出结构化 JSON，TraceID 注入 MDC：

```xml
<!-- logback-spring.xml -->
<configuration>
    <springProperty scope="context" name="appName" source="spring.application.name"/>

    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>tenantId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>taskId</includeMdcKeyName>
            <customFields>{"app":"${appName}","env":"${SPRING_PROFILES_ACTIVE}"}</customFields>
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <message>message</message>
                <thread>thread</thread>
                <level>level</level>
                <logger>logger</logger>
            </fieldNames>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

**TraceID 注入 Filter**（确保每个请求都有 TraceID）：

```java
@Component
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                     FilterChain chain) throws ServletException, IOException {
        String traceId = req.getHeader("X-Trace-Id");
        if (traceId == null) traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId);
        resp.setHeader("X-Trace-Id", traceId);
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.clear();
        }
    }
}
```

**Loki Promtail 采集配置**：

```yaml
# /etc/promtail/promtail.yml
server:
  http_listen_port: 9080

positions:
  filename: /var/lib/promtail/positions.yaml

clients:
  - url: http://loki.agent-platform-prod:3100/loki/api/v1/push

scrape_configs:
  - job_name: agent-platform
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_namespace]
        target_label: namespace
      - source_labels: [__meta_kubernetes_pod_label_app]
        target_label: app
      - source_labels: [__meta_kubernetes_pod_name]
        target_label: pod
    pipeline_stages:
      - json:
          expressions:
            level: level
            traceId: traceId
            app: app
      - labels:
          level:
          app:
      - structured_metadata:
          traceId:
```

**日志查询示例**（Loki LogQL）：

```logql
{app="agent-runtime", namespace="agent-platform-prod"}
  | json
  | traceId="a1b2c3d4"
```

---

# 第三篇 配置文件与部署说明

## 12. 配置管理

### 12.1 Nacos 配置中心

**DataId / Group 规划**：

| 命名空间（namespace） | Group | DataId | 类型 | 说明 |
|---|---|---|---|---|
| `agent-platform-prod` | `COMMON_GROUP` | `datasource-common.yml` | yaml | 共享数据源配置 |
| `agent-platform-prod` | `COMMON_GROUP` | `redis-common.yml` | yaml | 共享 Redis 配置 |
| `agent-platform-prod` | `COMMON_GROUP` | `rocketmq-common.yml` | yaml | 共享 RocketMQ 配置 |
| `agent-platform-prod` | `COMMON_GROUP` | `observability-common.yml` | yaml | 共享可观测配置 |
| `agent-platform-prod` | `COMMON_GROUP` | `governance-rules.yml` | yaml | 治理规则（限流/熔断/漂移阈值） |
| `agent-platform-prod` | `SERVICE_GROUP` | `task-orchestrator.yml` | yaml | task-orchestrator 服务配置 |
| `agent-platform-prod` | `SERVICE_GROUP` | `task-orchestrator-{profile}.yml` | yaml | profile 差异化 |
| `agent-platform-prod` | `SERVICE_GROUP` | `memory-service.yml` | yaml | memory-service 服务配置 |
| `agent-platform-prod` | `SERVICE_GROUP` | `memory-service-{profile}.yml` | yaml | profile 差异化 |
| `agent-platform-prod` | `SERVICE_GROUP` | `${其他服务}.yml` | yaml | 其余 9 个服务 |

profile 命名：`dev` / `staging` / `prod`，对应 K8s 命名空间（见 §13.3）。

**bootstrap.yml 通用配置**（所有服务共用）：

```yaml
spring:
  application:
    name: task-orchestrator
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:nacos-cluster.agent-platform-prod:8848}
        namespace: agent-platform-${spring.profiles.active}
        group: SERVICE_GROUP
      config:
        server-addr: ${NACOS_ADDR:nacos-cluster.agent-platform-prod:8848}
        namespace: agent-platform-${spring.profiles.active}
        group: SERVICE_GROUP
        file-extension: yaml
        shared-configs:
          - data-id: datasource-common.yml
            group: COMMON_GROUP
            refresh: true
          - data-id: redis-common.yml
            group: COMMON_GROUP
            refresh: true
          - data-id: rocketmq-common.yml
            group: COMMON_GROUP
            refresh: true
          - data-id: observability-common.yml
            group: COMMON_GROUP
            refresh: true
          - data-id: governance-rules.yml
            group: COMMON_GROUP
            refresh: true
```

### 12.2 task-orchestrator 完整配置示例

`task-orchestrator-prod.yml`：

```yaml
server:
  port: 8084
  servlet:
    context-path: /
  compression:
    enabled: true
    mime-types: application/json

spring:
  main:
    allow-bean-definition-overriding: true
  application:
    name: task-orchestrator
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai
    default-property-inclusion: non-null

# ShardingSphere 分库分表（见 §4.2）
  shardingsphere:
    mode:
      type: Standalone
      repository:
        type: JDBC
    datasource:
      names: ds0,ds1,ds2,ds3,ds4,ds5,ds6,ds7,ds8,ds9,ds10,ds11,ds12,ds13,ds14,ds15
      ds0:
        type: com.zaxxer.hikari.HikariDataSource
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbc-url: jdbc:mysql://mysql-master-0:3306/agent_task_0?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=true&verifyServerCertificate=false
        username: ${MYSQL_USER}
        password: ${MYSQL_PASSWORD}
        hikari:
          minimum-idle: 5
          maximum-pool-size: 20
          idle-timeout: 30000
          max-lifetime: 1800000
          connection-timeout: 30000
          leak-detection-threshold: 60000
      # ds1 ~ ds15 同 ds0，仅 jdbc-url 中库名递增
    rules:
      sharding:
        tables:
          task_instance:
            actual-data-nodes: ds$->{0..15}.task_instance
            database-strategy:
              standard:
                sharding-column: tenant_id
                sharding-algorithm-name: tenant-mod
            key-generate-strategy:
              column: id
              key-generator-name: snowflake
          task_step_log:
            actual-data-nodes: ds$->{0..15}.task_step_log
            database-strategy:
              standard:
                sharding-column: task_id
                sharding-algorithm-name: task-mod
          task_state_change:
            actual-data-nodes: ds$->{0..15}.task_state_change
            database-strategy:
              standard:
                sharding-column: task_id
                sharding-algorithm-name: task-mod
        sharding-algorithms:
          tenant-mod:
            type: MOD
            props:
              sharding-count: 16
          task-mod:
            type: MOD
            props:
              sharding-count: 16
        key-generators:
          snowflake:
            type: SNOWFLAKE
            props:
              worker-id: ${HOSTNAME_HASH:1}
        binding-tables:
          - task_instance,task_step_log,task_state_change
        broadcast-tables:
          - task_template
      readwrite-splitting:
        data-sources:
          ds0_rw:
            write-data-source-name: ds0
            read-data-source-names: ds0_read1,ds0_read2
            load-balancer-name: round-robin
          # ... ds1 ~ ds15 同理
        load-balancers:
          round-robin:
            type: ROUND_ROBIN
    props:
      sql-show: false

# gRPC 服务
grpc:
  server:
    port: 9084
    max-inbound-message-size: 50MB
  client:
    planning-service:
      address: dns:///planning-service.agent-platform-prod:9086
    agent-runtime:
      address: dns:///agent-runtime.agent-platform-prod:9092
    memory-service:
      address: dns:///memory-service.agent-platform-prod:9088

# RocketMQ（事务消息）
rocketmq:
  name-server: rocketmq-namesrv.agent-platform-prod:9876
  producer:
    group: PG_TASK_ORCHESTRATOR
    send-message-timeout: 5000
    retry-times-when-send-failed: 2
    transaction-message: true

# 任务编排业务配置
task:
  orchestrator:
    max-replan-count: 3
    default-cost-limit-cent:
      L1: 50
      L2: 500
      L3: 5000
    scheduler:
      poll-interval-ms: 1000
      max-concurrent-tasks: 500
    dag:
      cycle-detection: tarjan
      parallel-batch-max-size: 10

# 治理规则（与 Nacos governance-rules.yml 共享）
governance:
  hallucination:
    enabled: true
    layers: [L1, L2, L3, L4, L5, L6]
  drift:
    detection-enabled: true
    alert-threshold:
      task-level: 0.3
      system-level: 0.5
  cost:
    circuit-breaker:
      task-warn-ratio: 0.8
      task-block-ratio: 1.0

# 模型网关路由
model:
  gateway:
    address: dns:///model-gateway.agent-platform-prod:9094
    default-timeout-ms: 30000
```

### 12.3 memory-service 完整配置示例

`memory-service-prod.yml`：

```yaml
server:
  port: 8088

spring:
  application:
    name: memory-service
  datasource:
    type: com.zaxxer.hikari.HikariDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://mysql-master-0:3306/agent_memory?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=true
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}
    hikari:
      minimum-idle: 5
      maximum-pool-size: 30  # 记忆服务读写频繁
      idle-timeout: 30000

# gRPC
grpc:
  server:
    port: 9088
  client:
    model-gateway:
      address: dns:///model-gateway.agent-platform-prod:9094

# Milvus
milvus:
  host: milvus-cluster.agent-platform-prod
  port: 19530
  username: ${MILVUS_USER}
  password: ${MILVUS_PASSWORD}
  pool:
    max-connect-count: 20

# 记忆业务配置
memory:
  short-term:
    session-ttl-minutes: 120
    draft-ttl-minutes: 30
    max-recent-turns: 10
    token-watermark:
      safe: 0.7
      warn: 0.85
      critical: 0.95
  long-term:
    recall:
      top-k: 50
      relevance-threshold: 0.6
      strategies: [vector, keyword, time, tag]
      rerank-top-n: 10
    write:
      similarity-dedupe-threshold: 0.95
      importance-default: 0.5
    distill:
      cron: "0 0 2 * * ?"  # 每日 2:00 触发
      weekly-check-cron: "0 0 3 ? * MON"  # 每周一 3:00 全量校验
      batch-size: 1000
  collections:
    episodic: mem_episodic
    semantic: mem_semantic
    procedural: mem_procedural

# 嵌入模型
embedding:
  model: bge-large-zh-v1.5
  dim: 1024
  batch-size: 32
  timeout-ms: 5000

# RocketMQ（消费 memory.write）
rocketmq:
  name-server: rocketmq-namesrv.agent-platform-prod:9876
  consumer:
    group: CG_MEM_WRITE
    consume-mode: CONCURRENTLY
    consume-thread-max: 32

# XXL-Job（定时蒸馏任务）
xxl:
  job:
    admin-addresses: http://xxl-job-admin.agent-platform-prod:8080/xxl-job-admin
    access-token: ${XXL_JOB_TOKEN}
    executor:
      app-name: memory-service
      port: 9999
      log-path: /var/log/agent-platform/xxl-job/memory
```

### 12.4 敏感配置 Vault 接入

依据 [数据库文档 §5.1 `model_provider.api_key_ref`](../01-database/database-schema-design.md#51-model_provider-模型供应商表)「密钥引用（Vault 路径，禁止明文）」要求，所有密钥统一存 HashiCorp Vault，应用通过 spring-cloud-vault 读取：

```yaml
spring:
  cloud:
    vault:
      uri: https://vault.agent-platform-prod:8200
      authentication: kubernetes
      kubernetes:
        role: agent-platform-${spring.application.name}
        kubernetes-path: kubernetes
      ssl:
        trust-store: file:/etc/vault/truststore.jks
        trust-store-password: ${VAULT_TS_PASSWORD}
      kv:
        backend: agent-platform
        application-name: ${spring.application.name}
```

**密钥路径规划**：

| Vault Path | 用途 | 示例值 |
|---|---|---|
| `secret/data/agent-platform/common/mysql` | MySQL 凭据 | `MYSQL_USER` / `MYSQL_PASSWORD` |
| `secret/data/agent-platform/common/redis` | Redis 密码 | `REDIS_PASSWORD` |
| `secret/data/agent-platform/common/milvus` | Milvus 凭据 | `MILVUS_USER` / `MILVUS_PASSWORD` |
| `secret/data/agent-platform/common/neo4j` | Neo4j 凭据 | - |
| `secret/data/agent-platform/common/minio` | MinIO AK/SK | - |
| `secret/data/agent-platform/common/vault` | Vault 自身访问凭据 | - |
| `secret/data/agent-platform/model/openai` | OpenAI API Key | `api_key=sk-xxx` |
| `secret/data/agent-platform/model/anthropic` | Claude API Key | - |
| `secret/data/agent-platform/model/qwen` | 通义千问 API Key | - |
| `secret/data/agent-platform/model/deepseek` | DeepSeek API Key | - |
| `secret/data/agent-platform/service/jwt` | JWT 签名密钥 | - |

`model_provider.api_key_ref` 字段示例值：`secret/data/agent-platform/model/openai#api_key`，`model-gateway` 在初始化 Adapter 时按此路径从 Vault 读取：

```java
public String resolveApiKey(String apiKeyRef) {
    String[] parts = apiKeyRef.split("#");
    String path = parts[0];
    String key = parts.length > 1 ? parts[1] : "api_key";
    VaultResponse resp = vaultTemplate.read(path);
    return (String) resp.getData().get(key);
}
```

**禁止事项**：
- 禁止任何密钥硬编码进 application.yml
- 禁止密钥写入日志（Logback 中配置脱敏 Pattern）
- 禁止密钥通过环境变量明文传递（K8s Secret 仍需 Vault 注入，不直存明文）

---

## 13. 容器化部署

### 13.1 Dockerfile 模板

多阶段构建，maven 编译 → jre 运行：

```dockerfile
# 阶段一：Maven 编译
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom，利用 Docker 缓存加速依赖下载
COPY pom.xml .
COPY agent-common/pom.xml agent-common/
COPY agent-proto/pom.xml agent-proto/
COPY agent-task-orchestrator/pom.xml agent-task-orchestrator/
# ... 其他模块 pom

RUN mvn dependency:go-offline -B

# 复制源码
COPY agent-common/src agent-common/src
COPY agent-proto/src agent-proto/src
COPY agent-task-orchestrator/src agent-task-orchestrator/src

# 编译指定模块及其依赖
RUN mvn package -pl agent-task-orchestrator -am -DskipTests -B

# 阶段二：运行时镜像
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="agent-platform-team"
LABEL service="task-orchestrator"

# 安装 skywalking-agent
ARG SKYWALKING_VERSION=9.7.0
RUN apt-get update && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/* \
    && curl -L https://archive.apache.org/dist/skywalking/java-agent/${SKYWALKING_VERSION}/apache-skywalking-java-agent-${SKYWALKING_VERSION}.tgz \
       | tar -xzf - -C /opt \
    && ln -s /opt/skywalking-agent /skywalking

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational \
  -XX:MaxRAMPercentage=75 \
  -XX:InitialRAMPercentage=50 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/agent-platform/heapdump \
  -javaagent:/skywalking/skywalking-agent.jar"

WORKDIR /app

COPY --from=builder /build/agent-task-orchestrator/target/agent-task-orchestrator-*.jar app.jar

EXPOSE 8084 9084

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fs http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 13.2 K8s Deployment + Service + HPA

以 `agent-runtime` 为例（无状态服务，弹性伸缩）：

```yaml
# infra/k8s/agent-runtime-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: agent-runtime
  namespace: agent-platform-prod
  labels:
    app: agent-runtime
    tier: runtime
spec:
  replicas: 3
  selector:
    matchLabels:
      app: agent-runtime
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: agent-runtime
        tier: runtime
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8092"
        prometheus.io/path: /actuator/prometheus
    spec:
      serviceAccountName: agent-runtime-sa
      terminationGracePeriodSeconds: 60
      containers:
        - name: agent-runtime
          image: agentplatform/runtime:1.0.0
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8092
              protocol: TCP
            - name: grpc
              containerPort: 9092
              protocol: TCP
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            - name: SPRING_CLOUD_NACOS_DISCOVERY_NAMESPACE
              value: agent-platform-prod
            - name: HOSTNAME_HASH
              valueFrom:
                fieldRef:
                  fieldPath: metadata.uid
            - name: MYSQL_USER
              valueFrom:
                secretKeyRef:
                  name: mysql-credentials
                  key: username
            - name: MYSQL_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: mysql-credentials
                  key: password
          resources:
            requests:
              cpu: "500m"
              memory: "1Gi"
            limits:
              cpu: "2000m"
              memory: "4Gi"
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8092
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8092
            initialDelaySeconds: 60
            periodSeconds: 20
            failureThreshold: 5
          lifecycle:
            preStop:
              exec:
                command:
                  - sh
                  - -c
                  - "sleep 10 && curl -X POST http://localhost:8092/actuator/shutdown || true"
---
apiVersion: v1
kind: Service
metadata:
  name: agent-runtime
  namespace: agent-platform-prod
spec:
  type: ClusterIP
  selector:
    app: agent-runtime
  ports:
    - name: http
      port: 8092
      targetPort: 8092
      protocol: TCP
    - name: grpc
      port: 9092
      targetPort: 9092
      protocol: TCP
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: agent-runtime-hpa
  namespace: agent-platform-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: agent-runtime
  minReplicas: 3
  maxReplicas: 30
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    - type: Pods
      pods:
        metric:
          name: agent_active_instances
        target:
          type: AverageValue
          averageValue: "20"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Percent
          value: 100
          periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 50
          periodSeconds: 60
```

### 13.3 K8s 命名空间规划

| 命名空间 | 用途 | 资源配额基线 | 节点亲和 |
|---|---|---|---|
| `agent-platform-dev` | 开发环境 | CPU 20C / 内存 40G | 通用节点 |
| `agent-platform-staging` | 预发环境 | CPU 50C / 内存 100G | 独立节点池 |
| `agent-platform-prod` | 生产环境 | CPU 200C / 内存 400G | 生产节点池 |
| `agent-platform-infra` | 中间件集群 | CPU 100C / 内存 300G | 中间件节点池（独立） |

**ResourceQuota 示例**（生产命名空间）：

```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: agent-platform-prod-quota
  namespace: agent-platform-prod
spec:
  hard:
    requests.cpu: "200"
    requests.memory: 400Gi
    limits.cpu: "400"
    limits.memory: 800Gi
    persistentvolumeclaims: "20"
    services.loadbalancers: "2"
    pods: "100"
```

---

## 14. 部署架构

### 14.1 生产环境部署拓扑

```
                        ┌─────────────────────────────────────────┐
                        │       外部流量（Web/SDK/OpenAPI）         │
                        └────────────────────┬────────────────────┘
                                              │ HTTPS
                                    ┌─────────▼─────────┐
                                    │      SLB          │   公网四层负载
                                    └─────────┬─────────┘
                                              │
                              ┌───────────────┼───────────────┐
                              │               │               │
                       ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
                       │   gateway   │ │   gateway   │ │   gateway   │
                       │   (副本1)   │ │   (副本2)   │ │   (副本3)   │
                       └──────┬──────┘ └──────┬──────┘ └──────┬──────┘
                              │               │               │
                              └───────────────┼───────────────┘
                                              │
                ┌─────────────────────────────┼─────────────────────────────┐
                │                             │                             │
        ┌───────▼───────┐            ┌────────▼────────┐           ┌──────▼──────┐
        │ session (2)   │            │  task-orch (2)  │           │ risk-ctrl(2)│
        └───────┬───────┘            └────────┬────────┘           └──────┬──────┘
                │                             │                           │
        ┌───────▼───────┐            ┌────────▼────────┐           ┌──────▼──────┐
        │ planning(2)   │            │  memory (2)     │           │  tool-eng(2)│
        └───────────────┘            └────────┬────────┘           └──────┬──────┘
                                              │                           │
        ┌──────────────────────────────────────┼───────────────────────────┤
        │                                      │                           │
┌───────▼───────┐                      ┌──────▼──────┐           ┌────────▼───────┐
│  agent-       │                      │   model-    │           │ agent-repo /   │
│  runtime(3+)  │◄────────────────────►│   gateway   │           │ knowledge /    │
│  (HPA 3-30)   │                      │   (2)       │           │ quality (各2)  │
└───────────────┘                      └─────────────┘           └────────────────┘

                         中间件集群（agent-platform-infra 命名空间）
        ┌─────────────────────────────────────────────────────────────────┐
        │  MySQL(主从)  Milvus(集群)  Redis Cluster  RocketMQ(集群)        │
        │  ES Cluster   Neo4j       MinIO         SkyWalking  Prometheus  │
        │  Loki/Grafana  Nacos      Vault         XXL-Job                 │
        └─────────────────────────────────────────────────────────────────┘
```

### 14.2 中间件集群规模建议

| 中间件 | 部署模式 | 节点数 | 规格（每节点） | 总资源估算 |
|---|---|---|---|---|
| MySQL 8.0.36 | 主从 + 16 分库（共 16 主 16 从） | 32 | 16C / 64G / 1T SSD | CPU 512C / 内存 2048G |
| Milvus 2.4 | 集群（QueryNode + DataNode + IndexNode） | 9（3+3+3） | 8C / 32G / 500G SSD | CPU 72C / 内存 288G |
| Redis 7.2 Cluster | 主从 + 哨兵 | 12（6 主 6 从） | 4C / 16G / 100G | CPU 48C / 内存 192G |
| RocketMQ 5.3 | NameServer 3 + Broker 3 主 3 从 | 9（3+6） | 8C / 32G / 500G SSD | CPU 72C / 内存 288G |
| Elasticsearch 8.13 | Master 3 + Data 5 | 8 | 8C / 32G / 1T SSD | CPU 64C / 内存 256G |
| Neo4j 5.18 | 单实例（社区版） | 1 | 8C / 32G / 200G SSD | CPU 8C / 内存 32G |
| MinIO | 分布式 4 节点 | 4 | 4C / 16G / 5T HDD | CPU 16C / 内存 64G |
| SkyWalking 9.7 OAP | 集群 3 节点 + ES 后端存储 | 3 | 4C / 8G | CPU 12C / 内存 24G |
| Prometheus 2.51 | 单实例 + Thanos Query 扩展 | 2 | 4C / 8G / 500G | CPU 8C / 内存 16G |
| Nacos 2.x | 集群 3 节点 | 3 | 2C / 4G | CPU 6C / 内存 12G |
| Vault | 集群 3 节点（Raft） | 3 | 2C / 4G | CPU 6C / 内存 12G |
| XXL-Job | 单实例 | 1 | 2C / 4G | CPU 2C / 内存 4G |

### 14.3 资源配额建议表（业务服务）

| 服务 | 副本数（生产） | CPU Request / Limit | 内存 Request / Limit | 备注 |
|---|---|---|---|---|
| agent-gateway | 3 | 500m / 1000m | 1Gi / 2Gi | 入口流量，HPA 3-10 |
| session-service | 2 | 500m / 1000m | 1Gi / 2Gi | - |
| task-orchestrator | 2 | 1000m / 2000m | 2Gi / 4Gi | ShardingSphere 路由较重 |
| planning-service | 2 | 500m / 1000m | 1Gi / 2Gi | - |
| memory-service | 2 | 1000m / 2000m | 2Gi / 4Gi | Milvus + embedding 调用频繁 |
| tool-engine | 2 | 1000m / 2000m | 2Gi / 4Gi | 沙箱执行资源消耗大 |
| agent-runtime | 3 | 500m / 2000m | 1Gi / 4Gi | HPA 3-30，按 Agent 实例数扩缩 |
| model-gateway | 2 | 1000m / 2000m | 2Gi / 4Gi | 高 QPS，Prompt 缓存消耗内存 |
| agent-repo | 2 | 500m / 1000m | 1Gi / 2Gi | - |
| knowledge-service | 2 | 500m / 1000m | 1Gi / 2Gi | - |
| quality-service | 2 | 500m / 1000m | 1Gi / 2Gi | - |
| risk-control | 2 | 500m / 1000m | 1Gi / 2Gi | - |
| observability | 2 | 500m / 1000m | 1Gi / 2Gi | - |

**业务服务总资源估算（生产）**：

| 维度 | 总 Request | 总 Limit |
|---|---|---|
| CPU | 27.5 核 | 55 核 |
| 内存 | 56 Gi | 116 Gi |

考虑 HPA 弹性扩容上限，生产环境业务服务预留 CPU 100C / 内存 200G。

---

## 15. 运维脚本

### 15.1 init-db.ps1 初始化数据库

```powershell
<#
.SYNOPSIS
    Agent Platform 数据库初始化脚本
.DESCRIPTION
    执行 infra/sql/ 下的全部 DDL 脚本，初始化 MySQL / Milvus / Neo4j / ClickHouse
.PARAMETER Environment
    目标环境：dev | staging | prod
.PARAMETER Host
    MySQL 主机地址，默认 localhost
.PARAMETER Port
    MySQL 端口，默认 3306
.PARAMETER User
    MySQL 管理员用户，默认 root
.PARAMETER Password
    MySQL 管理员密码（必填）
.PARAMETER LogFile
    日志输出路径，默认 ./logs/init-db-<timestamp>.log
.EXAMPLE
    .\init-db.ps1 -Environment dev -Password xxx
#>
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("dev","staging","prod")]
    [string]$Environment,

    [string]$Host = "localhost",
    [int]$Port = 3306,
    [string]$User = "root",
    [Parameter(Mandatory=$true)]
    [string]$Password,

    [string]$LogFile = "./logs/init-db-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

# Ensure log directory
$logDir = Split-Path -Parent $LogFile
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "[$ts] [$Level] $Message"
    Add-Content -Path $LogFile -Value $line
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "WARN"  { Write-Host $line -ForegroundColor Yellow }
        "INFO"  { Write-Host $line -ForegroundColor Green }
        default { Write-Host $line }
    }
}

Write-Log "INFO" "=== Agent Platform DB Init Start (env=$Environment) ==="
Write-Log "INFO" "Log file: $LogFile"

# Locate mysql client
$mysqlExe = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysqlExe) {
    $mysqlExe = "D:\_program\mariadb\bin\mysql.exe"
    if (-not (Test-Path $mysqlExe)) {
        Write-Log "ERROR" "mysql client not found"
        exit 1
    }
}
Write-Log "INFO" "Using mysql: $mysqlExe"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptRoot
$sqlDir = Join-Path $projectRoot "infra\sql"

if (-not (Test-Path $sqlDir)) {
    Write-Log "ERROR" "SQL directory not found: $sqlDir"
    exit 1
}

# Execute MySQL DDL scripts
$mysqlScripts = @(
    "01-agent-session.sql",
    "02-agent-task.sql",
    "03-agent-memory.sql",
    "04-agent-tool.sql",
    "05-agent-model.sql",
    "06-agent-repo.sql",
    "07-agent-knowledge.sql",
    "08-agent-quality.sql",
    "09-agent-risk.sql",
    "99-init-data.sql"
)

foreach ($script in $mysqlScripts) {
    $path = Join-Path $sqlDir $script
    if (-not (Test-Path $path)) {
        Write-Log "WARN" "Skip missing script: $script"
        continue
    }
    Write-Log "INFO" "Executing: $script"
    $args = @("-h", $Host, "-P", $Port, "-u", $User, "-p$Password", "--default-character-set=utf8mb4", "-e", "source $path")
    $output = & $mysqlExe @args 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Log "ERROR" "Failed: $script. Output: $output"
        exit 1
    }
    Write-Log "INFO" "OK: $script"
}

# ClickHouse tables
$chScript = Join-Path $sqlDir "12-clickhouse-tables.sql"
if (Test-Path $chScript) {
    Write-Log "INFO" "Executing ClickHouse: $chScript"
    # clickhouse-client --host=ch-host --query="$(Get-Content $chScript -Raw)"
    Write-Log "WARN" "ClickHouse execution skipped, run manually"
}

Write-Log "INFO" "=== Agent Platform DB Init Done ==="
```

### 15.2 deploy.ps1 一键部署

```powershell
<#
.SYNOPSIS
    Agent Platform 一键部署脚本
.DESCRIPTION
    构建 Docker 镜像 → 推送 → kubectl apply K8s 清单
.PARAMETER Environment
    目标环境：dev | staging | prod
.PARAMETER Services
    要部署的服务列表（逗号分隔），默认 all
.PARAMETER SkipBuild
    跳过构建，仅 apply K8s 清单
.PARAMETER SkipPush
    跳过推送镜像
.EXAMPLE
    .\deploy.ps1 -Environment prod -Services agent-runtime,task-orchestrator
#>
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("dev","staging","prod")]
    [string]$Environment,

    [string]$Services = "all",
    [switch]$SkipBuild,
    [switch]$SkipPush,

    [string]$LogFile = "./logs/deploy-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
)

$ErrorActionPreference = "Stop"
$logDir = Split-Path -Parent $LogFile
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "[$ts] [$Level] $Message"
    Add-Content -Path $LogFile -Value $line
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "WARN"  { Write-Host $line -ForegroundColor Yellow }
        "INFO"  { Write-Host $line -ForegroundColor Green }
        default { Write-Host $line }
    }
}

Write-Log "INFO" "=== Agent Platform Deploy Start (env=$Environment) ==="

# Locate tools
$kubectl = (Get-Command kubectl -ErrorAction SilentlyContinue).Source
if (-not $kubectl) {
    Write-Log "ERROR" "kubectl not found"
    exit 1
}
$docker = (Get-Command docker -ErrorAction SilentlyContinue).Source
if (-not $docker) {
    Write-Log "ERROR" "docker not found"
    exit 1
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptRoot
$infraDir = Join-Path $projectRoot "infra"
$k8sDir = Join-Path $infraDir "k8s"

# All services
$allServices = @(
    "agent-gateway", "session-service", "task-orchestrator", "planning-service",
    "memory-service", "tool-engine", "agent-runtime", "model-gateway",
    "agent-repo", "knowledge-service", "quality-service", "risk-control", "observability"
)

if ($Services -eq "all") {
    $targetServices = $allServices
} else {
    $targetServices = $Services -split ","
}

$namespace = "agent-platform-$Environment"
$imageTag = (Get-Date -Format "yyyyMMdd-HHmmss")

# Step 1: Build images
if (-not $SkipBuild) {
    Write-Log "INFO" "Step 1: Building images for: $($targetServices -join ', ')"
    foreach ($svc in $targetServices) {
        $moduleName = $svc -replace "-", "" -replace "service", "" -replace "agent", "agent"
        $dockerfile = Join-Path $infraDir "docker\Dockerfile.$svc"
        if (-not (Test-Path $dockerfile)) {
            $dockerfile = Join-Path $infraDir "docker\Dockerfile.template"
        }
        $image = "agentplatform/${svc}:${imageTag}"
        Write-Log "INFO" "Building $image"
        $buildArgs = @("build", "-t", $image, "-f", $dockerfile, $projectRoot)
        & $docker @buildArgs 2>&1 | ForEach-Object { Write-Log "INFO" $_ }
        if ($LASTEXITCODE -ne 0) {
            Write-Log "ERROR" "Build failed: $svc"
            exit 1
        }
        if (-not $SkipPush) {
            Write-Log "INFO" "Pushing $image"
            & $docker push $image 2>&1 | ForEach-Object { Write-Log "INFO" $_ }
        }
    }
}

# Step 2: Apply K8s manifests
Write-Log "INFO" "Step 2: Applying K8s manifests to namespace=$namespace"
& $kubectl apply -f (Join-Path $k8sDir "namespace-$Environment.yaml") 2>&1 | ForEach-Object { Write-Log "INFO" $_ }

foreach ($svc in $targetServices) {
    $manifests = @(
        "configmap-$svc.yaml",
        "secret-$svc.yaml",
        "$svc-deployment.yaml",
        "$svc-service.yaml"
    )
    foreach ($m in $manifests) {
        $path = Join-Path $k8sDir $m
        if (Test-Path $path) {
            Write-Log "INFO" "Applying $m"
            & $kubectl apply -f $path -n $namespace 2>&1 | ForEach-Object { Write-Log "INFO" $_ }
        }
    }
    # HPA only for stateless services
    $hpaPath = Join-Path $k8sDir "$svc-hpa.yaml"
    if (Test-Path $hpaPath) {
        & $kubectl apply -f $hpaPath -n $namespace 2>&1 | ForEach-Object { Write-Log "INFO" $_ }
    }
}

# Step 3: Rollout status
Write-Log "INFO" "Step 3: Checking rollout status"
foreach ($svc in $targetServices) {
    Write-Log "INFO" "Waiting for $svc rollout..."
    & $kubectl rollout status deployment/$svc -n $namespace --timeout=300s 2>&1 | ForEach-Object { Write-Log "INFO" $_ }
}

Write-Log "INFO" "=== Agent Platform Deploy Done ==="
Write-Log "INFO" "Namespace: $namespace"
Write-Log "INFO" "Services deployed: $($targetServices.Count)"
Write-Log "INFO" "Image tag: $imageTag"
```

### 15.3 build-all.ps1 全量构建

```powershell
<#
.SYNOPSIS
    Agent Platform 全量构建脚本
.DESCRIPTION
    执行 maven 全量构建（含单元测试 + 镜像构建 + 推送）
.PARAMETER SkipTests
    跳过单元测试
.PARAMETER SkipImage
    跳过镜像构建
.PARAMETER PushImage
    推送镜像到仓库
.EXAMPLE
    .\build-all.ps1 -SkipTests -PushImage
#>
param(
    [switch]$SkipTests,
    [switch]$SkipImage,
    [switch]$PushImage,

    [string]$LogFile = "./logs/build-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
)

$ErrorActionPreference = "Stop"
$logDir = Split-Path -Parent $LogFile
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "[$ts] [$Level] $Message"
    Add-Content -Path $LogFile -Value $line
    switch ($Level) {
        "ERROR" { Write-Host $line -ForegroundColor Red }
        "WARN"  { Write-Host $line -ForegroundColor Yellow }
        "INFO"  { Write-Host $line -ForegroundColor Green }
        default { Write-Host $line }
    }
}

Write-Log "INFO" "=== Agent Platform Build Start ==="

# Locate maven
$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    Write-Log "ERROR" "maven not found"
    exit 1
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptRoot

# Step 1: Maven build
Write-Log "INFO" "Step 1: Maven build"
$mvnArgs = @("clean", "package", "-B", "-T", "1C")
if ($SkipTests) {
    $mvnArgs += "-DskipTests"
    Write-Log "WARN" "Tests skipped"
}
Write-Log "INFO" "mvn $($mvnArgs -join ' ')"
Push-Location $projectRoot
try {
    & $mvn @mvnArgs 2>&1 | ForEach-Object { Write-Log "INFO" $_ }
    if ($LASTEXITCODE -ne 0) {
        Write-Log "ERROR" "Maven build failed"
        exit 1
    }
} finally {
    Pop-Location
}
Write-Log "INFO" "Maven build OK"

# Step 2: Build images
if (-not $SkipImage) {
    $docker = (Get-Command docker -ErrorAction SilentlyContinue).Source
    if (-not $docker) {
        Write-Log "ERROR" "docker not found"
        exit 1
    }

    $modules = @(
        "agent-gateway", "session-service", "task-orchestrator", "planning-service",
        "memory-service", "tool-engine", "agent-runtime", "model-gateway",
        "agent-repo", "knowledge-service", "quality-service", "risk-control", "observability"
    )

    $imageTag = (Get-Date -Format "yyyyMMdd-HHmmss")
    $infraDir = Join-Path $projectRoot "infra\docker"

    foreach ($mod in $modules) {
        $dockerfile = Join-Path $infraDir "Dockerfile.$mod"
        if (-not (Test-Path $dockerfile)) {
            $dockerfile = Join-Path $infraDir "Dockerfile.template"
        }
        $image = "agentplatform/${mod}:${imageTag}"
        Write-Log "INFO" "Building $image"
        & $docker build -t $image -f $dockerfile $projectRoot 2>&1 | ForEach-Object { Write-Log "INFO" $_ }
        if ($LASTEXITCODE -ne 0) {
            Write-Log "ERROR" "Build failed: $mod"
            exit 1
        }
        if ($PushImage) {
            Write-Log "INFO" "Pushing $image"
            & $docker push $image 2>&1 | ForEach-Object { Write-Log "INFO" $_ }
        }
    }
}

Write-Log "INFO" "=== Agent Platform Build Done ==="
Write-Log "INFO" "Image tag: $imageTag"
Write-Log "INFO" "Log: $LogFile"
```

---

## 16. 与其他文档交叉引用

| 本文档章节 | 引用文档 | 引用内容 |
|---|---|---|
| §1.2.1 模型路由 | [01-database §5.2](../01-database/database-schema-design.md#52-model_route_rule-模型路由规则表) | `model_route_rule` 表 |
| §1.2.4 输出校验 | [02-api §9.1](../02-api/api-specification.md#91-质量评估) | 质量评估 API |
| §1.2.6 Badcase 归集 | [01-database §8.3](../01-database/database-schema-design.md#83-mysqlbadcase-异常案例表) / [02-api §9.2](../02-api/api-specification.md#92-badcase-管理) | `badcase` 表 + Badcase 管理 API |
| §1.2.6 幻觉率指标 | [01-database §8.4](../01-database/database-schema-design.md#84-clickhouseagent_metrics_daily-agent-指标日表) | `agent_metrics_daily` ClickHouse 表 |
| §2.2.1 黄金基准集 | [01-database §8.2](../01-database/database-schema-design.md#82-mysqleval_baseline-黄金基准集表) / [02-api §9.3](../02-api/api-specification.md#93-治理配置) | `eval_baseline` 表 + 治理配置 API |
| §2.2.1 全要素版本化 | [01-database §6.2](../01-database/database-schema-design.md#62-agent_version-agent-版本表) | `agent_version.is_stable` |
| §2.2.1 核心约束固化 | [01-database §6.1](../01-database/database-schema-design.md#61-agent_definition-agent-定义表) | `core_constraints` vs `business_config` |
| §3.1 模型选型 | [02-api §5](../02-api/api-specification.md#5-模型网关-apigrpc内部) | `ChatRequest.scene/tier` |
| §3.4 成本熔断 | [01-database §4.3](../01-database/database-schema-design.md#43-tool_quota-工具配额表) / [§5.3](../01-database/database-schema-design.md#53-model_usage_log-模型调用计量表按月分表) | `tool_quota` + `model_usage_log` |
| §3.4 成本熔断 | [02-api §0.5](../02-api/api-specification.md#5-错误码规范) | `COST_BUDGET_EXCEEDED` 错误码 |
| §4.2 分库分表 | [01-database §0.3](../01-database/database-schema-design.md#03-分库分表策略shardingsphere) | 分片策略 |
| §5.1 Milvus Collections | [01-database §10.1](../01-database/database-schema-design.md#101-collection-规划) | Collection 规划 |
| §5.3 召回参数 | [01-database §10.3](../01-database/database-schema-design.md#103-召回参数基线) | 召回参数基线 |
| §6.2 Redis Key TTL | [01-database §3.4](../01-database/database-schema-design.md#34-redis-短期记忆-key-设计) | Redis Key 设计 |
| §7.1 RocketMQ Topic | [02-api §11](../02-api/api-specification.md#11-异步事件规范rocketmq-topics) | Topic 清单 |
| §7.3 消费者幂等 | [02-api §11.2](../02-api/api-specification.md#112-幂等与重试) | 幂等与重试策略 |
| §9.2 Neo4j 约束 | [01-database §11.3](../01-database/database-schema-design.md#113-索引) | Neo4j 索引 |
| §12.4 Vault 接入 | [01-database §5.1](../01-database/database-schema-design.md#51-model_provider-模型供应商表) | `model_provider.api_key_ref` |
| §1 ~ §3 治理体系 | [00-overview §6](../00-overview/tech-stack-and-architecture.md#6-横向体系落地方式) | 横向体系落地方式 |
| §1.2.3 工具网关 | [00-overview ADR-005](../00-overview/tech-stack-and-architecture.md#adr-005工具调用统一走-grpc-网关而非直连) | 工具网关决策 |
| §3.3 上下文压缩降本 | [00-overview ADR-002](../00-overview/tech-stack-and-architecture.md#adr-002agent-运行时无状态化状态外置到-redis--mysql) | Agent 无状态化 |
| §3.2 Token 基线 | [00-overview §1.2](../00-overview/tech-stack-and-architecture.md#12-非功能性指标基线) | 非功能性指标基线 |
| §13 / §14 部署 | [00-overview §3.1](../00-overview/tech-stack-and-architecture.md#31-微服务拆分清单11-个核心服务--2-个横向服务) | 微服务清单与端口 |

---

## 17. 文档变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-06-26 | 初始版本，覆盖 PRD 第四节/第五节/第六节治理体系 + 第七节交付物 5（配置部署）+ 交付物 7（中间件集成） | agent-platform-team |