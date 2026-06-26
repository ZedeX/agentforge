# 工具能力与调用管控系统 详细设计

> 文档版本：v1.0  |  更新日期：2026-06-26  |  对应 PRD：第二节(二)工具能力与调用管控系统

## 0. 文档说明

### 0.1 文档目的

本文档是 `tool-engine`（工具引擎服务，端口 8090）的工程级详细设计，落地 PRD 第二节(二) 全部要求：工具标准化封装、调用全链路标准流程、权限边界管控、Token 与成本管控、业务效果量化指标。文档覆盖接口契约、算法伪代码、核心类签名、表结构映射，作为后续编码实现的基线契约。

### 0.2 依赖文档

| 文档 | 关键引用点 |
|---|---|
| [00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md) | tool-engine 端口 8090；存储 MySQL/MinIO；gRPC 调用；ADR-005 工具统一走网关；命名规范 `com.agentplatform.tool` |
| [01-database/database-schema-design.md](../01-database/database-schema-design.md) | 第 4 节工具域 `tool_registry`/`tool_call_log`/`tool_quota`/`tool_approval`；第 9 节 `audit_log`/`permission_policy`；第 10 节 Milvus `tool_index`；第 8.4 节 ClickHouse `agent_metrics_daily` |
| [02-api/api-specification.md](../02-api/api-specification.md) | 第 3 节工具引擎 REST 管理 + `ToolGateway` gRPC（`Invoke`/`Recall`/`ReportResult`）；第 11 节 RocketMQ `tool.call.audit`；第 12 节限流熔断 |
| [PRD.md](../../PRD.md) | 第二节(二) 1~5 全部子项 |

### 0.3 术语约定

| 术语 | 含义 |
|---|---|
| 原子工具 | 不可再拆的最小能力单元，单次外部调用完成 |
| 复合工具 | 编排多个原子工具的有序组合，内部维护子调用 DAG |
| Agent 型工具 | 工具本身包装一个子 Agent，对外暴露工具语义 |
| R1/R2/R3 | 工具风险三级：低危/中危/高危 |
| 执行器（Executor） | `general`/`proxy`/`sandbox` 三种隔离执行模式 |
| 调用网关（ToolGateway） | 所有工具调用的唯一入口，禁止 Agent 直连工具 |

---

## 1. 系统定位与总体架构

### 1.1 系统定位

`tool-engine` 是平台核心引擎层五大引擎之一，承担 Agent 与外部世界交互的**唯一标准化通道**。依据 [ADR-005](../00-overview/tech-stack-and-architecture.md#adr-005工具调用统一走-grpc-网关而非直连)：所有工具调用必须经过 `ToolGateway`，禁止 Agent Runtime 直连工具。系统职责边界：

| 职责 | 归属 | 不归属 |
|---|---|---|
| 工具注册/版本/生命周期 | tool-engine | — |
| 工具语义召回（Milvus `tool_index`） | tool-engine | — |
| 调用统一网关 + 前置校验 + 路由分发 | tool-engine | — |
| 隔离执行（general/proxy/sandbox） | tool-engine | — |
| 结果标准化清洗 + 返回上下文 | tool-engine | — |
| 全量落盘审计（`tool_call_log` + `audit_log`） | tool-engine + risk-control | — |
| 权限策略定义与判定 | risk-control（`permission_policy`） | tool-engine 仅调用 |
| 配额扣减与成本熔断 | tool-engine（`tool_quota`） | — |
| 模型生成调用指令 | agent-runtime + model-gateway | tool-engine 仅接收 |

### 1.2 总体架构

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              agent-runtime (8092)                            │
│                          ReAct 循环 / 工具调用决策                            │
└───────────────────────────────┬──────────────────────────────────────────────┘
                                │ gRPC: ToolGateway.Invoke / Recall / ReportResult
                                ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                              tool-engine (8090)                              │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ToolRecaller│  │  ToolGateway  │  │  ToolExecutor │  │ ToolCallAuditor  │  │
│  │ 语义召回    │→│ 网关入口+校验 │→│  路由+隔离执行 │→│ 审计+落盘+上报   │  │
│  └─────┬──────┘  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘  │
│        │                │                  │                   │            │
│        ▼                ▼                  ▼                   ▼            │
│  ┌──────────┐    ┌────────────┐    ┌────────────────┐   ┌──────────────┐     │
│  │ Milvus   │    │ risk-      │    │ general/proxy/ │   │ MySQL        │     │
│  │tool_index│    │ control    │    │ sandbox exec    │   │tool_call_log │     │
│  │ (1024维) │    │permission_ │    │                  │   │ + RocketMQ   │     │
│  └──────────┘    │ policy     │    └────────────────┘   │ tool.call.   │     │
│                  └────────────┘                          │   audit       │     │
│                                                          └──────────────┘     │
│  ┌────────────────┐  ┌────────────────┐                                       │
│  │ToolQuotaManager│  │ ResultCleaner │                                       │
│  │配额/成本熔断   │  │ 结果标准化清洗 │                                       │
│  └────────────────┘  └────────────────┘                                       │
└──────────────────────────────────────────────────────────────────────────────┘
        │ gRPC                    │ gRPC               │ gRPC
        ▼                         ▼                    ▼
┌──────────────┐          ┌──────────────┐     ┌──────────────────┐
│  业务服务    │          │ risk-control │     │  Docker 沙箱池   │
│ (proxy执行)  │          │  (8092→8102) │     │  (R3 高危执行)    │
└──────────────┘          └──────────────┘     └──────────────────┘
```

### 1.3 核心设计原则

| 原则 | 落地约束 |
|---|---|
| 统一入口 | 所有工具调用必经 `ToolGateway`，禁止 Agent 直连工具 |
| 管控与执行分离 | 校验/路由/审计在网关层；执行下沉到 Executor |
| 风险分级管控 | R1/R2/R3 对应不同执行器与审批要求 |
| 全链路留痕 | 每次调用在 `tool_call_log` + `audit_log` 双写 |
| 成本可观测可熔断 | 按 工具/业务线/单任务 三级阈值熔断 |
| Schema 强约束 | 入参/出参/错误码全 Schema 化，禁止裸 JSON |

---

## 2. 工具标准化封装

### 2.1 三层 Schema 设计

依据 PRD「三层 Schema」要求，所有工具遵循统一封装规范，分别对应元数据层、接口层、实现层。

#### 2.1.1 元数据层（meta schema）

元数据层描述工具的资产属性，对应 [`tool_registry`](../01-database/database-schema-design.md#41-tool_registry-工具注册表) 表字段。

| Schema 字段 | 表字段 | 类型 | 说明 |
|---|---|---|---|
| `toolId` | `tool_id` | VARCHAR(32) | 工具业务 ID |
| `name` | `name` | VARCHAR(64) | 工具名（唯一，含版本） |
| `displayName` | `display_name` | VARCHAR(128) | 显示名 |
| `description` | `description` | TEXT | 功能描述（供模型召回，必填） |
| `sceneTags` | `scene_tags` | JSON | 场景标签数组 |
| `toolType` | `tool_type` | VARCHAR(16) | `atomic`/`composite`/`agent` |
| `riskLevel` | `risk_level` | TINYINT | 1=R1 2=R2 3=R3 |
| `executorType` | `executor_type` | VARCHAR(16) | `general`/`proxy`/`sandbox` |
| `endpoint` | `endpoint` | VARCHAR(255) | gRPC service/method |
| `timeoutMs` | `timeout_ms` | INT | 默认超时 |
| `avgCostCent` | `avg_cost_cent` | BIGINT | 平均成本（分） |
| `avgDurationMs` | `avg_duration_ms` | INT | 平均耗时 |
| `undoAction` | `undo_action` | JSON | 补偿动作（写操作必填） |
| `promptCacheKey` | `prompt_cache_key` | VARCHAR(128) | Prompt 缓存键 |
| `status` | `status` | TINYINT | 1=草稿 2=启用 3=下线 |
| `version` | `version` | INT | 版本号 |

#### 2.1.2 接口层（interface schema）

接口层定义工具的输入输出契约，对应 `tool_registry.input_schema` / `output_schema` / `error_codes` 三列，全部以 JSON Schema 严格描述。

**inputSchema 示例（JSON Schema Draft 2020-12 子集）**：

```json
{
  "type": "object",
  "properties": {
    "userId": {"type": "string", "description": "用户ID", "minLength": 1, "maxLength": 64},
    "days": {"type": "integer", "description": "查询天数", "default": 7, "minimum": 1, "maximum": 90}
  },
  "required": ["userId"],
  "additionalProperties": false
}
```

**outputSchema 示例**：

```json
{
  "type": "object",
  "properties": {
    "orders": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "orderId": {"type": "string"},
          "amount": {"type": "number"},
          "status": {"type": "string", "enum": ["paid", "shipped", "done"]}
        },
        "required": ["orderId", "amount", "status"]
      }
    },
    "total": {"type": "integer"}
  },
  "required": ["orders", "total"]
}
```

**errorCodes 规范**：

```json
[
  {"code": "USER_NOT_FOUND", "message": "用户不存在", "retryable": false, "httpStatus": 404},
  {"code": "RATE_LIMITED", "message": "查询频次超限", "retryable": true, "httpStatus": 429},
  {"code": "UPSTREAM_TIMEOUT", "message": "上游服务超时", "retryable": true, "httpStatus": 504}
]
```

错误码字段约束：

| 字段 | 必填 | 说明 |
|---|---|---|
| `code` | 是 | 大写下划线，工具内唯一 |
| `message` | 是 | 中文可读说明 |
| `retryable` | 是 | 是否可重试（瞬时异常 true，业务异常 false） |
| `httpStatus` | 否 | 上游 HTTP 状态码（proxy 类型适用） |

#### 2.1.3 实现层（implementation layer）

实现层按 PRD「原子/复合/Agent 型」三级封装，落地于 `tool_type` 字段。

| toolType | 含义 | 执行特征 | executorType 约束 | 典型示例 |
|---|---|---|---|---|
| `atomic` | 原子工具，单次外部调用完成 | 不可再拆；同步调用一次后端 | `general`/`proxy` | 查询订单、获取天气、知识检索 |
| `composite` | 复合工具，编排多个原子工具 | 内部维护子调用 DAG；支持事务补偿 | `general`/`proxy` | "下单并发货"=create_order→ship→notify |
| `agent` | Agent 型工具，包装子 Agent | 对外暴露工具语义；内部跑 ReAct 循环 | `general` | 代码审查工具（内部子 Agent 多轮分析） |

复合工具 DAG 存储于 `endpoint` 指向的内部编排器，子节点引用其他 `tool_id`。Agent 型工具通过 `endpoint` 指向 `agent-repo` 的子 Agent 实例。

### 2.2 工具版本管理

版本号语义遵循 [`tool_registry`](../01-database/database-schema-design.md#41-tool_registry-工具注册表) 的 `uk_name_version` (name, version) 唯一约束。

#### 2.2.1 版本号规则

| 变更类型 | 版本变更 | 是否新建版本 | 调用方影响 |
|---|---|---|---|
| 破坏性变更（inputSchema 删字段/改类型/收紧约束） | 主版本+1 | 是 | 必须显式指定新版本 |
| 兼容性变更（outputSchema 增字段/放宽约束） | 次版本+1 | 是 | 旧调用方无感知 |
| 描述/标签/平均指标更新 | 不变版本 | 否（直接 update） | 无 |

调用方在 `ToolInvokeRequest.tool_version` 显式指定版本；未指定时网关取 `status=2` 的最新启用版本。

#### 2.2.2 版本兼容性约束

- 启用中的版本禁止物理删除，仅可置 `status=3`（下线）
- 下线版本 30 天内仍可被历史 `tool_call_log` 引用查询
- 新版本上线前必须通过 Schema 兼容性校验：旧 inputSchema 必须能通过新 inputSchema 校验

### 2.3 工具生命周期

生命周期状态机对应 `tool_registry.status` 字段：

```
            注册                审批通过(R3)
  ┌─────────┐──────►┌─────────┐──────────►┌─────────┐
  │  (空)   │       │ 草稿(1) │            │ 启用(2)  │
  └─────────┘       └────┬────┘            └────┬────┘
                         │ 审批驳回               │ 下线操作
                         ▼                        ▼
                    ┌─────────┐            ┌─────────┐
                    │ 草稿(1) │            │ 下线(3)  │
                    └─────────┘            └─────────┘
                                                │ 30天保留期
                                                ▼
                                          物理归档(逻辑删除)
```

| 状态 | 值 | 可执行调用 | 可被召回 | 可被绑定 Agent |
|---|---|---|---|---|
| 草稿 | 1 | 否 | 否 | 否 |
| 启用 | 2 | 是 | 是 | 是 |
| 下线 | 3 | 否（已绑定 Agent 的存量调用 30 天内放行） | 否 | 否 |

状态流转约束：
- R1/R2 工具：草稿→启用 由工具管理员审批
- R3 工具：草稿→启用 必须经过 `tool_approval` 审批流程（见 §4.4）
- 启用→下线：触发存量绑定 Agent 检查，影响范围超阈值需灰度下线

### 2.4 工具注册 REST API 契约

依据 [API 规范第 3.1 节](../02-api/api-specification.md#31-工具注册管理rest)，注册接口契约如下：

```yaml
POST /api/v1/tools
  Request: ToolRegisterRequest（见 2.1 三层 Schema 合并）
  Response.data: { toolId, version, status }
  错误码: VALIDATION_FAILED / TOOL_NAME_DUPLICATED / SCHEMA_INVALID

PUT /api/v1/tools/{toolId}
  # 创建新版本，旧版本保持不变
  Response.data: { toolId, version }

POST /api/v1/tools/{toolId}/approve
  # 仅 R3 工具走此流程，写入 tool_approval 表
  Request: { inputSnapshot, applicant, reason, expireAt }
  Response.data: { approvalId, status: "pending" }

GET /api/v1/tools?sceneTag=order&riskLevel=2&status=2&page=1&size=20
GET /api/v1/tools/{toolId}
```

---

## 3. 工具调用全链路标准流程

### 3.1 全链路总览

依据 PRD 第二节(二)2，工具调用全链路固定 9 步，任何调用不得跳步：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ①工具语义召回  →  ②模型生成调用指令  →  ③统一网关接入  →  ④前置校验        │
│                                                                          │
│  ⑤路由分发  →  ⑥隔离执行  →  ⑦结果标准化清洗  →  ⑧返回Agent上下文        │
│                                                                          │
│  ⑨全量落盘审计                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

| 步骤 | 归属组件 | 同步/异步 | 数据产出 |
|---|---|---|---|
| ① 工具语义召回 | `ToolRecaller` | 同步 | `ToolCandidate[]` |
| ② 模型生成调用指令 | agent-runtime + model-gateway | 同步 | `{toolId, inputJson}` |
| ③ 统一网关接入 | `ToolGateway.Invoke` | 同步 | `ToolInvokeRequest` |
| ④ 前置校验 | `ToolGateway` + risk-control | 同步 | 校验通过/拒绝 |
| ⑤ 路由分发 | `ToolExecutor` | 同步 | 选定 Executor |
| ⑥ 隔离执行 | `GeneralExecutor`/`ProxyExecutor`/`SandboxExecutor` | 同步 | 原始输出 |
| ⑦ 结果标准化清洗 | `ResultCleaner` | 同步 | 标准化输出 |
| ⑧ 返回 Agent 上下文 | `ToolGateway` | 同步 | `ToolInvokeResponse` |
| ⑨ 全量落盘审计 | `ToolCallAuditor` | 异步 | `tool_call_log` + `audit_log` |

### 3.2 Step1：工具语义召回

**触发时机**：agent-runtime 在 ReAct 循环的 think 阶段，根据当前任务目标与上一步观察，主动调用 `ToolGateway.Recall` 检索可用工具。

**实现要点**：见 §7 工具语义召回。

### 3.3 Step2：模型生成调用指令

**归属**：agent-runtime（不在 tool-engine 范围），但 tool-engine 对模型生成的指令做**对齐校验**（见 §7.3）。

模型输出的调用指令格式：

```json
{
  "toolId": "tl_query_order",
  "inputJson": "{\"userId\":\"u_123\",\"days\":7}"
}
```

### 3.4 Step3：统一网关接入

**入口**：`ToolGateway.Invoke`（gRPC，定义见 [API 第 3.2 节](../02-api/api-specification.md#32-工具调用grpc仅内部-agent-runtime-调用)）。

**职责**：
- 接收 `ToolInvokeRequest`（含 `call_id`/`task_id`/`step_no`/`agent_id`/`tool_id`/`tool_version`/`input_json`/`trace`）
- 生成调用上下文 `ToolCallContext`（贯穿后续所有步骤）
- 注入 SkyWalking TraceID 透传

**禁止行为**：
- 禁止 Agent 绕过网关直连工具（ADR-005）
- 禁止从非 `agent-runtime` 来源发起 `Invoke`（网关侧通过 mTLS + 服务账号校验）

### 3.5 Step4：前置校验

前置校验是网关的核心管控点，按顺序执行，任一失败立即返回错误并落审计日志（status=blocked）：

```text
function preCheck(ctx: ToolCallContext):
    # 1. 工具存在性 + 状态校验
    tool = toolRegistry.findById(ctx.toolId, ctx.toolVersion)
    if tool == null: return reject(TOOL_NOT_FOUND)
    if tool.status != ENABLED: return reject(TOOL_DISABLED)

    # 2. inputSchema 参数校验（JSON Schema Validator）
    errors = jsonSchemaValidator.validate(ctx.inputJson, tool.inputSchema)
    if errors.notEmpty(): return reject(VALIDATION_FAILED, errors)

    # 3. 工具-Agent 绑定校验
    if not agentToolBinder.isBound(ctx.agentId, ctx.toolId):
        return reject(TOOL_NOT_BOUND)

    # 4. RBAC + ABAC 权限校验（调用 risk-control.preCheck）
    permResult = riskControlClient.preCheck(
        subjectType="agent", subjectId=ctx.agentId,
        resourceType="tool", resourceId=ctx.toolId,
        action="execute", attributes=ctx.toAbacAttributes())
    if permResult.effect != "allow":
        return reject(FORBIDDEN, permResult.reason)

    # 5. R3 高危工具审批校验
    if tool.riskLevel == R3:
        approval = toolApprovalService.findValid(ctx.toolId, ctx.taskId, ctx.inputSnapshot)
        if approval == null or approval.status != APPROVED:
            return reject(APPROVAL_REQUIRED)
        if approval.expireAt < now(): return reject(APPROVAL_EXPIRED)

    # 6. 高危参数白名单校验（见 §4.3.1）
    if tool.riskLevel >= R2:
        paramWhitelistChecker.check(tool, ctx.inputJson)

    # 7. 配额校验（见 §5.5）
    if not toolQuotaManager.tryAcquire(ctx, tool):
        return reject(COST_BUDGET_EXCEEDED)

    # 8. 缓存命中校验（见 §5.4.2 相同入参结果缓存）
    cached = toolResultCache.get(ctx.toolId, ctx.inputHash)
    if cached != null:
        ctx.markFromCache(cached)
        return shortCircuit(cached)

    return pass()
```

| 校验项 | 失败错误码 | 是否落审计 |
|---|---|---|
| 工具不存在 | `TOOL_NOT_FOUND` | 是 |
| 工具未启用 | `TOOL_DISABLED` | 是 |
| 参数 Schema 不合法 | `VALIDATION_FAILED` | 是 |
| 工具未绑定 Agent | `TOOL_NOT_BOUND` | 是 |
| 权限拒绝 | `FORBIDDEN` / `TOOL_RISK_DENIED` | 是 |
| R3 审批未通过 | `APPROVAL_REQUIRED` / `APPROVAL_EXPIRED` | 是 |
| 配额耗尽 | `COST_BUDGET_EXCEEDED` | 是 |
| 缓存命中 | `OK`（短路返回） | 是（status=success, from_cache=true） |

### 3.6 Step5：路由分发

依据 `tool_registry.executor_type` 路由到对应 Executor：

```text
function route(tool: ToolRegistry, ctx: ToolCallContext): Executor:
    match tool.executorType:
        case "general":  return generalExecutor   # 本地 JVM 内执行
        case "proxy":    return proxyExecutor      # gRPC 调用业务服务
        case "sandbox":  return sandboxExecutor    # Docker 沙箱执行
        default: throw INTERNAL
```

`executor_type` 与 `risk_level` 的约束矩阵：

| risk_level | 允许的 executor_type | 强制要求 |
|---|---|---|
| R1 | `general` / `proxy` | 无 |
| R2 | `proxy` / `sandbox` | 必须可回滚（`undo_action` 非空） |
| R3 | `sandbox`（强制） | 必须审批 + 沙箱 + 双人复核 |

### 3.7 Step6：隔离执行（三种模式）

#### 3.7.1 General Executor（通用执行器）

- **场景**：R1 只读、平台内置原子工具（如 `kb_retrieve`/`memory_recall`/`time_now`）
- **隔离方式**：JVM 内线程池隔离，按 `tool_id` 分组限流
- **超时控制**：虚拟线程 + `CompletableFuture.orTimeout(tool.timeoutMs)`
- **资源限制**：单工具并发上限（Sentinel 流控）、JVM 堆内存监控

```java
// 伪代码
public class GeneralExecutor implements ToolExecutor {
    private final Map<String, ToolHandler> handlerRegistry;
    private final RateLimiter rateLimiter;  // 按 toolId 限流

    @Override
    public ToolRawResult execute(ToolCallContext ctx) {
        rateLimiter.acquire(ctx.getToolId());
        ToolHandler handler = handlerRegistry.get(ctx.getToolId());
        return CompletableFuture
            .supplyAsync(() -> handler.handle(ctx.getInput()), virtualThreadExecutor)
            .orTimeout(ctx.getTool().getTimeoutMs(), TimeUnit.MILLISECONDS)
            .exceptionally(this::wrapTimeoutOrError)
            .join();
    }
}
```

#### 3.7.2 Proxy Executor（内网代理执行器）

- **场景**：R1/R2 调用业务微服务（订单/库存/用户中心等）
- **隔离方式**：gRPC Stub 池 + 熔断器（Sentinel），按 `endpoint` 维度熔断
- **超时控制**：gRPC deadline 透传 `tool.timeoutMs`
- **失败处理**：依据 `error_codes.retryable` 字段决定重试（接口级最多 3 次，指数退避）

```java
public class ProxyExecutor implements ToolExecutor {
    private final GrpcStubPool stubPool;
    private final CircuitBreakerRegistry breakerRegistry;

    @Override
    public ToolRawResult execute(ToolCallContext ctx) {
        String endpoint = ctx.getTool().getEndpoint();  // grpc://order-service/OrderService/QueryOrder
        CircuitBreaker breaker = breakerRegistry.circuitBreaker(endpoint);
        return breaker.executeSupplier(() ->
            stubPool.invoke(endpoint, ctx.getInputJson(), ctx.getTool().getTimeoutMs())
        );
    }
}
```

#### 3.7.3 Sandbox Executor（沙箱执行器）

- **场景**：R3 高危工具强制使用（写操作、系统级、不可回滚）
- **隔离方式**：Docker 容器隔离执行，详见 §6
- **超时控制**：容器级 OOM Kill + 超时 SIGKILL
- **资源限制**：CPU/内存/网络/文件系统全隔离

### 3.8 Step7：结果标准化清洗

详见 §8 结果标准化清洗。清洗后产出标准化 `ToolInvokeResponse.output_json`。

### 3.9 Step8：返回 Agent 上下文

**返回格式**：严格遵循 [API 规范](../02-api/api-specification.md#32-工具调用grpc仅内部-agent-runtime-调用) 的 `ToolInvokeResponse`：

```json
{
  "callId": "call_xxx",
  "status": "success",
  "outputJson": "{\"orders\":[...],\"total\":3}",
  "errorCode": "",
  "errorMsg": "",
  "durationMs": 1200,
  "costCent": 50,
  "tokenUsed": 320,
  "fromCache": false
}
```

**返回约束**：
- `outputJson` 必须通过 `output_schema` 校验，否则置 status=failed
- `tokenUsed` 由 `TokenCounter` 估算（清洗后输出 Token 数）
- `costCent` 包含工具调用成本 + 清洗成本
- 失败时 `outputJson` 为空，`errorCode`/`errorMsg` 必填

### 3.10 Step9：全量落盘审计

**双写机制**（强一致 + 最终一致）：

1. **强一致写**：`tool_call_log` 表（按月分表，见 [数据库 §0.3](../01-database/database-schema-design.md#03-分库分表策略shardingsphere)），调用返回前完成。
2. **最终一致写**：`audit_log` 表（risk-control 域），通过 RocketMQ `tool.call.audit` 异步上报。

```text
function audit(ctx, response):
    # 1. 强一致：写 tool_call_log
    toolCallLog.insert({
        callId: ctx.callId, taskId: ctx.taskId, stepNo: ctx.stepNo,
        agentId: ctx.agentId, toolId: ctx.toolId, toolVersion: ctx.toolVersion,
        input: desensitize(ctx.inputJson),       # 脱敏后落库
        output: truncate(response.outputJson),   # 截断后落库
        status: response.status, errorCode: response.errorCode,
        durationMs: response.durationMs, costCent: response.costCent,
        tokenUsed: response.tokenUsed, riskLevel: ctx.tool.riskLevel,
        approvedBy: ctx.approval?.approver, traceId: ctx.traceId
    })

    # 2. 异步：发送 RocketMQ tool.call.audit
    rocketMQ.send("tool.call.audit", {
        eventId, eventType: "tool.call.audit", traceId,
        payload: { callId, toolId, agentId, status, riskLevel, action: "tool.invoke" }
    })
```

脱敏规则引用 [数据库 §13.3](../01-database/database-schema-design.md#133-数据脱敏)：手机号/身份证/银行卡/API Key/Token 等正则替换。

---

## 4. 权限边界管控

### 4.1 工具风险三级分级

依据 PRD 第二节(二)3，工具按风险分 R1/R2/R3 三级，对应 [`tool_registry.risk_level`](../01-database/database-schema-design.md#41-tool_registry-工具注册表) 字段（1/2/3）。

| 维度 | R1 低危 | R2 中危 | R3 高危 |
|---|---|---|---|
| **副作用** | 只读公开数据、无副作用 | 只读内部数据、轻度可回滚副作用 | 写操作、系统级、不可回滚副作用 |
| **executor_type** | `general` / `proxy` | `proxy` / `sandbox` | `sandbox`（强制） |
| **审批要求** | 无 | 工具注册时管理员审批 | 每次调用前需 `tool_approval` 审批 + 限时授权 + 双人复核 |
| **Agent 绑定** | 全量 Agent 默认开放 | 按业务线申请绑定指定 Agent | 单 Agent 单独绑定 + 审批 |
| **undo_action** | 可空 | 必填（写操作时） | 必填 |
| **审计粒度** | 全量落 `tool_call_log` | 全量落 + 越权实时告警 | 全量落 + 双人复核留痕 + 高危参数白名单 |
| **典型工具** | 天气查询、知识检索、时间获取 | 查询订单、查询用户信息、修改地址 | 删除用户、转账、系统配置变更、数据导出 |

### 4.2 细粒度授权模型（RBAC + ABAC）

依据 PRD「最小权限原则」，授权模型落地于 [`permission_policy`](../01-database/database-schema-design.md#92-permission_policy-表rbacabac) 表。

#### 4.2.1 RBAC 静态授权

```text
角色(role) ──授权──► 资源(tool) ──操作(action)
```

- `subject_type=role`：角色（如 `cs_agent`/`ops_admin`/`finance_agent`）
- `resource_type=tool`：工具资源
- `action`：`read`（查询工具定义）/ `execute`（调用）/ `write`（注册/修改工具）

#### 4.2.2 ABAC 动态条件

`permission_policy.conditions` JSON 字段承载 ABAC 条件，支持以下属性维度：

```json
{
  "timeWindow": {"start": "09:00", "end": "18:00", "timezone": "Asia/Shanghai"},
  "ipCidr": ["10.0.0.0/8", "192.168.0.0/16"],
  "inputConstraints": {
    "amount": {"max": 100000},
    "targetUser": {"mustBeSelf": true}
  },
  "tenantScope": ["tenant_1001", "tenant_1002"],
  "sceneTags": ["finance", "production"]
}
```

| ABAC 维度 | 字段 | 校验时机 |
|---|---|---|
| 时间窗口 | `timeWindow` | 前置校验第 4 步 |
| IP 白名单 | `ipCidr` | 网关层（来自 `X-Forwarded-For`） |
| 入参约束 | `inputConstraints` | 前置校验第 6 步（高危参数白名单） |
| 租户隔离 | `tenantScope` | 网关层（来自 JWT `tenantId`） |
| 场景标签 | `sceneTags` | 前置校验第 4 步 |

#### 4.2.3 临时权限

依据 [`permission_policy.expire_at`](../01-database/database-schema-design.md#92-permission_policy-表rbacabac) 字段，支持限时授权自动过期。临时权限通过 [API §10.1](../02-api/api-specification.md#101-权限策略管理) `POST /api/v1/permissions/temporary` 授予。

### 4.3 核心管控机制

依据 PRD 第二节(二)3「核心管控机制」，四大机制落地如下：

#### 4.3.1 高危参数白名单校验

针对 R2/R3 工具，对入参中的高危字段做白名单校验。白名单规则配置于 `tool_registry.input_schema` 的扩展字段 `x-param-whitelist`：

```json
{
  "properties": {
    "targetAccount": {
      "type": "string",
      "x-param-whitelist": {
        "source": "permission_policy.conditions.allowedAccounts",
        "matchMode": "exact",
        "denyOnError": true
      }
    }
  }
}
```

校验逻辑：

```text
function paramWhitelistChecker.check(tool, inputJson):
    whitelistFields = extractFromSchema(tool.inputSchema, "x-param-whitelist")
    for field, rule in whitelistFields:
        actualValue = inputJson[field]
        allowedValues = resolveFromPermissionPolicy(rule.source)
        if actualValue not in allowedValues:
            if rule.denyOnError:
                return reject(PARAM_WHITELIST_DENIED, field)
            else:
                auditLog.warn(field, actualValue)  # 仅告警不拦截
```

#### 4.3.2 写操作二次确认

R3 写操作工具调用前，强制人工二次确认：

```text
function writeOperationConfirm(tool, ctx):
    if tool.riskLevel != R3: return pass()
    if tool.undoAction == null: return reject(UNDO_ACTION_REQUIRED)

    # 1. 生成确认单
    confirmation = {
        callId: ctx.callId,
        toolId: ctx.toolId,
        inputSnapshot: ctx.inputJson,
        expectedEffect: tool.description,
        undoAction: tool.undoAction,
        expireAt: now() + 5min
    }

    # 2. 推送给审批人（IM/Web 控制台）
    notifyApprover(ctx.approval.approver, confirmation)

    # 3. 阻塞等待确认（最长 5 分钟）
    result = await confirmationQueue.poll(ctx.callId, timeout=5min)
    if result == CONFIRMED: return pass()
    if result == REJECTED: return reject(USER_REJECTED)
    return reject(CONFIRM_TIMEOUT)
```

#### 4.3.3 全量调用审计留痕

所有工具调用（含 blocked）100% 落 `tool_call_log` + `audit_log`，详见 §3.10。

#### 4.3.4 越权实时拦截告警

`risk-control` 拦截器在权限校验失败时：

1. 实时返回 `FORBIDDEN`，阻止调用继续
2. 写 `audit_log`（result=deny）
3. 触发越权告警事件 `governance.drift.alert`（依据 [API §11](../02-api/api-specification.md#11-异步事件规范rocketmq-topics)）
4. 同一 Agent 1 分钟内连续 3 次越权 → 自动熔断该 Agent 1 小时

### 4.4 R3 高危工具审批流程

落地于 [`tool_approval`](../01-database/database-schema-design.md#44-tool_approval-高危工具审批表) 表，状态机：

```
  提交申请         审批人通过           过期
  ──────► pending ──────► approved ──────► expired
              │
              │ 驳回
              ▼
           rejected
```

**审批流程**：

```text
function toolApprovalService.submit(ctx):
    # 1. 校验是否已有有效审批
    existing = findValid(ctx.toolId, ctx.taskId, ctx.inputSnapshot)
    if existing != null and existing.status == APPROVED and existing.expireAt > now():
        return existing  # 复用

    # 2. 创建审批单
    approval = toolApproval.insert({
        approvalId, toolId: ctx.toolId, taskId: ctx.taskId, agentId: ctx.agentId,
        inputSnapshot: ctx.inputJson, applicant: ctx.userId,
        approver: resolveApprover(ctx.toolId),  # 按业务线路由审批人
        status: PENDING, expireAt: now() + 24h,
        reason: ctx.reason
    })

    # 3. 通知审批人
    notify(approval.approver, approval)

    # 4. 阻塞等待（最长 24h，实际限时授权通常 1h）
    result = await approvalQueue.poll(approval.approvalId, timeout=24h)
    return result

function toolApprovalService.approve(approvalId, approver, comment, newExpireAt):
    # 双人复核：R3 必须两个不同审批人同时通过
    approval = findById(approvalId)
    if approval.approver != approver: throw FORBIDDEN
    approval.approver = approver
    approval.comment = comment
    approval.status = APPROVED
    approval.expireAt = newExpireAt  # 限时授权，通常 1h
    update(approval)
```

**限时授权**：审批通过后 `expire_at` 字段限定有效窗口（默认 1 小时），过期后该 `inputSnapshot` 的调用需重新审批。

---

## 5. Token 与成本管控

依据 PRD 第二节(二)4，成本管控从 Prompt 端、返回端、调用次数、全局熔断四个维度落地。

### 5.1 成本管控总体框架

```
┌─────────────────────────────────────────────────────────────────┐
│                    单任务成本构成                                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Prompt 端成本 │  │ 返回端成本    │  │ 调用次数 × 单次成本  │  │
│  │ (工具Schema   │  │ (工具输出     │  │ (含重试)             │  │
│  │  注入Prompt)  │  │  占用Token)  │  │                      │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│                          ▼                                       │
│              ┌────────────────────────┐                          │
│              │ 全局成本熔断 (tool_quota)│                          │
│              │  工具/业务线/单任务三级  │                          │
│              └────────────────────────┘                          │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 Prompt 端降本

#### 5.2.1 语义召回工具（按需注入）

依据 PRD「基于语义按需召回工具注入上下文」，仅将与当前任务相关的工具 Schema 注入 Prompt，而非全量工具列表。

**实现**：通过 `ToolGateway.Recall`（§7）召回 Top-K（默认 K=5）工具，仅这 K 个工具的 `input_schema` 注入 Prompt。

**降本效果**：假设平台共注册 200 个工具，单任务平均召回 5 个，Prompt 中工具 Schema 部分降本 **97.5%**。

#### 5.2.2 Schema 最小化

注入 Prompt 的工具 Schema 仅保留模型生成调用指令所需的最小字段：

| 保留字段 | 裁剪字段 |
|---|---|
| `name` | `toolId`、`version`、`displayName` |
| `description`（截断至 200 字符） | `avgCostCent`、`avgDurationMs` |
| `inputSchema`（仅 properties + required） | `outputSchema`、`errorCodes`、`undoAction` |
| `sceneTags`（仅标签名） | `executorType`、`endpoint`、`timeoutMs` |

#### 5.2.3 Prompt 缓存引用

依据 [`tool_registry.prompt_cache_key`](../01-database/database-schema-design.md#41-tool_registry-工具注册表) 字段，对稳定的工具 Schema 部分启用 Prompt 缓存：

- 工具 Schema 注入 Prompt 时，按 `prompt_cache_key` 标记为可缓存前缀
- 模型网关依据 `enable_prompt_cache=true`（见 [model.proto](../02-api/api-specification.md#5-模型网关-apigrpc内部) `ChatRequest`）启用缓存
- 缓存命中时 `model_usage_log.cache_hit=1`，输入 Token 按缓存折扣计费

### 5.3 返回端降本

#### 5.3.1 标准化裁剪冗余元数据

`ResultCleaner`（§8）在清洗阶段裁剪工具输出的冗余字段：

```text
function trimMetadata(rawOutput, tool):
    # 1. 仅保留 outputSchema 中定义的字段
    trimmed = jsonSchemaFilter(rawOutput, tool.outputSchema)

    # 2. 裁剪字段级元数据（_meta/_debug/_trace 等内部字段）
    trimmed = removeInternalFields(trimmed, prefix=["_", "meta_", "debug_"])

    # 3. 长文本字段截断（单字段上限 4K Token）
    for field in trimmed:
        if isLongText(field) and tokenCount(field.value) > 4000:
            field.value = summarize(field.value, maxTokens=4000)

    return trimmed
```

#### 5.3.2 单工具 Token 上限

每个工具在 `tool_registry` 注册时声明 `max_output_token`（扩展字段，存于 `undo_action` 同级 JSON），清洗后超过上限的处理：

| 超限比例 | 处理策略 |
|---|---|
| ≤ 100% | 直接返回 |
| 100%~300% | 触发摘要压缩（保留结构 + 摘要长文本） |
| > 300% | 触发分页增量返回（仅返回首页 + 总数 + 分页游标） |

#### 5.3.3 超长摘要

对超长输出（如代码、日志、长文档）调用轻量模型生成摘要：

```text
function summarizeIfNeeded(output, maxTokens):
    if tokenCount(output) <= maxTokens: return output
    # 调用 model-gateway 轻量模型（light tier）
    summary = modelGateway.chat(
        scene="summary", tier="light",
        messages=[{role:"user", content:"请将以下工具输出压缩为不超过"+maxTokens+"Token的摘要:\n"+output}]
    )
    return { "_summarized": true, "_original_token": tokenCount(output), "summary": summary }
```

#### 5.3.4 分页增量返回

对天然分页的工具输出（如列表查询），强制启用分页：

```json
{
  "_paginated": true,
  "page": 1,
  "size": 20,
  "total": 135,
  "items": [...20条...],
  "_nextCursor": "base64encodedcursor"
}
```

Agent 需要下一页时，通过 `Invoke` 携带 `_nextCursor` 增量获取。

### 5.4 调用次数管控

#### 5.4.1 单任务最大次数熔断

依据 PRD「单任务设置最大工具调用次数熔断」，落地于 `tool_quota` 表 `subject_type=task`：

```text
function checkTaskQuota(ctx):
    quota = toolQuota.find(subjectType="task", subjectId=ctx.taskId, toolId=null)
    if quota.dailyUsed >= quota.dailyLimit:
        return reject(TASK_TOOL_CALL_LIMIT_EXCEEDED)
    # 同时校验单工具在单任务内的调用次数
    toolQuota = toolQuota.find(subjectType="task", subjectId=ctx.taskId, toolId=ctx.toolId)
    if toolQuota != null and toolQuota.dailyUsed >= toolQuota.dailyLimit:
        return reject(TASK_TOOL_CALL_LIMIT_EXCEEDED)
    return pass()
```

默认阈值：单任务总调用 ≤ 50 次；单工具单任务内 ≤ 10 次（防循环调用）。

#### 5.4.2 相同入参结果缓存

```text
function toolResultCache.get(toolId, inputHash):
    cacheKey = "tool:cache:" + toolId + ":" + inputHash
    cached = redis.get(cacheKey)
    if cached != null: return deserialize(cached)
    return null

function toolResultCache.put(toolId, inputHash, output, ttl):
    # 仅 R1 只读工具启用缓存；R2/R3 写操作禁用
    if tool.riskLevel != R1: return
    if tool.executorType == "sandbox": return
    cacheKey = "tool:cache:" + toolId + ":" + inputHash
    redis.setex(cacheKey, ttl, serialize(output))  # TTL 默认 5 分钟
```

`inputHash` 计算规则：对 `inputJson` 字段排序后 SHA-256，取前 16 字节。

#### 5.4.3 低成本工具优先

同功能工具（通过 `scene_tags` 聚类）存在多个时，优先调用 `avg_cost_cent` 低的：

```text
function preferLowCost(candidates: List<ToolCandidate>):
    # 按 sceneTags 聚类
    grouped = groupBy(candidates, c -> c.sceneTags)
    for group in grouped:
        # 同组内按 avg_cost_cent 升序排序
        group.sort(Comparator.comparing(c -> c.avgCostCent))
    return flatten(grouped)
```

召回阶段（§7）在重排时应用此规则。

#### 5.4.4 失败重试限次

依据 PRD「失败重试限次」+ [API §12](../02-api/api-specification.md#12-限流与熔断规范) 熔断规则：

| 重试层级 | 最大次数 | 退避策略 | 触发条件 |
|---|---|---|---|
| 接口级（Executor 内） | 3 | 指数退避（1s/2s/4s） | 仅 `error_codes.retryable=true` 的瞬时异常 |
| 单步执行级（agent-runtime） | 2 | 附错误说明重跑 | 模型决策重试 |
| 子任务级（task-orchestrator） | 2 | 重置上下文 | 见任务编排详设 |
| 单工具 1 分钟失败率 > 50% | — | 熔断 60s | Sentinel 熔断器 |

**禁止重试场景**：业务异常（参数错误、权限拒绝）、合规问题（越权拦截）、R3 写操作已部分执行。

### 5.5 全局成本熔断

依据 PRD「按工具、业务线、单任务分别设置成本阈值」，落地于 [`tool_quota`](../01-database/database-schema-design.md#43-tool_quota-工具配额表) 表。

#### 5.5.1 三级阈值体系

| 级别 | subject_type | subject_id 含义 | tool_id | 阈值字段 | 默认值 |
|---|---|---|---|---|---|
| 工具级 | `tenant` | 租户 ID | 具体 toolId | `daily_limit` / `cost_limit_cent` | 按工具注册时配置 |
| 业务线级 | `tenant` | 租户 ID | NULL（全工具） | `daily_limit` / `cost_limit_cent` | 租户级配额 |
| 单任务级 | `task` | 任务 ID | NULL / 具体 toolId | `daily_limit` / `cost_limit_cent` | 任务级配额（来自 `task_instance.cost_limit_cent`） |

#### 5.5.2 配额扣减逻辑

```text
function toolQuotaManager.tryAcquire(ctx, tool):
    # 1. 工具级配额（租户 + 工具）
    if not tryDecrement(subjectType="tenant", subjectId=ctx.tenantId,
                         toolId=ctx.toolId, count=1, costCent=tool.avgCostCent):
        fireAlert(TOOL_QUOTA_EXCEEDED, level=tool)
        return false

    # 2. 业务线级配额（租户 + 全工具）
    if not tryDecrement(subjectType="tenant", subjectId=ctx.tenantId,
                         toolId=null, count=1, costCent=tool.avgCostCent):
        fireAlert(TENANT_QUOTA_EXCEEDED, level=business)
        return false

    # 3. 单任务级配额
    if not tryDecrement(subjectType="task", subjectId=ctx.taskId,
                         toolId=null, count=1, costCent=tool.avgCostCent):
        fireAlert(TASK_BUDGET_EXCEEDED, level=task)
        return false

    return true

function tryDecrement(subjectType, subjectId, toolId, count, costCent):
    # Redis 原子扣减 + MySQL 异步对账
    redisKey = "quota:" + subjectType + ":" + subjectId + ":" + (toolId ?: "*")
    remaining = redis.decrBy(redisKey + ":count", count)
    costRemaining = redis.decrBy(redisKey + ":cost", costCent)
    if remaining < 0 or costRemaining < 0:
        # 回滚
        redis.incrBy(redisKey + ":count", count)
        redis.incrBy(redisKey + ":cost", costCent)
        return false
    return true
```

#### 5.5.3 分级预警与自动拦截

| 阈值比例 | 行为 |
|---|---|
| 80% | 预警，发送 `governance.drift.alert` 事件 |
| 95% | 限流，仅放行 R1 工具 |
| 100% | 熔断，拒绝所有工具调用，返回 `COST_BUDGET_EXCEEDED` |

#### 5.5.4 配额重置

`tool_quota.reset_at` 字段记录下次重置时间，XXL-Job 定时任务每日 00:00 重置 `daily_used`/`cost_used_cent`。

### 5.6 成本管控策略汇总

| 维度 | 策略 | 落地表/字段 | 触发时机 |
|---|---|---|---|
| Prompt 端 | 语义召回 Top-K 工具 | `tool_index` Milvus | 召回阶段 |
| Prompt 端 | Schema 最小化注入 | 网关层裁剪 | 注入 Prompt 前 |
| Prompt 端 | Prompt 缓存 | `tool_registry.prompt_cache_key` | 模型调用时 |
| 返回端 | 元数据裁剪 | `ResultCleaner` | 清洗阶段 |
| 返回端 | 单工具 Token 上限 | `tool_registry.max_output_token` | 清洗阶段 |
| 返回端 | 超长摘要 | model-gateway（light tier） | 清洗阶段 |
| 返回端 | 分页增量返回 | 网关层分页 | 清洗阶段 |
| 调用次数 | 单任务最大次数熔断 | `tool_quota`（subject_type=task） | 前置校验 |
| 调用次数 | 相同入参结果缓存 | Redis（R1 工具，TTL 5min） | 前置校验短路 |
| 调用次数 | 低成本工具优先 | 召回重排 | 召回阶段 |
| 调用次数 | 失败重试限次 | Executor 内重试 | 执行阶段 |
| 全局熔断 | 工具级阈值 | `tool_quota`（tenant + toolId） | 前置校验 |
| 全局熔断 | 业务线级阈值 | `tool_quota`（tenant + NULL） | 前置校验 |
| 全局熔断 | 单任务级阈值 | `tool_quota`（task + NULL） | 前置校验 |

---

## 6. 沙箱执行器设计

### 6.1 沙箱架构与适用场景

沙箱执行器（`SandboxExecutor`）专为 R3 高危工具设计，依据 [`tool_registry.executor_type=sandbox`](../01-database/database-schema-design.md#41-tool_registry-工具注册表) 强制路由。

**设计目标**：
- 完全隔离：执行环境与宿主机进程/文件系统/网络隔离
- 资源限制：CPU/内存/网络/文件系统配额硬限制
- 超时强制：超时 SIGKILL，不留僵尸进程
- 可审计：沙箱内所有系统调用可追溯

**适用场景**：
- R3 写操作工具（删用户、转账、系统配置）
- 不可信代码执行工具（用户自定义脚本）
- 涉及敏感数据导出的工具

### 6.2 Docker 沙箱隔离执行

#### 6.2.1 沙箱镜像

预构建精简基础镜像 `agentplatform/sandbox-base:1.0`：

```dockerfile
FROM eclipse-temurin:17-jre-alpine
# 仅安装最小依赖
RUN apk add --no-cache curl jq
# 非 root 用户运行
RUN addgroup -S sandbox && adduser -S sandbox -G sandbox
USER sandbox
WORKDIR /sandbox
# 工具执行入口
COPY tool-runner.jar /opt/tool-runner.jar
ENTRYPOINT ["java", "-jar", "/opt/tool-runner.jar"]
```

#### 6.2.2 沙箱池

预启动池化容器，避免冷启动延迟：

```text
const SANDBOX_POOL_SIZE = 20
const SANDBOX_IMAGE = "agentplatform/sandbox-base:1.0"

function sandboxPool.borrow(toolId, inputJson, constraints):
    container = pool.poll(timeout=2s)
    if container == null:
        container = docker.run(SANDBOX_IMAGE, detach=true)
    # 注入工具代码与入参（通过 stdin 或临时卷）
    docker.exec(container, ["java", "-jar", "/opt/tool-runner.jar",
                             "--tool-id=" + toolId,
                             "--input=" + base64(inputJson)])
    return container
```

### 6.3 资源限制

依据 Docker `--cpu`/`--memory`/`--network` 参数硬限制：

| 资源 | 限制参数 | 默认值 | 超限行为 |
|---|---|---|---|
| CPU | `--cpus=1` `--cpu-quota=100000` | 1 核 | 节流（不强制终止） |
| 内存 | `--memory=512m` `--memory-swap=512m` | 512MB | OOM Kill（exit code 137） |
| 网络 | `--network=tool-internal` | 仅允许访问白名单服务 | — |
| 文件系统 | `--read-only` + tmpfs `/tmp:64m` | 只读根 + 64MB tmpfs | 写入失败 |
| 进程数 | `--pids-limit=50` | 50 进程 | fork 失败 |
| 磁盘 IO | `--device-read-bps=/dev/sda:10mb` | 10MB/s | 节流 |

**网络白名单**：通过自定义 Docker 网络 `tool-internal` + iptables 规则，仅允许访问 `proxy` 类型工具的 `endpoint` 解析出的内网服务，禁止访问公网。

### 6.4 超时强制终止

```text
function sandboxExecutor.execute(ctx):
    container = sandboxPool.borrow(ctx.toolId, ctx.inputJson, ctx.constraints)
    deadline = now() + ctx.tool.timeoutMs

    try:
        result = docker.waitContainer(container, timeout=ctx.tool.timeoutMs)
        if result.exitCode == 0:
            return parseOutput(result.stdout)
        else if result.exitCode == 137:
            return ToolRawResult(timeout=true, error="OOM_KILLED")
        else if result.exitCode == 143:
            return ToolRawResult(timeout=true, error="TIMEOUT_SIGTERM")
        else:
            return ToolRawResult(failed=true, error=result.stderr)
    catch TimeoutException:
        docker.kill(container, signal=SIGKILL)  # 强制终止
        return ToolRawResult(timeout=true, error="TIMEOUT_SIGKILL")
    finally:
        docker.rm(container)  # 销毁容器，不复用
```

### 6.5 沙箱生命周期管理

| 阶段 | 操作 | 超时 |
|---|---|---|
| 借用 | 从池中获取或新建容器 | 2s |
| 执行 | 容器内运行工具 | `tool.timeoutMs`（默认 30s） |
| 终止 | 超时 SIGKILL | 即时 |
| 销毁 | `docker rm`（一次性使用） | 1s |
| 池补充 | 后台预启动新容器维持池大小 | 异步 |

**安全约束**：
- 每个容器仅执行一次工具调用，不复用（避免状态泄漏）
- 容器销毁前清空 tmpfs
- 沙箱宿主机定期轮换（防逃逸）

---

## 7. 工具语义召回

### 7.1 Milvus Collection 设计

依据 [数据库 §10.1](../01-database/database-schema-design.md#101-collection-规划)，工具语义索引存于 Milvus `tool_index` Collection：

| 字段 | 类型 | 说明 |
|---|---|---|
| `vector_id` | VarChar(64) | 主键，等于 `tool_id + ":" + version` |
| `embedding` | FloatVector(1024) | 工具描述向量化 |
| `tool_id` | VarChar(32) | 工具 ID |
| `tool_name` | VarChar(64) | 工具名 |
| `scene_tags` | Array<VarChar> | 场景标签（Partition Key） |
| `risk_level` | Int8 | 风险等级 |
| `status` | Int8 | 状态（仅 status=2 启用才索引） |
| `avg_cost_cent` | Int64 | 平均成本（用于低成本优先） |

索引：HNSW (M=16, efConstruction=256)，Partition Key = `scene_tags`。

### 7.2 向量召回 + 场景标签过滤

```text
function toolRecaller.recall(query, sceneTags, topK=30):
    # 1. 向量化查询
    queryEmbedding = embeddingModel.encode(query, dim=1024)

    # 2. Milvus 向量召回 + 标签过滤
    expr = "status == 2"
    if sceneTags.notEmpty():
        expr += " and scene_tags contains any of " + sceneTags

    candidates = milvus.search(
        collection="tool_index",
        vector=queryEmbedding,
        top_k=topK,           # 默认 30（见数据库 §10.3）
        ef=64,                # 搜索 ef
        expr=expr,
        partition_keys=sceneTags
    )

    # 3. 重排：相关性 + 低成本优先 + 风险等级偏好
    reranked = candidates.stream()
        .map(c -> {
            relevanceScore = c.score                         # 向量相似度
            costScore = 1 - normalize(c.avgCostCent)         # 成本越低分越高
            riskScore = (c.riskLevel == R1) ? 1.0 :
                        (c.riskLevel == R2) ? 0.7 : 0.4      # R1 优先
            finalScore = 0.6 * relevanceScore + 0.3 * costScore + 0.1 * riskScore
            return ToolCandidate(c, finalScore)
        })
        .sorted(descending by finalScore)
        .limit(5)                                              # 最终返回 Top-5
        .collect()

    return reranked
```

重排权重依据 PRD 成本管控要求，相关性为主，成本与风险为辅。

### 7.3 与模型生成调用指令对齐校验

模型生成调用指令后，网关在 §3.5 前置校验前增加**召回-指令对齐校验**：

```text
function alignCheck(recalledTools, modelInstruction):
    # 1. 模型指定的 toolId 必须在召回结果中（防止幻觉调用未召回工具）
    if modelInstruction.toolId not in recalledTools.map(t -> t.toolId):
        # 例外：Agent 显式绑定该工具且 risk_level == R1 时放行
        tool = toolRegistry.findById(modelInstruction.toolId)
        if tool.riskLevel == R1 and agentToolBinder.isBound(ctx.agentId, tool.toolId):
            auditLog.warn("tool_not_recalled_but_allowed", tool.toolId)
            return pass()
        return reject(TOOL_NOT_RECALLED, "模型调用了未召回的工具，疑似幻觉")

    # 2. 校验模型入参与工具 inputSchema 对齐（前置校验第 2 步会再做 JSON Schema 校验）
    return pass()
```

此机制是 [PRD 第四节工具幻觉治理](../../PRD.md) 第三层「工具调用硬管控」的落地：网关前置拦截虚构工具。

---

## 8. 结果标准化清洗

### 8.1 清洗流程

`ResultCleaner` 在 §3.7 隔离执行后、§3.8 返回前执行：

```
原始输出 ──► Schema 校验 ──► 冗余字段裁剪 ──► Token 截断 ──► 错误码归一化 ──► 标准化输出
```

### 8.2 Schema 校验

```text
function validateSchema(rawOutput, tool):
    errors = jsonSchemaValidator.validate(rawOutput, tool.outputSchema)
    if errors.notEmpty():
        # Schema 不匹配时，不直接失败，尝试容错修复
        repaired = tryRepair(rawOutput, tool.outputSchema, errors)
        if repaired == null:
            return ResultCleanResult(failed=true,
                errorCode="OUTPUT_SCHEMA_INVALID", errors=errors)
        rawOutput = repaired
    return ResultCleanResult(output=rawOutput)
```

容错修复策略：
- 多余字段：自动裁剪
- 缺失必填字段：填默认值（如有）或标记失败
- 类型不符：尝试隐式转换（string→int 等安全转换）

### 8.3 Token 截断

依据 §5.3.2 单工具 Token 上限：

```text
function tokenTruncate(output, tool):
    maxTokens = tool.maxOutputToken ?: 8000  # 默认 8K Token
    currentTokens = tokenCounter.count(output)

    if currentTokens <= maxTokens:
        return output

    # 1. 尝试裁剪长文本字段
    output = trimLongFields(output, maxFieldTokens=4000)
    if tokenCounter.count(output) <= maxTokens:
        return output

    # 2. 触发摘要压缩
    if currentTokens <= maxTokens * 3:
        return summarize(output, maxTokens)

    # 3. 触发分页增量返回
    return paginate(output, pageSize=20)
```

### 8.4 错误码归一化

不同执行器返回的错误码格式不一，统一归一化为 `tool_registry.error_codes` 中定义的标准码：

| 执行器原始错误 | 归一化后错误码 | 归一化规则 |
|---|---|---|
| gRPC `DEADLINE_EXCEEDED` | `UPSTREAM_TIMEOUT` | retryable=true |
| gRPC `UNAVAILABLE` | `UPSTREAM_UNAVAILABLE` | retryable=true |
| gRPC `PERMISSION_DENIED` | `FORBIDDEN` | retryable=false |
| HTTP 429 | `RATE_LIMITED` | retryable=true |
| HTTP 5xx | `UPSTREAM_ERROR` | retryable=true |
| HTTP 4xx（除 429） | `BUSINESS_ERROR` | retryable=false |
| OOM Killed | `SANDBOX_OOM` | retryable=false |
| 容器超时 | `SANDBOX_TIMEOUT` | retryable=false |

归一化逻辑：

```text
function normalizeError(rawError, executorType, tool):
    if executorType == "proxy":
        return mapGrpcStatus(rawError.code, tool.errorCodes)
    elif executorType == "sandbox":
        return mapContainerExitCode(rawError.exitCode, tool.errorCodes)
    else:  # general
        return mapJvmException(rawError.exception, tool.errorCodes)

function mapGrpcStatus(grpcCode, errorCodes):
    mapping = {
        "DEADLINE_EXCEEDED": "UPSTREAM_TIMEOUT",
        "UNAVAILABLE": "UPSTREAM_UNAVAILABLE",
        "PERMISSION_DENIED": "FORBIDDEN",
        "RESOURCE_EXHAUSTED": "RATE_LIMITED"
    }
    normalized = mapping[grpcCode] ?: "UPSTREAM_ERROR"
    # 校验工具是否声明了该错误码
    if errorCodes.any(e -> e.code == normalized):
        return errorCodes.find(e -> e.code == normalized)
    else:
        return {code: normalized, message: "未声明的上游错误", retryable: true}
```

---

## 9. 核心类设计

### 9.1 包结构

```
agent-tool-engine/
└── src/main/java/com/agentplatform/tool/
    ├── registry/         # 工具注册与版本管理
    ├── recall/           # 工具语义召回
    ├── gateway/          # 调用网关入口
    ├── validator/        # 前置校验
    ├── executor/         # 隔离执行器
    │   ├── general/
    │   ├── proxy/
    │   └── sandbox/
    ├── cleaner/          # 结果标准化清洗
    ├── quota/            # 配额与成本熔断
    ├── audit/            # 审计与落盘
    └── config/           # 配置
```

### 9.2 ToolRegistry（工具注册与版本管理）

```java
package com.agentplatform.tool.registry;

import com.agentplatform.common.model.ToolRegistry;
import java.util.List;
import java.util.Optional;

/**
 * 工具注册表读写仓储
 */
public interface ToolRegistryRepository {

    /** 注册新工具（status=草稿） */
    ToolRegistry save(ToolRegistry entity);

    /** 按 toolId + version 精确查询 */
    Optional<ToolRegistry> findById(String toolId, int version);

    /** 查询启用中的最新版本（status=2） */
    Optional<ToolRegistry> findLatestEnabled(String toolId);

    /** 按场景标签 + 风险等级分页查询 */
    List<ToolRegistry> query(ToolQueryCondition condition, int page, int size);

    /** 状态流转：草稿→启用 / 启用→下线 */
    boolean updateStatus(String toolId, int version, int targetStatus, String operator);

    /** Schema 兼容性校验（旧 inputSchema 能否通过新 inputSchema） */
    SchemaCompatResult validateSchemaCompat(String toolId, int oldVersion, int newVersion);
}

/**
 * 工具注册应用服务（REST API 入口）
 */
public class ToolRegistryService {

    private final ToolRegistryRepository repository;
    private final RiskControlClient riskControlClient;
    private final ToolApprovalService approvalService;

    /** POST /api/v1/tools */
    public ToolRegisterResponse register(ToolRegisterRequest request);

    /** PUT /api/v1/tools/{toolId}（创建新版本） */
    public ToolRegisterResponse updateVersion(String toolId, ToolRegisterRequest request);

    /** POST /api/v1/tools/{toolId}/approve（R3 审批提交） */
    public ApprovalSubmitResponse submitApproval(String toolId, ApprovalRequest request);

    /** 状态流转 */
    public void publish(String toolId, int version);      // 草稿→启用
    public void offline(String toolId, int version);      // 启用→下线
}
```

### 9.3 ToolGateway（调用网关）

```java
package com.agentplatform.tool.gateway;

import com.agentplatform.tool.executor.ToolRawResult;
import com.agentplatform.tool.model.ToolCallContext;

/**
 * gRPC 服务实现：agentplatform.tool.v1.ToolGateway
 * 接口契约见 docs/02-api/api-specification.md §3.2
 */
public class ToolGatewayService {

    private final ToolRecaller recaller;
    private final ToolValidator validator;
    private final ToolExecutorRouter router;
    private final ResultCleaner cleaner;
    private final ToolQuotaManager quotaManager;
    private final ToolCallAuditor auditor;

    /** rpc Invoke */
    public ToolInvokeResponse invoke(ToolInvokeRequest request) {
        ToolCallContext ctx = ToolCallContext.from(request);

        // Step4: 前置校验
        PreCheckResult preCheck = validator.preCheck(ctx);
        if (preCheck.isRejected()) {
            auditor.auditBlocked(ctx, preCheck);
            return preCheck.toResponse();
        }

        // Step5+6: 路由分发 + 隔离执行
        ToolRawResult raw = router.route(ctx).execute(ctx);

        // Step7: 结果标准化清洗
        CleanResult cleaned = cleaner.clean(raw, ctx.getTool());

        // Step8: 构造返回
        ToolInvokeResponse response = buildResponse(ctx, cleaned);

        // Step9: 全量落盘审计（异步）
        auditor.audit(ctx, response);

        // 配额扣减（实际在 preCheck 中已预扣，此处确认）
        quotaManager.confirmAcquire(ctx, response);

        return response;
    }

    /** rpc Recall */
    public ToolRecallResponse recall(ToolRecallRequest request);

    /** rpc ReportResult（结果回写，用于缓存与审计补充） */
    public ReportAck reportResult(ToolResultReport report);
}
```

### 9.4 ToolExecutor（执行器）

```java
package com.agentplatform.tool.executor;

import com.agentplatform.tool.model.ToolCallContext;

/**
 * 执行器接口：所有执行模式的统一抽象
 */
public interface ToolExecutor {

    /** 执行工具调用 */
    ToolRawResult execute(ToolCallContext ctx);

    /** 执行器类型标识 */
    String executorType();  // "general" / "proxy" / "sandbox"
}

/** 路由分发器 */
public class ToolExecutorRouter {

    private final Map<String, ToolExecutor> executors;  // key=executorType

    public ToolExecutor route(ToolCallContext ctx) {
        String type = ctx.getTool().getExecutorType();
        ToolExecutor executor = executors.get(type);
        if (executor == null) {
            throw new ToolEngineException("UNSUPPORTED_EXECUTOR", type);
        }
        return executor;
    }
}

/** 通用执行器（R1 JVM 内执行） */
public class GeneralExecutor implements ToolExecutor {
    private final Map<String, ToolHandler> handlerRegistry;
    private final RateLimiter rateLimiter;

    @Override
    public ToolRawResult execute(ToolCallContext ctx) { /* 见 §3.7.1 */ }

    @Override public String executorType() { return "general"; }
}

/** 内网代理执行器（R1/R2 gRPC 调用业务服务） */
public class ProxyExecutor implements ToolExecutor {
    private final GrpcStubPool stubPool;
    private final CircuitBreakerRegistry breakerRegistry;
    private final RetryPolicy retryPolicy;

    @Override
    public ToolRawResult execute(ToolCallContext ctx) { /* 见 §3.7.2 */ }

    @Override public String executorType() { return "proxy"; }
}

/** 沙箱执行器（R3 Docker 隔离） */
public class SandboxExecutor implements ToolExecutor {
    private final DockerClient dockerClient;
    private final SandboxPool sandboxPool;
    private final ResourceQuota defaultQuota;  // CPU/Memory/Network/Pids

    @Override
    public ToolRawResult execute(ToolCallContext ctx) { /* 见 §6.4 */ }

    @Override public String executorType() { return "sandbox"; }
}
```

### 9.5 ToolRecaller（工具语义召回）

```java
package com.agentplatform.tool.recall;

import com.agentplatform.tool.model.ToolCandidate;
import java.util.List;

/**
 * 工具语义召回器
 */
public class ToolRecaller {

    private final MilvusClient milvusClient;
    private final EmbeddingModel embeddingModel;  // 1024 维
    private final ToolRegistryRepository registryRepo;

    /**
     * 工具语义召回
     * @param query 自然语言查询
     * @param sceneTags 场景标签过滤（Partition Key）
     * @param topK 召回数量，默认 30
     * @return 重排后的 Top-5 候选
     */
    public List<ToolCandidate> recall(String query, List<String> sceneTags, int topK);

    /** 召回结果与模型调用指令对齐校验（防幻觉） */
    public AlignResult alignCheck(List<ToolCandidate> recalled, String modelToolId);
}
```

### 9.6 ToolQuotaManager（配额与成本熔断）

```java
package com.agentplatform.tool.quota;

import com.agentplatform.tool.model.ToolCallContext;
import com.agentplatform.tool.model.ToolRegistry;

/**
 * 配额与成本熔断管理器
 * 落地表：tool_quota
 */
public class ToolQuotaManager {

    private final RedisClient redisClient;  // 原子扣减
    private final ToolQuotaRepository repository;

    /**
     * 预扣配额（前置校验阶段调用）
     * @return true=通过，false=熔断
     */
    public boolean tryAcquire(ToolCallContext ctx, ToolRegistry tool);

    /** 确认扣减（调用完成后按实际成本对账） */
    public void confirmAcquire(ToolCallContext ctx, ToolInvokeResponse response);

    /** 回滚预扣（调用失败时） */
    public void rollbackAcquire(ToolCallContext ctx);

    /** 查询剩余配额 */
    public QuotaStatus queryRemain(SubjectType subjectType, String subjectId, String toolId);

    /** 配额预警检查（80% 预警 / 95% 限流 / 100% 熔断） */
    public QuotaAlert checkAlert(SubjectType subjectType, String subjectId);
}

/** 配额主体类型 */
public enum SubjectType {
    TENANT,   // 业务线级
    TASK,     // 单任务级
    AGENT     // Agent 级（扩展）
}
```

### 9.7 SandboxExecutor（沙箱执行器，见 §9.4）

`SandboxExecutor` 类签名见 §9.4，配套组件：

```java
package com.agentplatform.tool.executor.sandbox;

/** 沙箱容器池 */
public class SandboxPool {
    /** 借用容器（2s 超时） */
    public SandboxContainer borrow(SandboxConstraints constraints);
    /** 归还（实际销毁，不复用） */
    public void destroy(SandboxContainer container);
    /** 后台补充池 */
    public void refill();
}

/** 沙箱资源限制 */
public class ResourceQuota {
    private int cpuCount = 1;           // --cpus
    private String memoryLimit = "512m"; // --memory
    private String networkMode = "tool-internal";
    private boolean readOnlyRoot = true;
    private String tmpfsSize = "64m";
    private int pidsLimit = 50;
}

/** 沙箱超时处理 */
public class SandboxTimeoutHandler {
    /** SIGTERM 优雅终止（10s） */
    public void gracefulKill(String containerId);
    /** SIGKILL 强制终止 */
    public void forceKill(String containerId);
}
```

### 9.8 ToolCallAuditor（审计与落盘）

```java
package com.agentplatform.tool.audit;

import com.agentplatform.tool.model.ToolCallContext;

/**
 * 工具调用审计器
 * 落地表：tool_call_log（强一致）+ audit_log（异步）
 */
public class ToolCallAuditor {

    private final ToolCallLogRepository logRepository;
    private final RocketMQProducer mqProducer;
    private final DataMasker dataMasker;  // 脱敏

    /** 审计：调用成功/失败 */
    public void audit(ToolCallContext ctx, ToolInvokeResponse response);

    /** 审计：前置校验被拦截 */
    public void auditBlocked(ToolCallContext ctx, PreCheckResult preCheck);

    /** 内部：写 tool_call_log（强一致） */
    private void writeToolCallLog(ToolCallContext ctx, ToolInvokeResponse response) {
        ToolCallLog log = ToolCallLog.builder()
            .callId(ctx.getCallId())
            .taskId(ctx.getTaskId())
            .stepNo(ctx.getStepNo())
            .agentId(ctx.getAgentId())
            .toolId(ctx.getToolId())
            .toolVersion(ctx.getToolVersion())
            .input(dataMasker.mask(ctx.getInputJson()))    // 脱敏
            .output(truncate(response.getOutputJson()))    // 截断
            .status(response.getStatus())
            .errorCode(response.getErrorCode())
            .durationMs(response.getDurationMs())
            .costCent(response.getCostCent())
            .tokenUsed(response.getTokenUsed())
            .riskLevel(ctx.getTool().getRiskLevel())
            .approvedBy(ctx.getApproval() != null ? ctx.getApproval().getApprover() : null)
            .traceId(ctx.getTraceId())
            .build();
        logRepository.insert(log);
    }

    /** 内部：发送 RocketMQ tool.call.audit（异步） */
    private void sendAuditEvent(ToolCallContext ctx, ToolInvokeResponse response) {
        ToolCallAuditEvent event = ToolCallAuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("tool.call.audit")
            .traceId(ctx.getTraceId())
            .tenantId(ctx.getTenantId())
            .payload(ToolCallAuditPayload.builder()
                .callId(ctx.getCallId())
                .toolId(ctx.getToolId())
                .agentId(ctx.getAgentId())
                .status(response.getStatus())
                .riskLevel(ctx.getTool().getRiskLevel())
                .action("tool.invoke")
                .build())
            .build();
        mqProducer.send("tool.call.audit", event);
    }
}
```

### 9.9 核心模型类

```java
package com.agentplatform.tool.model;

/** 工具调用上下文（贯穿全链路） */
public class ToolCallContext {
    private String callId;
    private String taskId;
    private int stepNo;
    private long agentId;
    private String toolId;
    private int toolVersion;
    private String inputJson;
    private String inputHash;       // SHA-256 前 16 字节，用于缓存
    private TraceContext trace;
    private ToolRegistry tool;      // 加载后的工具定义
    private ToolApproval approval;  // R3 审批单（如有）
    private boolean fromCache;
    // getters / builder
}

/** 工具执行原始结果（清洗前） */
public class ToolRawResult {
    private boolean success;
    private String rawOutput;
    private String errorCode;
    private String errorMsg;
    private int durationMs;
    private boolean timeout;
}

/** 清洗后结果 */
public class CleanResult {
    private boolean success;
    private String outputJson;
    private String errorCode;
    private String errorMsg;
    private boolean summarized;
    private boolean paginated;
    private int tokenUsed;
}

/** 工具召回候选 */
public class ToolCandidate {
    private String toolId;
    private String name;
    private String description;
    private double score;             // 重排后得分
    private double relevanceScore;   // 向量相似度
    private String inputSchemaJson;
}
```

---

## 10. 业务效果量化指标

依据 PRD 第二节(二)5，指标分质量/价值/成本/合规/性能五类，采集链路关联 `tool_call_log`（MySQL）与 `agent_metrics_daily`（ClickHouse）。

### 10.1 指标采集架构

```
┌─────────────────────────────────────────────────────────────────┐
│  tool-engine                                                    │
│  每次调用 → tool_call_log（MySQL，明细）                         │
│           → RocketMQ tool.call.audit（事件）                     │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  quality-service（XXL-Job 定时聚合，每日 02:00）                 │
│  聚合 tool_call_log → agent_metrics_daily（ClickHouse，日表）   │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│  Grafana / 运营控制台                                           │
│  可视化看板 + 阈值告警                                          │
└─────────────────────────────────────────────────────────────────┘
```

### 10.2 五类指标定义与采集

#### 10.2.1 质量类指标

| 指标 | 计算公式 | 数据源 | 采集时机 |
|---|---|---|---|
| 调用成功率 | `count(status=success) / count(*)` | `tool_call_log.status` | 每次调用 |
| 参数错误率 | `count(error_code=VALIDATION_FAILED) / count(*)` | `tool_call_log.error_code` | 前置校验失败 |
| 工具选错率 | `count(align_check=reject) / count(recall)` | 召回对齐日志（扩展） | 召回对齐校验 |
| 重试成功率 | `count(retry_success) / count(retry_total)` | `tool_call_log`（重试标记） | 重试后 |

`tool_select_error` 字段已存在于 [ClickHouse `agent_metrics_daily`](../01-database/database-schema-design.md#84-clickhouseagent_metrics_daily-agent-指标日表)。

#### 10.2.2 价值类指标

| 指标 | 计算公式 | 数据源 | 采集时机 |
|---|---|---|---|
| 任务完成率提升幅度 | `(本期完成率 - 上期完成率) / 上期完成率` | `agent_metrics_daily.task_success / task_total` | 日聚合 |
| 结果准确率提升幅度 | 同上，按 Agent 维度 | 质量评估（`eval_task`） | 评测任务 |
| 人工介入率下降幅度 | `(本期介入率 - 上期介入率) / 上期介入率` | `task_state_change`（trigger=manual） | 日聚合 |

#### 10.2.3 成本类指标

| 指标 | 计算公式 | 数据源 | 采集时机 |
|---|---|---|---|
| 单次调用平均成本 | `sum(cost_cent) / count(*)` | `tool_call_log.cost_cent` | 每次调用 |
| 工具相关 Token 占比 | `sum(tool_token) / sum(total_token)` | `tool_call_log.token_used` + `model_usage_log` | 日聚合 |
| 缓存命中率 | `count(from_cache=true) / count(*)` | `tool_call_log`（扩展 from_cache 字段） | 每次调用 |

`avg_cost_cent` / `avg_token` 已存在于 `agent_metrics_daily`。

#### 10.2.4 合规类指标

| 指标 | 计算公式 | 数据源 | 采集时机 |
|---|---|---|---|
| 越权调用拦截率 | `count(audit.result=deny) / count(*)` | `audit_log`（action=tool.invoke, result=deny） | 每次调用 |
| 高危工具审批合规率 | `count(R3_with_approval) / count(R3_total)` | `tool_approval` + `tool_call_log`（risk_level=3） | 日聚合 |
| 审计覆盖率 | `count(tool_call_log) / count(tool_call_actual)` | `tool_call_log` vs 实际调用计数 | 日对账 |

#### 10.2.5 性能类指标

| 指标 | 计算公式 | 数据源 | 采集时机 |
|---|---|---|---|
| 平均调用耗时 | `avg(duration_ms)` | `tool_call_log.duration_ms` | 每次调用 |
| P95 调用耗时 | `percentile(0.95, duration_ms)` | `tool_call_log.duration_ms` | 日聚合 |
| 工具可用率 | `count(status!=failed and !=timeout) / count(*)` | `tool_call_log.status` | 每次调用 |
| 峰值并发承载能力 | `max(concurrent_calls)` | Prometheus 实时指标 | 实时 |

`p95_latency_ms` 已存在于 `agent_metrics_daily`。

### 10.3 采集与聚合

#### 10.3.1 实时指标（Prometheus）

`tool-engine` 暴露 `/actuator/prometheus`，关键指标：

```text
# 工具调用计数
tool_call_total{tool_id, risk_level, status}
# 工具调用耗时
tool_call_duration_ms{tool_id}  # Histogram
# 工具调用成本
tool_call_cost_cent{tool_id}    # Summary
# 当前并发
tool_concurrent_active{tool_id}
# 配额使用率
tool_quota_usage_ratio{subject_type, tool_id}
# 缓存命中
tool_cache_hit_total{tool_id}
```

#### 10.3.2 日聚合（ClickHouse）

XXL-Job 每日 02:00 执行聚合任务，从 MySQL `tool_call_log` 聚合到 ClickHouse `agent_metrics_daily`：

```sql
-- 聚合 SQL 伪代码
INSERT INTO agent_metrics_daily
SELECT
    DATE(created_at) AS date,
    tenant_id,
    agent_id,
    COUNT(DISTINCT task_id) AS task_total,
    COUNT(DISTINCT CASE WHEN task_status='success' THEN task_id END) AS task_success,
    COUNT(*) AS tool_call_total,
    COUNT(CASE WHEN status='success' THEN 1 END) AS tool_call_success,
    COUNT(CASE WHEN align_check='reject' THEN 1 END) AS tool_select_error,
    -- 其他指标...
    AVG(token_used) AS avg_token,
    AVG(cost_cent) AS avg_cost_cent,
    QUANTILE(0.95)(duration_ms) AS p95_latency_ms
FROM tool_call_log
WHERE created_at >= yesterday() AND created_at < today()
GROUP BY date, tenant_id, agent_id;
```

### 10.4 指标汇总表

| 类别 | 指标 | 数据源 | 聚合粒度 | 告警阈值 |
|---|---|---|---|---|
| 质量 | 调用成功率 | `tool_call_log` | 实时 + 日 | < 95% |
| 质量 | 参数错误率 | `tool_call_log` | 实时 + 日 | > 5% |
| 质量 | 工具选错率 | 召回对齐日志 | 日 | > 3% |
| 质量 | 重试成功率 | `tool_call_log` | 日 | < 80% |
| 价值 | 任务完成率提升 | `agent_metrics_daily` | 周 | 负增长 |
| 价值 | 人工介入率下降 | `task_state_change` | 周 | 上升 |
| 成本 | 单次平均成本 | `tool_call_log` | 日 | 超预算 20% |
| 成本 | 工具 Token 占比 | `tool_call_log` + `model_usage_log` | 日 | > 40% |
| 成本 | 缓存命中率 | `tool_call_log` | 日 | < 30% |
| 合规 | 越权拦截率 | `audit_log` | 实时 + 日 | 单 Agent > 3 次/min |
| 合规 | 审批合规率 | `tool_approval` | 日 | < 100% |
| 合规 | 审计覆盖率 | `tool_call_log` | 日 | < 100% |
| 性能 | 平均耗时 | `tool_call_log` | 日 | P50 > 500ms |
| 性能 | P95 耗时 | `tool_call_log` | 日 | > 800ms（[SLA 基线](../00-overview/tech-stack-and-architecture.md#12-非功能性指标基线)） |
| 性能 | 工具可用率 | `tool_call_log` | 日 | < 99% |
| 性能 | 峰值并发 | Prometheus | 实时 | 接近上限 80% |

---

## 11. 交叉引用与依赖

### 11.1 文档间引用

| 引用方向 | 引用内容 | 目标文档 |
|---|---|---|
| 本文档 → 技术栈 | tool-engine 端口、存储、ADR-005 | [00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md) |
| 本文档 → 数据库 | `tool_registry`/`tool_call_log`/`tool_quota`/`tool_approval`/`audit_log`/`permission_policy`/`tool_index`/`agent_metrics_daily` | [01-database/database-schema-design.md](../01-database/database-schema-design.md) |
| 本文档 → API | `ToolGateway` gRPC、REST 工具管理、RocketMQ `tool.call.audit`、限流熔断 | [02-api/api-specification.md](../02-api/api-specification.md) |
| 本文档 → PRD | 第二节(二) 全部要求 | [PRD.md](../../PRD.md) |
| 本文档 → 任务编排 | 工具调用在 DAG 节点中的角色 | [03-task-engine/task-orchestration-and-planning.md](../03-task-engine/task-orchestration-and-planning.md) |

### 11.2 表字段映射

| 本文档概念 | 数据库表 | 字段 |
|---|---|---|
| 三层 Schema 元数据层 | `tool_registry` | 全字段 |
| 三层 Schema 接口层 | `tool_registry` | `input_schema`/`output_schema`/`error_codes` |
| 风险三级 | `tool_registry` | `risk_level` (1/2/3) |
| 执行器类型 | `tool_registry` | `executor_type` (general/proxy/sandbox) |
| 工具生命周期 | `tool_registry` | `status` (1/2/3) |
| Prompt 缓存 | `tool_registry` | `prompt_cache_key` |
| 补偿动作 | `tool_registry` | `undo_action` |
| 调用审计 | `tool_call_log` | 全字段 |
| 配额管理 | `tool_quota` | `subject_type`/`subject_id`/`tool_id`/`daily_limit`/`cost_limit_cent` |
| R3 审批 | `tool_approval` | `status`/`expire_at`/`approver` |
| 权限策略 | `permission_policy` | `conditions`/`effect`/`expire_at` |
| 审计日志 | `audit_log` | `result` (allow/deny/warn) |
| 工具语义索引 | Milvus | `tool_index` Collection |
| 业务指标日表 | ClickHouse | `agent_metrics_daily` |

### 11.3 关键约束清单

| 约束 ID | 约束内容 | 来源 |
|---|---|---|
| C-001 | 所有工具调用必经 `ToolGateway`，禁止 Agent 直连 | ADR-005 |
| C-002 | R3 高危工具强制 `executor_type=sandbox` | PRD §二(二)3 |
| C-003 | R3 工具调用前必须有有效 `tool_approval` | PRD §二(二)3 |
| C-004 | R2/R3 写操作必须有 `undo_action` | PRD §二(二)3 |
| C-005 | 单任务工具调用 ≤ 50 次，单工具单任务内 ≤ 10 次 | PRD §二(二)4 |
| C-006 | 相同入参结果缓存仅 R1 工具启用 | PRD §二(二)4 |
| C-007 | 沙箱容器一次性使用，不复用 | 安全设计 |
| C-008 | 工具调用全量落 `tool_call_log` + `audit_log` | PRD §二(二)3 |
| C-009 | 模型生成的调用指令必须通过召回对齐校验 | PRD §四(二) 第三层 |
| C-010 | P95 调用耗时 ≤ 800ms | 技术栈 §1.2 |

---

## 12. 后续工作

本文档定义了工具引擎的工程级契约，后续待补充：

| 待补充项 | 归属 | 优先级 |
|---|---|---|
| `tool.proto` 完整 Protobuf 定义 | `agent-proto` 模块 | P0 |
| `ToolHandler` 内置工具实现（`kb_retrieve`/`time_now` 等） | `agent-tool-engine` | P1 |
| Docker 沙箱镜像构建脚本 | `infra/docker/sandbox/` | P0 |
| 工具注册管理后台 UI | 控制台前端 | P2 |
| `tool_index` Collection 初始化脚本 | `infra/sql/10-milvus-collections.json` | P0 |
| 指标聚合 XXL-Job 任务实现 | `agent-quality` | P1 |

下一份相关文档：[06-agent-runtime/agent-runtime-engine.md](../06-agent-runtime/agent-runtime-engine.md) 将描述 Agent Runtime 如何通过 `ToolGateway` 发起调用。
