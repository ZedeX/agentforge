# 核心接口定义与 API 规范

> 文档版本：v1.0  |  更新日期：2026-06-26  |  对应 PRD：第七节·工程文件输出要求 4
> 依赖文档：[00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md)、[01-database/database-schema-design.md](../01-database/database-schema-design.md)

## 0. 总体规范

### 0.1 协议分层

| 层级 | 协议 | 用途 |
|---|---|---|
| 对外开放 API | RESTful + JSON over HTTPS | 终端用户、第三方系统、Web 控制台 |
| 内部服务间通信 | gRPC + Protobuf | 引擎间高性能调用 |
| 流式输出 | SSE（Server-Sent Events）/ WebSocket | 对话流式响应、任务进度推送 |
| 异步事件 | RocketMQ Topic | 子任务分发、状态变更通知、日志归集 |

### 0.2 REST URL 规范

- 基础路径：`/api/v1`
- 资源命名：复数名词，小写连字符，如 `/api/v1/agents`、`/api/v1/tasks/{taskId}/steps`
- HTTP 方法语义：GET 查询、POST 创建、PUT 全量更新、PATCH 部分更新、DELETE 删除
- 查询参数：分页 `page`/`size`、排序 `sort=field,desc`、过滤 `field=value`

### 0.3 通用请求头

| Header | 必填 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <JWT>` |
| `X-Tenant-Id` | 是 | 租户 ID |
| `X-Request-Id` | 否 | 请求追踪 ID，未传则网关生成 |
| `X-Trace-Id` | 否 | 链路 ID（透传 SkyWalking） |
| `Content-Type` | 是 | `application/json; charset=utf-8` |
| `Accept-Language` | 否 | `zh-CN` 默认 |

### 0.4 统一响应格式

**成功响应**：

```json
{
  "code": "OK",
  "message": "success",
  "data": { },
  "traceId": "a1b2c3d4...",
  "timestamp": "2026-06-26T10:00:00.000Z"
}
```

**错误响应**：

```json
{
  "code": "TASK_NOT_FOUND",
  "message": "任务不存在",
  "details": { "taskId": "tk_xxx" },
  "traceId": "a1b2c3d4...",
  "timestamp": "2026-06-26T10:00:00.000Z"
}
```

**分页响应**：

```json
{
  "code": "OK",
  "data": {
    "items": [ ],
    "page": 1,
    "size": 20,
    "total": 135
  }
}
```

### 0.5 错误码规范

错误码采用 `大写下划线` 命名，按域分组：

| 前缀 | 域 | HTTP 状态 | 示例 |
|---|---|---|---|
| `OK` | 成功 | 200 | OK |
| `VALIDATION_*` | 参数校验 | 400 | VALIDATION_FAILED |
| `UNAUTHENTICATED` | 未认证 | 401 | UNAUTHENTICATED |
| `FORBIDDEN` | 无权限 | 403 | FORBIDDEN、TOOL_RISK_DENIED |
| `NOT_FOUND` | 资源不存在 | 404 | TASK_NOT_FOUND、AGENT_NOT_FOUND |
| `CONFLICT` | 状态冲突 | 409 | TASK_STATUS_CONFLICT、DAG_VERSION_CONFLICT |
| `RATE_LIMITED` | 限流 | 429 | RATE_LIMITED、COST_BUDGET_EXCEEDED |
| `INTERNAL` | 内部错误 | 500 | INTERNAL、MODEL_GATEWAY_ERROR |
| `UNAVAILABLE` | 服务不可用 | 503 | DEPENDENCY_DOWN、CIRCUIT_OPEN |
| `TIMEOUT` | 超时 | 504 | TOOL_TIMEOUT、MODEL_TIMEOUT |

### 0.6 鉴权与授权

- **JWT 认证**：网关校验 JWT，解析出 `userId`/`tenantId`/`roles` 注入下游 Header
- **RBAC+ABAC**：`risk-control` 拦截器对每个写操作校验 `permission_policy`
- **API Key**：第三方系统集成使用长期 API Key（`X-API-Key` Header），网关映射为系统用户

---

## 1. 会话与任务接入 API（REST，gateway 转发）

### 1.1 会话管理

#### POST `/api/v1/sessions` 创建会话

```json
// Request
{
  "agentId": "ag_1001",
  "title": "订单查询助手",
  "meta": { "channel": "web" }
}

// Response.data
{
  "sessionId": "ss_a1b2c3d4",
  "agentId": "ag_1001",
  "status": "active",
  "createdAt": "2026-06-26T10:00:00.000Z"
}
```

#### GET `/api/v1/sessions/{sessionId}` 查询会话

#### GET `/api/v1/sessions/{sessionId}/messages` 消息历史

```
GET /api/v1/sessions/ss_xxx/messages?page=1&size=20&sort=createdAt,asc
```

#### DELETE `/api/v1/sessions/{sessionId}` 关闭会话

### 1.2 对话交互

#### POST `/api/v1/sessions/{sessionId}/chat` 发送消息（同步）

```json
// Request
{
  "content": "帮我查一下最近 7 天的订单",
  "contentType": "text",
  "stream": false
}

// Response.data（stream=false）
{
  "messageId": "msg_xxx",
  "role": "assistant",
  "content": "已为您查询到 3 笔订单...",
  "toolCalls": [
    {"toolId": "tl_query_order", "callId": "call_xxx", "status": "success"}
  ],
  "taskId": "tk_yyy",
  "tokenUsed": 1520
}
```

#### POST `/api/v1/sessions/{sessionId}/chat/stream` 流式对话（SSE）

请求体同上（`stream: true`）。响应为 SSE 流：

```
event: token
data: {"delta": "已"}

event: token
data: {"delta": "为您"}

event: tool_call
data: {"toolId": "tl_query_order", "callId": "call_xxx", "phase": "start"}

event: tool_result
data: {"callId": "call_xxx", "status": "success", "summary": "3 笔订单"}

event: done
data: {"messageId": "msg_xxx", "tokenUsed": 1520, "taskId": "tk_yyy"}
```

### 1.3 任务管理

#### POST `/api/v1/tasks` 提交异步任务

```json
// Request
{
  "title": "生成周报并邮件发送",
  "goal": "汇总本周销售数据生成周报，发送给 manager@xx.com",
  "priority": 5,
  "async": true,
  "sessionId": "ss_xxx",
  "costLimitCent": 5000,
  "constraints": {
    "deadline": "2026-06-27T00:00:00Z",
    "allowHumanFallback": true
  }
}

// Response.data
{
  "taskId": "tk_yyy",
  "status": "PENDING",
  "complexity": null,
  "submittedAt": "2026-06-26T10:00:00.000Z"
}
```

#### GET `/api/v1/tasks/{taskId}` 查询任务

```json
// Response.data
{
  "taskId": "tk_yyy",
  "status": "RUNNING",
  "complexity": 3,
  "dagVersion": 1,
  "progress": {
    "totalNodes": 5,
    "finishedNodes": 2,
    "runningNodes": 1,
    "failedNodes": 0
  },
  "costUsedCent": 1200,
  "tokenUsed": 8500,
  "startedAt": "2026-06-26T10:00:01.000Z",
  "steps": [
    {
      "stepNo": 1,
      "nodeId": "n1",
      "phase": "observe",
      "status": "success",
      "durationMs": 1200
    }
  ]
}
```

#### GET `/api/v1/tasks/{taskId}/steps` 任务步骤列表

#### POST `/api/v1/tasks/{taskId}/cancel` 取消任务

```json
// Request
{ "reason": "用户主动取消" }

// Response.data
{ "taskId": "tk_yyy", "status": "CANCELLED", "cancelledAt": "..." }
```

#### POST `/api/v1/tasks/{taskId}/feedback` 任务结果反馈

```json
// Request
{
  "rating": "positive|negative",
  "tags": ["accurate", "too_slow"],
  "comment": "结果准确但耗时较长"
}
```

---

## 2. Agent 仓库 API（REST）

### 2.1 Agent 管理

#### POST `/api/v1/agents` 创建 Agent

```json
// Request
{
  "name": "订单查询助手",
  "description": "处理用户订单查询、状态跟踪",
  "abilityTags": ["query", "order", "status"],
  "sceneTags": ["ecommerce"],
  "systemPrompt": "你是订单查询助手...",
  "coreConstraints": "1. 必须通过工具查询；2. 禁止编造订单号",
  "businessConfig": { "defaultPageSize": 10 },
  "modelTier": "middle",
  "maxSteps": 10,
  "maxToken": 60000,
  "boundTools": ["tl_query_order", "tl_update_address"],
  "boundKnowledgeIds": ["kb_001"],
  "reflectionMode": "single"
}

// Response.data
{
  "agentId": "ag_1001",
  "version": 1,
  "status": "draft"
}
```

#### GET `/api/v1/agents/{agentId}` 查询 Agent 详情
#### GET `/api/v1/agents?page=1&size=20&tag=query` 列表
#### PUT `/api/v1/agents/{agentId}` 更新 Agent（创建新版本）
#### POST `/api/v1/agents/{agentId}/publish` 发布 Agent 版本

```json
// Request
{ "changeLog": "增加退款流程支持" }

// Response.data
{ "agentId": "ag_1001", "version": 2, "publishedAt": "..." }
```

#### POST `/api/v1/agents/{agentId}/rollback` 回滚至稳定版

```json
// Request
{ "targetVersion": 1, "reason": "v2 出现漂移" }
```

#### GET `/api/v1/agents/{agentId}/scores` Agent 效果评分

```json
// Response.data
{
  "agentId": "ag_1001",
  "dimensions": [
    {"dimension": "success_rate", "score": 0.92, "sampleCount": 1200},
    {"dimension": "accuracy", "score": 0.88},
    {"dimension": "hallucination", "score": 0.04},
    {"dimension": "latency_p95", "score": 1800}
  ],
  "period": "2026-06-19 ~ 2026-06-25"
}
```

---

## 3. 工具引擎 API（REST 管理 + gRPC 调用）

### 3.1 工具注册管理（REST）

#### POST `/api/v1/tools` 注册工具

```json
// Request
{
  "name": "query_order",
  "displayName": "查询订单",
  "description": "根据用户ID查询订单列表",
  "sceneTags": ["order", "query"],
  "toolType": "atomic",
  "riskLevel": 1,
  "inputSchema": {
    "type": "object",
    "properties": {
      "userId": {"type": "string", "description": "用户ID"},
      "days": {"type": "integer", "default": 7, "minimum": 1, "maximum": 90}
    },
    "required": ["userId"]
  },
  "outputSchema": {
    "type": "object",
    "properties": {
      "orders": {"type": "array", "items": {"type": "object"}}
    }
  },
  "errorCodes": [
    {"code": "USER_NOT_FOUND", "message": "用户不存在"},
    {"code": "RATE_LIMITED", "message": "查询频次超限"}
  ],
  "executorType": "proxy",
  "endpoint": "grpc://order-service/OrderService/QueryOrder",
  "timeoutMs": 5000,
  "undoAction": null
}
```

#### GET `/api/v1/tools` 列表（支持按场景/风险过滤）
#### GET `/api/v1/tools/{toolId}` 详情
#### PUT `/api/v1/tools/{toolId}` 更新（创建新版本）
#### POST `/api/v1/tools/{toolId}/approve` 高危工具审批提交

### 3.2 工具调用（gRPC，仅内部 Agent Runtime 调用）

```protobuf
// agent-proto/src/main/proto/tool.proto
syntax = "proto3";
package agentplatform.tool.v1;

service ToolGateway {
  // 工具调用统一入口
  rpc Invoke(ToolInvokeRequest) returns (ToolInvokeResponse);
  // 工具语义召回
  rpc Recall(ToolRecallRequest) returns (ToolRecallResponse);
  // 工具调用结果回写（用于缓存与审计）
  rpc ReportResult(ToolResultReport) returns (ReportAck);
}

message ToolInvokeRequest {
  string call_id = 1;
  string task_id = 2;
  int32 step_no = 3;
  int64 agent_id = 4;
  string tool_id = 5;
  int32 tool_version = 6;
  string input_json = 7;          // JSON 字符串，符合 inputSchema
  TraceContext trace = 99;
}

message ToolInvokeResponse {
  string call_id = 1;
  string status = 2;               // success | failed | timeout | blocked
  string output_json = 3;
  string error_code = 4;
  string error_msg = 5;
  int32 duration_ms = 6;
  int64 cost_cent = 7;
  int32 token_used = 8;
  bool from_cache = 9;
}

message ToolRecallRequest {
  string task_id = 1;
  string query = 2;                // 自然语言查询
  repeated string scene_tags = 3;
  int32 top_k = 4;
  TraceContext trace = 99;
}

message ToolRecallResponse {
  repeated ToolCandidate candidates = 1;
}

message ToolCandidate {
  string tool_id = 1;
  string name = 2;
  string description = 3;
  double score = 4;
  string input_schema_json = 5;
}

message TraceContext {
  string trace_id = 1;
  string span_id = 2;
  int64 tenant_id = 3;
  string user_id = 4;
}
```

---

## 4. 记忆管理 API（gRPC，内部）

```protobuf
// agent-proto/src/main/proto/memory.proto
syntax = "proto3";
package agentplatform.memory.v1;

service MemoryService {
  // 加载短期记忆（会话上下文）
  rpc LoadShortTerm(LoadShortTermRequest) returns (ShortTermMemory);
  // 长期记忆召回（多路融合）
  rpc Recall(RecallRequest) returns (RecallResponse);
  // 写入长期记忆（任务完成后）
  rpc WriteLongTerm(WriteLongTermRequest) returns (WriteAck);
  // 蒸馏触发
  rpc TriggerDistill(DistillRequest) returns (DistillAck);
  // 记忆状态查询
  rpc GetMemoryStatus(MemoryStatusRequest) returns (MemoryStatus);
}

message LoadShortTermRequest {
  string session_id = 1;
  int64 agent_id = 2;
  int32 max_recent_turns = 3;   // 最近 N 轮
  int32 token_budget = 4;      // 可用 Token 预算
  TraceContext trace = 99;
}

message ShortTermMemory {
  string system_prompt = 1;
  string core_constraints = 2;
  repeated MessageBlock recent_messages = 3;
  repeated RecalledMemory recalled = 4;   // 注入的长期记忆
  int32 token_used = 5;
  int32 token_water_percent = 6;          // 水位百分比
  string compress_level = 7;               // none|light|medium|heavy
}

message RecallRequest {
  string session_id = 1;
  int64 agent_id = 2;
  string query = 3;
  repeated string strategies = 4;   // [vector, keyword, time, tag]
  int32 top_k = 5;
  int32 token_budget = 6;
  TraceContext trace = 99;
}

message RecallResponse {
  repeated RecalledMemory memories = 1;
  RecallMeta meta = 2;
}

message RecalledMemory {
  string memory_id = 1;
  string content = 2;
  string source_type = 3;          // task | user | system
  string source_task_id = 4;
  double importance_score = 5;
  double relevance_score = 6;
  int64 created_at = 7;
}

message WriteLongTermRequest {
  int64 agent_id = 1;
  string user_id = 2;
  string domain = 3;
  string memory_type = 4;          // episodic | semantic | procedural
  string content = 5;
  repeated string tags = 6;
  string source_task_id = 7;
  TraceContext trace = 99;
}

message WriteAck {
  string memory_id = 1;
  bool deduplicated = 2;           // 是否被去重合并
  string merged_memory_id = 3;     // 若去重，被合并的目标
}
```

---

## 5. 模型网关 API（gRPC，内部）

```protobuf
// agent-proto/src/main/proto/model.proto
syntax = "proto3";
package agentplatform.model.v1;

service ModelGateway {
  // 同步调用
  rpc Chat(ChatRequest) returns (ChatResponse);
  // 流式调用
  rpc ChatStream(ChatRequest) returns (stream ChatChunk);
  // Token 计数
  rpc CountTokens(CountTokensRequest) returns (CountTokensResponse);
}

message ChatRequest {
  string call_id = 1;
  string task_id = 2;
  string scene = 3;                // intent | planning | tool_call | summary | audit
  string tier = 4;                 // light | middle | strong
  string preferred_model = 5;       // 可指定，否则按路由
  repeated Message messages = 6;
  ModelParams params = 7;
  bool enable_prompt_cache = 8;
  TraceContext trace = 99;
}

message Message {
  string role = 1;                 // system | user | assistant | tool
  string content = 2;
  repeated ToolCall tool_calls = 3;
  string tool_call_id = 4;
}

message ModelParams {
  double temperature = 1;
  int32 max_tokens = 2;
  double top_p = 3;
  repeated string stop = 4;
  bool enable_cot = 5;             // 强制思维链
  bool require_source = 6;         // 要求来源标注
}

message ChatResponse {
  string call_id = 1;
  string model = 2;                 // 实际使用的模型
  string provider = 3;
  string content = 4;
  repeated ToolCall tool_calls = 5;
  int32 input_tokens = 6;
  int32 output_tokens = 7;
  int64 cost_cent = 8;
  bool cache_hit = 9;
  int32 duration_ms = 10;
}

message ChatChunk {
  string call_id = 1;
  string delta = 2;
  oneof extra {
    ToolCall tool_call = 3;
    FinishReason finish = 4;
  }
}

message CountTokensRequest {
  string model = 1;
  repeated Message messages = 2;
}
message CountTokensResponse {
  int32 token_count = 1;
}
```

---

## 6. 任务编排 API（gRPC，内部）

```protobuf
// agent-proto/src/main/proto/task.proto
syntax = "proto3";
package agentplatform.task.v1;

service TaskOrchestrator {
  // 提交任务（gateway 转发）
  rpc SubmitTask(SubmitTaskRequest) returns (SubmitTaskResponse);
  // 查询任务状态
  rpc GetTask(GetTaskRequest) returns (TaskDetail);
  // 子任务完成回调（Agent Runtime 上报）
  rpc ReportSubtaskResult(SubtaskResult) returns (ReportAck);
  // 取消任务
  rpc CancelTask(CancelTaskRequest) returns (CancelAck);
  // 请求重规划
  rpc RequestReplan(ReplanRequest) returns (ReplanResponse);
}

message SubmitTaskRequest {
  string task_id = 1;
  int64 tenant_id = 2;
  string user_id = 3;
  string session_id = 4;
  string title = 5;
  string goal = 6;
  int32 priority = 7;
  int64 cost_limit_cent = 8;
  TraceContext trace = 99;
}

service PlanningService {
  // 复杂度评估
  rpc AssessComplexity(AssessRequest) returns (AssessResponse);
  // 生成规划（DAG）
  rpc Plan(PlanRequest) returns (PlanResponse);
  // 规划自检
  rpc ValidatePlan(ValidateRequest) returns (ValidateResponse);
}

message PlanRequest {
  string task_id = 1;
  string task_schema_json = 2;
  bool prefer_template = 3;
  TraceContext trace = 99;
}

message PlanResponse {
  string dag_json = 1;             // nodes + edges
  int32 dag_version = 2;
  string source = 3;              // template | ai
  string template_id = 4;
  repeated string warnings = 5;
}
```

---

## 7. Agent 运行时 API（gRPC，内部，由 task-orchestrator 通过 RocketMQ 触发）

Agent Runtime 主要消费 RocketMQ Topic `task.subtask.execute`，上报通过 gRPC。

### 7.1 消息格式（RocketMQ Topic）

```json
// Topic: task.subtask.execute
{
  "taskId": "tk_yyy",
  "subtaskId": "st_001",
  "nodeId": "n1",
  "agentId": "ag_1001",
  "inputs": {"userId": "u_123"},
  "config": {
    "maxRetries": 2,
    "timeoutMs": 30000,
    "modelTier": "middle"
  },
  "traceId": "..."
}
```

### 7.2 gRPC 上报接口

```protobuf
service AgentRuntime {
  // 上报步骤执行
  rpc ReportStep(StepReport) returns (ReportAck);
  // 请求工具调用（实际由 tool-engine 处理，runtime 仅触发）
  rpc RequestToolCall(ToolCallRequest) returns (ToolCallResponse);
  // 上报执行完成
  rpc ReportSubtaskDone(SubtaskDoneReport) returns (ReportAck);
  // 请求人工介入
  rpc RequestHumanIntervention(HumanInterventionRequest) returns (HumanInterventionResponse);
}

message StepReport {
  string task_id = 1;
  int32 step_no = 2;
  string node_id = 3;
  string phase = 4;                // think | act | observe | reflect
  string action_type = 5;
  string action_target = 6;
  string input_json = 7;
  string output_json = 8;
  int32 token_used = 9;
  int64 cost_cent = 10;
  int32 duration_ms = 11;
  string status = 12;
  string error = 13;
  TraceContext trace = 99;
}
```

---

## 8. 知识库 API（REST）

### 8.1 知识库管理

#### POST `/api/v1/knowledge-bases` 创建知识库
#### POST `/api/v1/knowledge-bases/{kbId}/documents` 上传文档

```json
// multipart/form-data
// file: 文件
// metadata: {"title":"退货政策","acl":{"roles":["cs_agent"]}}
```

#### GET `/api/v1/knowledge-bases/{kbId}/documents/{docId}` 查询解析状态
#### POST `/api/v1/knowledge-bases/{kbId}/documents/{docId}/reindex` 重新切片索引
#### DELETE `/api/v1/knowledge-bases/{kbId}/documents/{docId}` 删除文档

### 8.2 知识检索（内部 gRPC）

```protobuf
service KnowledgeService {
  rpc Retrieve(KnowledgeQuery) returns (KnowledgeResult);
  rpc IngestDocument(IngestRequest) returns (IngestResponse);
}

message KnowledgeQuery {
  string kb_id = 1;
  string query = 2;
  int32 top_k = 3;
  string user_id = 4;              // ACL 过滤
  repeated string roles = 5;
  TraceContext trace = 99;
}
```

---

## 9. 质量与治理 API（REST）

### 9.1 质量评估

#### POST `/api/v1/evaluations` 创建评测任务

```json
// Request
{
  "type": "offline",
  "agentId": "ag_1001",
  "baselineId": "bl_xxx",
  "sampleCount": 100
}
```

#### GET `/api/v1/evaluations/{evalId}` 查询评测结果
#### GET `/api/v1/agents/{agentId}/metrics?period=7d` Agent 指标趋势

### 9.2 Badcase 管理

#### POST `/api/v1/badcases` 上报 Badcase
#### GET `/api/v1/badcases?status=open` 列表
#### POST `/api/v1/badcases/{caseId}/analyze` 提交根因分析

### 9.3 治理配置

#### GET/PUT `/api/v1/governance/baselines` 黄金基准集
#### GET/PUT `/api/v1/governance/drift-thresholds` 漂移阈值配置
#### GET/PUT `/api/v1/governance/hallucination-rules` 幻觉治理规则

---

## 10. 风控与审计 API（REST）

### 10.1 权限策略管理

#### POST `/api/v1/permissions/policies` 创建权限策略
#### GET `/api/v1/permissions/policies?subjectType=role&resourceType=tool` 查询
#### POST `/api/v1/permissions/temporary` 授予临时权限

```json
// Request
{
  "subjectType": "user",
  "subjectId": "u_admin",
  "resourceType": "tool",
  "resourceId": "tl_delete_user",
  "action": "execute",
  "expireAt": "2026-06-26T12:00:00Z",
  "reason": "紧急运维"
}
```

### 10.2 审计查询

#### GET `/api/v1/audit/logs?subjectId=u_123&action=write&from=2026-06-01&to=2026-06-26`

```json
// Response.data.items[]
{
  "auditId": "ad_xxx",
  "subjectType": "user",
  "subjectId": "u_123",
  "action": "tool.invoke",
  "resourceType": "tool",
  "resourceId": "tl_update_address",
  "riskLevel": 2,
  "result": "allow",
  "detail": {"callId": "call_xxx", "taskId": "tk_yyy"},
  "traceId": "...",
  "createdAt": "2026-06-26T10:05:00.000Z"
}
```

### 10.3 越权拦截告警

#### GET `/api/v1/audit/violations` 越权记录列表（含 R3 高危被拦截记录）

---

## 11. 异步事件规范（RocketMQ Topics）

| Topic | 生产者 | 消费者 | 用途 | 消息格式 |
|---|---|---|---|---|
| `task.subtask.execute` | task-orchestrator | agent-runtime | 分发子任务执行 | JSON |
| `task.subtask.done` | agent-runtime | task-orchestrator | 子任务完成上报 | JSON |
| `task.state.change` | task-orchestrator | session/observability | 任务状态变更广播 | JSON |
| `tool.call.audit` | tool-engine | risk-control | 工具调用审计 | JSON |
| `memory.write` | agent-runtime | memory-service | 记忆写入请求 | JSON |
| `quality.badcase` | quality-service | observability | Badcase 事件 | JSON |
| `governance.drift.alert` | quality-service | observability | 漂移告警 | JSON |

### 11.1 事件通用信封

```json
{
  "eventId": "ev_uuid",
  "eventType": "task.subtask.done",
  "eventTime": "2026-06-26T10:00:00.000Z",
  "traceId": "...",
  "tenantId": 1001,
  "payload": { }
}
```

### 11.2 幂等与重试

- 所有事件携带 `eventId`，消费者落 `event_consume_log` 表去重
- 消费失败按 RocketMQ 重试策略：1s、5s、10s、30s、1m、5m、10m...最多 16 次
- 16 次仍失败进入死信队列 `DLQ.%topic%`，触发告警人工处理

---

## 12. 限流与熔断规范

### 12.1 限流规则（Sentinel）

| 资源 | 维度 | 阈值 | 行为 |
|---|---|---|---|
| `/api/v1/sessions/*/chat` | tenantId | 50 QPS | 排队 1s 后拒绝 |
| `/api/v1/tasks` POST | tenantId | 10 QPS | 直接拒绝 |
| `ToolGateway.Invoke` | toolId | 按 tool 配置 | 拒绝并返回 RATE_LIMITED |
| `ModelGateway.Chat` | tenantId + scene | 按 scene 配置 | 降级到 fallback 模型 |

### 12.2 熔断规则

| 资源 | 触发条件 | 半开恢复 |
|---|---|---|
| model-gateway (single provider) | 1 分钟错误率 > 30% | 30s 后放 10% 流量试探 |
| tool-engine (single tool) | 1 分钟失败率 > 50% | 60s 后单次试探 |
| agent-runtime (single agent) | 1 分钟超时率 > 40% | 自动扩容并 30s 试探 |

---

## 13. 版本与兼容性

- API 版本通过 URL 路径 `/api/v1/` 显式声明
- 破坏性变更需新增 `/api/v2/`，旧版本至少保留 6 个月
- gRPC 接口通过 Protobuf 字段编号兼容：新增字段使用新编号，禁止删除/复用已有编号
- Webhook/事件格式变更通过 `eventType` 后缀版本化，如 `task.subtask.done.v2`

---

## 14. 与各模块详设文档的对应

| 本文档章节 | 详设文档 |
|---|---|
| §1 会话与任务接入 | [03-task-engine](../03-task-engine/task-orchestration-and-planning.md) |
| §3 工具引擎 API | [05-tool-engine](../05-tool-engine/tool-and-invocation-system.md) |
| §4 记忆 API | [04-memory](../04-memory/memory-system-design.md) |
| §5 模型网关 API | [09-governance-and-deployment](../09-governance-and-deployment/governance-and-middleware.md) |
| §6 任务编排 API | [03-task-engine](../03-task-engine/task-orchestration-and-planning.md) |
| §7 Agent 运行时 API | [06-agent-runtime](../06-agent-runtime/agent-runtime-engine.md) |
| §9 质量治理 API | [09-governance-and-deployment](../09-governance-and-deployment/governance-and-middleware.md) |
