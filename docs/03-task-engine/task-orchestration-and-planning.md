# 任务编排与调度引擎 + 智能规划引擎 详细设计

> 文档版本：v1.0  |  更新日期：2026-06-26  |  对应 PRD：第一节(三)/第二节(三)

## 0. 文档定位与依赖

本文档定义 Agent 平台 **核心引擎层** 中「任务编排与调度引擎」与「智能规划引擎」两个核心子系统的工程级实现契约，是后续编码实现的直接依据。

**依赖文档**：
- [00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md) — 微服务端口、技术栈、通信矩阵
- [01-database/database-schema-design.md](../01-database/database-schema-design.md) — 任务域表结构（task_instance/task_dag/task_step_log/task_replan_log/task_template/task_state_change）
- [02-api/api-specification.md](../02-api/api-specification.md) — 第 6 节任务编排 gRPC 接口、第 11 节 RocketMQ Topic 规范
- [PRD.md](../../PRD.md) — 第一节(三) 完整执行全链路 7 步、第二节(三) 任务规划与编排引擎、第三节 异常容错
- [08-flow/state-machines-and-sequences.md](../08-flow/state-machines-and-sequences.md) — 状态机时序图（本文仅列状态与流转规则，具体时序图在该文档绘制）

**关联模块**：
- [06-agent-runtime/agent-runtime-engine.md](../06-agent-runtime/agent-runtime-engine.md) — 子任务执行端
- [04-memory/memory-system-design.md](../04-memory/memory-system-design.md) — 任务上下文加载与沉淀
- [05-tool-engine/tool-and-invocation-system.md](../05-tool-engine/tool-and-invocation-system.md) — Agent 调用工具的网关
- [09-governance-and-deployment/governance-and-middleware.md](../09-governance-and-deployment/governance-and-middleware.md) — 异常分级、熔断、成本管控

---

## 1. 模块职责与边界

### 1.1 双引擎职责划分

PRD「管控与执行分离」原则要求：**规划与调度分离、调度与执行分离**。本设计将其落地为两个独立微服务：

| 维度 | task-orchestrator（8084） | planning-service（8086） |
|---|---|---|
| 定位 | 任务全生命周期管控者 | 任务规划智能中枢 |
| 核心职责 | 状态机驱动、DAG 执行调度、子任务分发、失败重试、重规划触发 | 复杂度评估、子任务拆解、DAG 生成、规划自检、模板管理 |
| 是否有状态 | 有（任务实例状态、DAG 运行态） | 无（纯计算 + DB 读取模板/写入规划结果） |
| 数据库表 | task_instance / task_dag / task_step_log / task_state_change / task_replan_log | task_template / task_dag（写入）/ task_instance.complexity（回写） |
| MQ 角色 | task.subtask.execute 生产者 / task.subtask.done 消费者 | 不参与 MQ |
| gRPC 角色 | TaskOrchestrator 服务端 / PlanningService 客户端 | PlanningService 服务端 |
| 与 Agent Runtime 关系 | 异步分发 + 接收回调 | 不直接交互 |
| 与 Memory 关系 | 任务完成后触发记忆写入事件 | 规划阶段读取流程记忆辅助拆解 |
| 与 Model Gateway 关系 | 不直接调用 | 调用强模型做规划/复杂度精判/自检 |

### 1.2 调用边界与契约

```
gateway ──REST SubmitTask──> task-orchestrator ──gRPC AssessComplexity──> planning-service
                                       │
                                       └──gRPC Plan──> planning-service
                                              │
                                              └── gRPC ValidatePlan (自检)
                                       │
                                       └── 落 task_dag (v1)
                                       │
                                       └── MQ task.subtask.execute ──> agent-runtime
                                              │
                                       ┌──MQ task.subtask.done──┘
                                       │
                                       └── 失败/校验不通过 ──gRPC RequestReplan──> 内部 Replanner
                                                                      │
                                                                      └── gRPC Plan(prefer_template=false) ──> planning-service
```

**关键约束**：
1. **planning-service 不持有运行态**：规划结果落库后即返回，后续执行/重规划由 orchestrator 主导，planning-service 仅作为「无状态计算服务」被反复调用
2. **DAG 不可在运行中就地修改**：所有结构变更必须新建版本（task_dag.version 自增），由 task_state_change 审计
3. **状态机唯一归属**：task_instance.status 仅由 task-orchestrator 修改，planning-service 不可越权
4. **失败语义边界**：planning-service 失败（如模型超时）→ orchestrator 进入 REPLANNING 或 WAITING_HUMAN；orchestrator 内部失败（如 DB 死锁）→ 内部重试不暴露给 planning

### 1.3 模块内子包划分

依据 [00-overview 第 4 节](../00-overview/tech-stack-and-architecture.md#4-项目目录结构) 约定的 `com.agentplatform.task.*` 包名：

| 子包 | 归属服务 | 职责 |
|---|---|---|
| `com.agentplatform.task.orchestrator.api` | task-orchestrator | gRPC 服务实现（TaskOrchestratorGrpcImpl） |
| `com.agentplatform.task.orchestrator.statemachine` | task-orchestrator | 状态机引擎、流转校验 |
| `com.agentplatform.task.orchestrator.dag` | task-orchestrator | DAG 加载、并行批次执行、环检测 |
| `com.agentplatform.task.orchestrator.dispatcher` | task-orchestrator | RocketMQ 子任务分发器 |
| `com.agentplatform.task.orchestrator.replanner` | task-orchestrator | 重规划触发器、熔断控制 |
| `com.agentplatform.task.orchestrator.callback` | task-orchestrator | 子任务完成回调处理 |
| `com.agentplatform.task.planning.api` | planning-service | gRPC 服务实现（PlanningServiceGrpcImpl） |
| `com.agentplatform.task.planning.assessor` | planning-service | 复杂度评估器（规则+模型） |
| `com.agentplatform.task.planning.planner` | planning-service | 模板规划器 + 智能规划器 |
| `com.agentplatform.task.planning.template` | planning-service | 模板仓库、匹配、参数填充 |
| `com.agentplatform.task.planning.validator` | planning-service | 规划自检（5 维度） |
| `com.agentplatform.task.common.model` | 共享 | DagNode/DagEdge/TaskSchema 等 POJO |
| `com.agentplatform.task.common.enums` | 共享 | TaskStatus/ComplexityLevel/ReplanMode 等 |

---

## 2. 任务复杂度识别

### 2.1 三级分级标准

依据 PRD 第二节(三)1，定义三级分级：

| 等级 | 名称 | 特征 | 模型档位 | 编排方式 | 典型耗时 | 典型 Token |
|---|---|---|---|---|---|---|
| L1 | 简单任务 | 单目标单步、无需工具与外部知识，单 Agent 直出 | light | 无 DAG，单 Agent 直跑 | < 3s | ≤ 2K |
| L2 | 中等任务 | 单领域、1~3 次工具调用，单 Agent 可闭环 | middle | 单 Agent + 工具循环（隐式 DAG，1 节点） | 3~15s | 2K~10K |
| L3 | 复杂任务 | 多目标跨领域、多步骤依赖，需多 Agent 协同 | strong | 显式 DAG 编排，多节点并行/串行 | > 15s | > 10K |

**判级对调度路径的影响**：
- **L1**：跳过 Plan 阶段，直接调用 agent-runtime 单步执行（无 DAG 落库，task_instance.dag_id = NULL）
- **L2**：跳过显式 DAG 生成，但调用 Plan 接口获取「单节点 DAG」（含工具召回建议），落 task_dag（v1，1 节点）
- **L3**：完整走 AssessComplexity → Plan → ValidatePlan → DAG 持久化 → 分发

### 2.2 六维度打分模型

依据 PRD「从目标、执行、领域、知识、风险、上下文六个维度综合打分」，每维度 0~3 分，总分 0~18 分：

| 维度 | 0 分 | 1 分 | 2 分 | 3 分 | 权重 |
|---|---|---|---|---|---|
| **目标** (Goal) | 单一明确 | 单一但需细化 | 多目标同域 | 多目标跨域 | 1.0 |
| **执行** (Execution) | 无工具 | 1~2 次工具 | 3~5 次工具/多步推理 | 多步且含写操作 | 1.0 |
| **领域** (Domain) | 通用闲聊 | 单领域常识 | 单领域专业 | 跨领域专业 | 0.8 |
| **知识** (Knowledge) | 无需外部知识 | 内置知识即可 | 需 RAG 召回 | 需多源交叉验证 | 0.8 |
| **风险** (Risk) | 无副作用 | 只读内部数据 | 含 R2 写操作 | 含 R3 高危写/不可逆 | 1.2 |
| **上下文** (Context) | 单轮即解 | 需会话历史 | 需跨会话记忆 | 需多模态/长文档 | 0.6 |

**判级阈值**：
- 加权总分 ≤ 4 → L1
- 5 ~ 9 → L2
- ≥ 10 → L3
- **风险维度 = 3** 时强制 ≥ L2（即使其他维度低，写操作必须有兜底）
- **执行维度 = 3 且 风险维度 = 3** 时强制 L3

### 2.3 两级识别流程

依据 PRD「规则初筛（长度、关键词、历史匹配、场景标签）→ 轻量模型精判」：

```
┌─────────────────────────────────────────────────────────────────┐
│                    任务提交 (goal + title + schema)              │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
            ┌───────────────────────────────────┐
            │   Stage 1: 规则初筛 (RuleFilter)   │  < 5ms
            │   - 长度规则: goal 字符数 < 20 → L1 候选
            │   - 关键词规则: "查询/翻译/总结" → L1；"分析/对比/生成报告" → L2；"编排/协同/跨系统" → L3
            │   - 场景标签: scene_tags 命中 task_template → L2/L3 候选（模板优先）
            │   - 历史匹配: 同 tenant+相似 goal 历史判级结果复用
            └───────────────┬───────────────────┘
                            │
              ┌─────────────┴─────────────┐
              │                           │
        规则明确判级                   规则不确定
        (置信度 ≥ 0.9)                 (置信度 < 0.9)
              │                           ▼
              │           ┌───────────────────────────────────┐
              │           │   Stage 2: 模型精判 (ModelAssessor) │  200~500ms
              │           │   - 调用 model-gateway scene=intent, tier=light
              │           │   - 输入: goal + task_schema + 历史
              │           │   - 输出: 六维度打分 JSON + 总分 + 判级
              │           │   - 强制要求思维链 (enable_cot=true, temperature=0.1)
              │           └───────────────┬───────────────────┘
              │                           │
              └───────────┬───────────────┘
                          ▼
              ┌───────────────────────────┐
              │  最终判级 + 写 task_instance │
              │  .complexity              │
              │  记录判级依据到 task_step_log │
              │  (phase=think)            │
              └───────────────────────────┘
```

**模型精判 Prompt 模板核心结构**：
```text
你是任务复杂度评估器。请按六维度（目标/执行/领域/知识/风险/上下文）打分（0~3），
输出严格 JSON：
{
  "dimensions": {"goal":0-3, "execution":0-3, "domain":0-3, "knowledge":0-3, "risk":0-3, "context":0-3},
  "reasoning": "思维链简述",
  "level": "L1|L2|L3"
}
任务目标：{goal}
任务 Schema：{task_schema_json}
历史相似任务判级：{history_samples}
```

### 2.4 动态升级机制

PRD 要求「执行中支持动态升级复杂度」。设计两类升级触发：

| 触发场景 | 触发条件 | 升级动作 |
|---|---|---|
| **工具调用溢出** | L2 任务中 Agent 工具调用次数 ≥ 4 次 | 转为 L3，触发增量重规划生成多节点 DAG |
| **重试溢出** | L2 单步重试 ≥ 2 次仍失败 | 转为 L3，触发重规划拆解子路径 |
| **跨域识别** | Agent 运行时识别到需要其他领域能力（如代码 Agent 需调数据库工具） | 上报 RequestReplan(reason=cross_domain) |
| **成本超预期** | 已消耗 Token / 预估 Token > 200% | 升级 L3 并重新拆解剩余预算 |

**降级不允许**：复杂度只能升不能降，避免「偷懒跳过规划」导致质量回退。

升级动作走 `RequestReplan` 流程（见第 5 节），mode=incremental。

---

## 3. 两种规划模式

### 3.1 模式对比

| 维度 | 模板化规划 (Template) | 通用智能规划 (AI) |
|---|---|---|
| 适用场景 | 高频标准化场景（如周报生成、订单查询、邮件发送） | 长尾个性化场景（如「调研某新兴行业并产出投资建议」） |
| 触发条件 | task_template 表中匹配到 scene_tags 且 success_rate ≥ 0.85 | 模板未命中 或 模板参数 schema 不匹配 或 L3 优先智能规划 |
| 实现路径 | 模板匹配 → 参数填充 → ValidatePlan → 落库 | 强模型生成 DAG → 解析为标准结构 → ValidatePlan → 落库 |
| 耗时 | < 50ms | 3~15s（含模型推理） |
| 成本 | 极低（无模型调用） | 较高（强模型调用） |
| 灵活度 | 低（仅模板预定义路径） | 高（可处理任意需求） |
| 失败兜底 | 参数填充失败 → 回退智能规划 | 模型输出非法 JSON → 重试 2 次 → 仍失败 → WAITING_HUMAN |
| DAG source 字段 | `template` | `ai` |
| task_dag.template_id | 模板 ID | NULL |

### 3.2 模板匹配算法

伪代码：
```
function matchTemplate(task_schema, scene_tags):
    # 第一步：按 scene_tags 精确匹配
    candidates = SELECT * FROM task_template 
                 WHERE status=2  # 启用
                 AND scene_tags ∩ task.scene_tags != ∅
                 AND success_rate >= 0.85
                 ORDER BY usage_count DESC, success_rate DESC
    
    if candidates.empty():
        return null  # 走智能规划
    
    # 第二步：参数 schema 兼容性校验
    for t in candidates:
        if validateParams(task_schema, t.param_schema):
            return t
    
    return null
```

**模板参数填充示例**（task_template.dag_template 含占位符 `${var}`）：
```json
// 模板 dag_template 片段
{
  "nodes": [
    {"nodeId":"n1", "title":"查询${entity}数据", "inputs":{"entity":"${entity}"}}
  ]
}
// 填充后 (task_schema.params = {"entity":"订单"})
{"nodeId":"n1", "title":"查询订单数据", "inputs":{"entity":"订单"}}
```

### 3.3 通用智能规划完整 6 步流程

依据 PRD 第二节(三)2「完整规划生成流程」：

#### Step 1：任务语义形式化 (SemanticFormalizer)
- **输入**：原始 goal + title + 用户约束
- **动作**：调用强模型（scene=planning, tier=strong）提取结构化 Task Schema
- **输出**：
```json
{
  "overallGoal": "汇总本周销售数据生成周报，发送给 manager@xx.com",
  "deliverables": ["周报 PDF", "发送回执"],
  "constraints": {
    "deadline": "2026-06-27T00:00:00Z",
    "format": "pdf",
    "language": "zh-CN"
  },
  "resources": {
    "dataSources": ["sales_db", "crm_api"],
    "agentPool": ["data-analyst", "report-writer", "email-sender"]
  },
  "domain": "sales"
}
```
- **失败处理**：模型输出非法 JSON → 重试 2 次 → 仍失败 → 抛 PLAN_FORMALIZE_FAILED

#### Step 2：拆解策略决策 (DecomposeStrategyDecider)
- **决策树**：
  1. 模板命中且参数兼容 → 走模板化路径（Step 3a）
  2. 模板未命中 → 走智能拆解路径（Step 3b）
  3. 强制智能规划场景（用户显式 `prefer_template=false`）→ 走 Step 3b
- **输出**：`{strategy: "template"|"ai", templateId?: 123}`

#### Step 3a：模板化子任务生成 (TemplatePlanner)
- 读取 task_template.dag_template
- 参数填充（见 3.2）
- 跳转 Step 4

#### Step 3b：智能子任务生成 (AiPlanner)
- **Prompt 关键约束**：
  - 输出必须为标准 DAG JSON
  - 每个子任务必须原子化（单一职责、可独立验证）
  - 必须标注 abilityTags（用于 Agent 匹配）
  - 必须区分数据依赖 vs 逻辑依赖
  - 子任务数量上限：L3 ≤ 15 节点（防止规划爆炸）
- **模型调用**：scene=planning, tier=strong, temperature=0.2, enable_cot=true
- **输出**：原始 DAG 草稿（可能含语法错误，进入 Step 5 自检）

#### Step 4：依赖梳理与 DAG 构建 (DagBuilder)
- 解析子任务间的依赖关系（来自模板或模型输出）
- 分类边类型：`data`（输出参数映射）/ `logic`（执行顺序）/ `none`（无依赖，可并行）
- 调用环检测（见 4.3）
- 调用并行批次划分（见 4.2）
- 落 task_dag 表（version=1, source=template|ai）

#### Step 5：规划自检优化 (PlanValidator)
依据 PRD「完备性、原子性、效率、成本、容错五个维度」：

| 维度 | 校验项 | 失败动作 |
|---|---|---|
| **完备性** | 所有 deliverables 都有对应产出节点 | 补节点或抛 PLAN_INCOMPLETE |
| **原子性** | 单节点是否混合多个职责 | 拆分该节点（递归调用 AiPlanner） |
| **效率** | 是否存在可并行但被串行的节点对 | 调整 depType 为 none |
| **成本** | 预估总 Token 是否超 cost_limit_cent × 80% | 削减非核心节点或抛 PLAN_TOO_EXPENSIVE |
| **容错** | R3 写操作节点是否配置 maxRetries/undoAction | 强制注入补偿配置 |

自检不通过 → 修正后重试（最多 2 轮）→ 仍失败 → 抛 PLAN_VALIDATION_FAILED → orchestrator 转 WAITING_HUMAN

#### Step 6：输出标准化执行计划 (PlanSerializer)
- 序列化为 task_dag.nodes / edges / parallel_batches JSON（见 4.1）
- 返回 PlanResponse：
```json
{
  "dagJson": "{...}",
  "dagVersion": 1,
  "source": "ai",
  "templateId": null,
  "warnings": ["节点 n3 成本预估偏高，建议监控"]
}
```

### 3.4 规划结果落库契约

planning-service 完成 Plan 后写入：
- `task_dag`（新版本，dag_id 复用或新建）
- 回写 `task_instance.complexity`（如评估阶段未定，Plan 时强制确定）
- 回写 `task_instance.dag_id`

orchestrator 收到 PlanResponse 后：
- 校验 dag_json 格式合法性
- 调用 `ValidatePlan`（可选，Plan 内部已自检，此处为 orchestrator 防御性二次校验）
- 状态机流转：PLANNING → RUNNING（见第 6 节）

---

## 4. DAG 引擎设计

### 4.1 节点与边数据结构

严格遵循 [01-database 第 2.3/2.4 节](../01-database/database-schema-design.md#23-dag-节点-json-结构nodes-字段)定义的 JSON 结构。

#### DagNode POJO
```java
package com.agentplatform.task.common.model;

public class DagNode {
    private String nodeId;            // "n1"
    private String nodeType;          // "subtask" | "start" | "end" | "human_review"
    private String subtaskId;         // 子任务业务 ID
    private String title;             // 节点标题
    private Long agentId;             // 绑定 Agent（可选，未绑定时由调度器召回）
    private List<String> abilityTags; // ["query","order"]
    private Map<String, Object> inputs;
    private Map<String, Object> outputs;
    private NodeConfig config;        // maxRetries, timeoutMs, modelTier
    private List<String> dependsOn;   // 显式依赖节点 ID
    private NodeStatus status;        // pending|running|success|failed|skipped|blocked
    private Long startedAt;
    private Long finishedAt;
    private Integer retryCount;
    private String errorCode;
    private String errorMsg;
    
    public enum NodeStatus {
        PENDING, RUNNING, SUCCESS, FAILED, SKIPPED, BLOCKED
    }
}
```

```java
public class NodeConfig {
    private Integer maxRetries = 2;
    private Long timeoutMs = 30000L;
    private String modelTier = "middle";  // light|middle|strong
    private Boolean requireHumanReview = false;
    private String undoAction;            // 补偿动作 JSON
}
```

#### DagEdge POJO
```java
package com.agentplatform.task.common.model;

public class DagEdge {
    private String from;          // 源节点 ID
    private String to;            // 目标节点 ID
    private DepType depType;      // data|logic|none
    private Map<String, String> paramMapping; 
    // {"n0.outputs.orderId": "n1.inputs.orderId"}
    
    public enum DepType {
        DATA,   // 数据依赖：上游 outputs 必须作为下游 inputs
        LOGIC,  // 逻辑依赖：上游 success 后才执行下游
        NONE    // 无依赖（仅用于并行批次标记，不应出现在 edges 中）
    }
}
```

#### 完整 DAG 实例（task_dag.nodes + edges JSON）
```json
{
  "nodes": [
    {"nodeId":"n0","nodeType":"start","title":"任务起点","status":"success"},
    {"nodeId":"n1","nodeType":"subtask","title":"查询订单","agentId":1001,
     "abilityTags":["query","order"],"dependsOn":["n0"],"status":"success",
     "config":{"maxRetries":2,"timeoutMs":5000,"modelTier":"middle"}},
    {"nodeId":"n2","nodeType":"subtask","title":"生成报表","agentId":1002,
     "abilityTags":["report","generate"],"dependsOn":["n1"],"status":"running",
     "config":{"maxRetries":2,"timeoutMs":30000,"modelTier":"strong"}},
    {"nodeId":"n3","nodeType":"subtask","title":"发送邮件","agentId":1003,
     "abilityTags":["email","send"],"dependsOn":["n2"],"status":"pending",
     "config":{"maxRetries":1,"timeoutMs":10000,"modelTier":"light",
     "requireHumanReview":true,"undoAction":"{\"action\":\"recall_email\"}"}},
    {"nodeId":"n4","nodeType":"end","title":"任务终点","dependsOn":["n3"],"status":"pending"}
  ],
  "edges": [
    {"from":"n1","to":"n2","depType":"data",
     "paramMapping":{"n1.outputs.orderList":"n2.inputs.data"}},
    {"from":"n2","to":"n3","depType":"data",
     "paramMapping":{"n2.outputs.reportUrl":"n3.inputs.attachment"}},
    {"from":"n0","to":"n1","depType":"logic"},
    {"from":"n3","to":"n4","depType":"logic"}
  ]
}
```

### 4.2 并行批次划分算法

`task_dag.parallel_batches` 字段存储并行执行批次，算法基于**拓扑排序 + 层级划分**：

```
function computeParallelBatches(nodes, edges):
    # 构建入度表
    inDegree = {n.nodeId: 0 for n in nodes}
    adjList = {n.nodeId: [] for n in nodes}
    for e in edges:
        if e.depType in [DATA, LOGIC]:  # none 不构成依赖
            adjList[e.from].append(e.to)
            inDegree[e.to] += 1
    
    # 按层级（拓扑代数）划分批次
    batches = []
    currentBatch = [id for id, deg in inDegree.items() if deg == 0]
    processed = set()
    
    while currentBatch:
        batches.append(currentBatch)
        processed.update(currentBatch)
        nextBatch = []
        for nodeId in currentBatch:
            for neighbor in adjList[nodeId]:
                inDegree[neighbor] -= 1
                if inDegree[neighbor] == 0 and neighbor not in processed:
                    nextBatch.append(neighbor)
        currentBatch = nextBatch
    
    # 校验：若 processed != all nodes，说明有环
    if len(processed) != len(nodes):
        throw DAG_CYCLE_DETECTED
    
    return batches  # [["n0"],["n1"],["n2"],["n3"],["n4"]]
```

**并行批次执行规则**：
- 同批次节点**同时**投递到 `task.subtask.execute` Topic
- 当前批次**全部 success** 后才推进下一批次
- 任一节点 failed → 触发同批次其他节点 `cancel`（已发出的通过 RocketMQ 死信处理）+ 触发重规划评估
- 节点 `requireHumanReview=true` → 该批次执行完转 WAITING_HUMAN，等待人工 ack 后推进

**parallel_batches JSON 示例**：
```json
[["n0"],["n1"],["n2"],["n3"],["n4"]]
```

含并行场景：
```json
[["n0"],["n1","n2"],["n3"],["n4"]]
```
（n1、n2 无相互依赖，同批次并行执行）

### 4.3 环检测与可达性校验

#### 环检测（基于 DFS 三色标记法）
```
function detectCycle(nodes, edges):
    WHITE, GRAY, BLACK = 0, 1, 2
    color = {n.nodeId: WHITE for n in nodes}
    adjList = buildAdjList(edges)  # 仅 DATA/LOGIC 边
    
    function dfs(nodeId):
        color[nodeId] = GRAY
        for neighbor in adjList[nodeId]:
            if color[neighbor] == GRAY:
                return True  # 发现环
            if color[neighbor] == WHITE and dfs(neighbor):
                return True
        color[nodeId] = BLACK
        return False
    
    for n in nodes:
        if color[n.nodeId] == WHITE:
            if dfs(n.nodeId):
                return True
    return False
```

#### 可达性校验
- **起始可达性**：每个非 start 节点必须存在从 start 节点的路径
- **终止可达性**：每个非 end 节点必须存在到 end 节点的路径
- **参数依赖可达性**：edges.paramMapping 引用的 `nX.outputs.y` 中 nX 必须是 nY 的祖先节点

校验失败 → 抛 `DAG_VALIDATION_FAILED`，规划阶段拒绝落库。

### 4.4 DAG 版本管理

依据 [01-database 第 2.2 节](../01-database/database-schema-design.md#22-task_dag-dag-定义表)，task_dag 表的 `(dag_id, version)` 唯一索引实现版本管理：

| 场景 | 版本操作 | source 字段 |
|---|---|---|
| 首次规划 | 新建 dag_id，version=1 | template 或 ai |
| 增量重规划 | dag_id 不变，version+1，新插入行 | ai |
| 全量重规划 | dag_id 不变，version+1，新插入行（旧版本保留） | ai |
| 模板升级触发重新规划 | dag_id 不变，version+1 | template |

**版本读取规则**：
- orchestrator 始终读取 `MAX(version)` 对应当前行
- 历史版本仅用于审计、回滚、Badcase 分析
- `task_replan_log.old_dag_version` 和 `new_dag_version` 记录变更前后版本

**DAG 运行态缓存**：
- 当前版本 DAG 加载到 Redis（Key: `dag:{taskId}:current`），TTL = 任务最大执行时长
- 每次节点状态变更同步更新 Redis + 异步刷 MySQL
- 节点 outputs 实时写 Redis（Key: `dag:{taskId}:node:{nodeId}:outputs`），供下游节点取参

---

## 5. 动态重规划机制

### 5.1 触发条件

依据 PRD 第二节(三)2「动态重规划机制 - 触发条件」：

| 触发条件 | 检测方 | 触发源 | 默认 mode |
|---|---|---|---|
| **子任务连续失败无降级方案** | orchestrator | ReportSubtaskResult(status=failed, retryCount≥maxRetries) | incremental |
| **校验不通过无法修复** | quality-service / orchestrator | StepReport(status=verify_failed) | incremental |
| **核心依赖失效** | orchestrator | 上游节点 outputs 缺失关键字段 | incremental |
| **用户需求变更** | 用户/gateway | CancelTask(reason=change_requirement) → 转 RequestReplan | full |
| **复杂度动态升级** | orchestrator | 监控阈值触发（见 2.4） | incremental |
| **成本超限** | orchestrator | cost_used_cent > cost_limit_cent × 90% | incremental（削减节点） |
| **Agent 跨域能力不足** | agent-runtime | ReportSubtaskDone(status=failed, reason=cross_domain) | incremental |

### 5.2 增量 vs 全量重规划

| 维度 | 增量重规划 (incremental) | 全量重规划 (full) |
|---|---|---|
| 触发频率 | 默认优先采用 | 极端场景，限次 |
| 已完成节点 | 保留（status=success 的不重新执行） | 全部重置为 pending（除已成功且不可逆的写操作节点） |
| 失败节点 | 标记为 pending 重新规划 | 同左 |
| 未执行节点 | 重新生成（可能合并/拆分/删除） | 重新生成 |
| DAG 版本 | version+1 | version+1 |
| 实现路径 | 复用旧 nodes + 新增/调整部分节点 + 重新计算 edges + 重算 parallel_batches | 全量调用 Plan(strategy=ai, prefer_template=false) |
| 成本 | 低（仅规划变更部分） | 高（重新规划 + 部分重跑） |

**增量重规划算法伪代码**：
```
function incrementalReplan(oldDag, failedNodeIds, triggerReason):
    # 1. 标记已成功节点为"冻结"
    frozenNodes = oldDag.nodes.filter(n => n.status == SUCCESS)
    
    # 2. 识别受影响节点（failed + 其所有下游）
    affectedNodes = computeDownstream(oldDag, failedNodeIds)
    
    # 3. 调用 planning-service 重新规划受影响部分
    replanRequest = {
        taskId: oldDag.taskId,
        frozenNodes: frozenNodes,
        affectedNodes: affectedNodes,
        failureContext: {failedNodeIds, triggerReason, errorLogs}
    }
    newSubDag = planningService.replan(replanRequest)  # gRPC
    
    # 4. 合并：保留 frozenNodes + 替换 affectedNodes
    mergedDag = merge(oldDag, newSubDag, frozenNodes)
    
    # 5. 重新校验（环检测、可达性、并行批次）
    validateDag(mergedDag)
    
    # 6. 新版本落库
    newVersion = oldDag.version + 1
    saveTaskDag(dagId=oldDag.dagId, version=newVersion, dag=mergedDag, source="ai")
    
    # 7. 写 task_replan_log
    saveReplanLog(taskId, replanNo=++replanCount, mode="incremental",
                  triggerReason, failedNodeIds, oldDag.version, newVersion, cost)
    
    return mergedDag
```

**全量重规划约束**：
- 仅当增量重规划失败 2 次后允许触发
- 单任务全量重规划上限 = 1 次（防死循环）
- 全量重规划前需 snapshot 当前 DAG（用于回滚）

### 5.3 重规划熔断

依据 PRD「单任务设置重规划次数上限与成本上限，超限转入人工介入」：

| 熔断维度 | 阈值 | 触发动作 |
|---|---|---|
| 增量重规划次数 | ≥ 3 次 | 升级为全量重规划 |
| 全量重规划次数 | ≥ 1 次 | 转 WAITING_HUMAN |
| 累计重规划次数（增量+全量） | ≥ 4 次 | 转 FAILED + 告警 |
| 重规划累计成本 | > cost_limit_cent × 30% | 转 WAITING_HUMAN |
| 重规划耗时 | 单次 > 60s | 中断 + 转 FAILED |
| 单节点连续重规划 | ≥ 2 次仍失败 | 跳过该节点（如可降级）或转人工 |

熔断后状态机流转：REPLANNING → WAITING_HUMAN 或 FAILED（见第 6 节）。

### 5.4 重规划日志契约

每次重规划必须写 `task_replan_log`（[01-database 第 2.7 节](../01-database/database-schema-design.md#27-task_replan_log-重规划日志表)）：

```json
{
  "taskId": "tk_yyy",
  "replanNo": 2,
  "mode": "incremental",
  "triggerReason": "node n3 failed 2 times: tool email_send returned TIMEOUT",
  "failedNodeIds": ["n3"],
  "oldDagVersion": 1,
  "newDagVersion": 2,
  "costCent": 850,
  "createdAt": "2026-06-26T10:05:00.000Z"
}
```

---

## 6. 任务状态机

### 6.1 状态列表

依据任务需求文档要求，定义 10 个状态：

| 状态 | 含义 | 持有方 | 是否终态 |
|---|---|---|---|
| `PENDING` | 任务已提交，待复杂度评估与规划 | orchestrator | 否 |
| `PLANNING` | 调用 planning-service 生成 DAG 中 | orchestrator | 否 |
| `RUNNING` | DAG 已就绪，正在分发执行 | orchestrator | 否 |
| `SUBTASK_RUNNING` | 子任务执行中（部分节点 running） | orchestrator | 否 |
| `WAITING_HUMAN` | 等待人工介入（R3 审批/重规划超限/用户申诉） | orchestrator | 否 |
| `REPLANNING` | 正在执行增量/全量重规划 | orchestrator | 否 |
| `SUCCESS` | 所有节点成功，结果聚合完成 | orchestrator | 是 |
| `FAILED` | 任务失败（无法重规划/熔断/致命异常） | orchestrator | 是 |
| `CANCELLED` | 用户主动取消 | orchestrator | 是 |
| `TIMEOUT` | 任务整体超时（cost_limit 或 deadline 触发） | orchestrator | 是 |

### 6.2 合法状态流转矩阵

| 从 \ 到 | PENDING | PLANNING | RUNNING | SUBTASK_RUNNING | WAITING_HUMAN | REPLANNING | SUCCESS | FAILED | CANCELLED | TIMEOUT |
|---|---|---|---|---|---|---|---|---|---|---|
| **PENDING** | - | ✓ | ✓(L1跳规划) | - | - | - | - | ✓ | ✓ | ✓ |
| **PLANNING** | - | - | ✓ | - | ✓ | - | - | ✓ | ✓ | ✓ |
| **RUNNING** | - | - | - | ✓ | ✓ | ✓ | - | ✓ | ✓ | ✓ |
| **SUBTASK_RUNNING** | - | - | - | - | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **WAITING_HUMAN** | - | - | ✓(人工恢复) | - | - | ✓(人工触发) | ✓(人工确认) | ✓ | ✓ | - |
| **REPLANNING** | - | - | ✓(全量重规划完成) | ✓(增量完成继续跑) | ✓(熔断) | - | - | ✓ | ✓ | ✓ |
| **SUCCESS** | - | - | - | - | - | - | - | - | - | - |
| **FAILED** | - | - | - | - | ✓(人工申诉) | - | - | - | - | - |
| **CANCELLED** | - | - | - | - | - | - | - | - | - | - |
| **TIMEOUT** | - | - | - | - | ✓(人工申诉) | - | - | - | - | - |

**流转规则说明**：
1. **PENDING → RUNNING (L1 跳规划)**：L1 任务不调用 Plan，直接进入 RUNNING（DAG 为空，单 Agent 直跑）
2. **RUNNING → SUBTASK_RUNNING**：首批节点投递后立即转入
3. **SUBTASK_RUNNING → SUCCESS**：所有节点 success 且无下游 → 触发结果聚合 → SUCCESS
4. **任一运行态 → CANCELLED**：用户主动取消，已投递的子任务通过 RocketMQ 死信队列异步清理
5. **WAITING_HUMAN → RUNNING**：人工修正后恢复执行（不重规划）
6. **WAITING_HUMAN → REPLANNING**：人工指定重规划路径
7. **FAILED → WAITING_HUMAN**：用户申诉触发人工兜底（仅 R3 高风险任务允许）

**禁止流转**：
- 任何终态 → 非终态（除 FAILED/TIMEOUT → WAITING_HUMAN 的申诉路径）
- SUCCESS → 任何状态
- 跳过 PLANNING 直接进入 SUBTASK_RUNNING（L1 除外）

### 6.3 状态变更审计

每次状态流转写 `task_state_change` 表（[01-database 第 2.6 节](../01-database/database-schema-design.md#26-task_state_change-状态流转审计表)）：

```json
{
  "taskId": "tk_yyy",
  "fromStatus": "RUNNING",
  "toStatus": "SUBTASK_RUNNING",
  "trigger": "auto",
  "operator": "system",
  "reason": "batch 1 dispatched, 3 nodes running",
  "traceId": "abc123",
  "createdAt": "2026-06-26T10:00:01.000Z"
}
```

**具体时序图（包含子任务回调、重规划、人工介入）留待 [08-flow 文档](../08-flow/state-machines-and-sequences.md) 绘制 Mermaid 图。**

---

## 7. 子任务分发与并行调度

### 7.1 RocketMQ Topic 设计

依据 [02-api 第 11 节](../02-api/api-specification.md#11-异步事件规范rocketmq-topics)：

| Topic | 生产者 | 消费者 | 消息类型 | 用途 |
|---|---|---|---|---|
| `task.subtask.execute` | task-orchestrator | agent-runtime | JSON | 分发子任务 |
| `task.subtask.done` | agent-runtime | task-orchestrator | JSON | 子任务完成上报 |
| `task.state.change` | task-orchestrator | session / observability | JSON | 任务状态广播 |
| `task.subtask.cancel` | task-orchestrator | agent-runtime | JSON | 取消已分发的子任务 |

### 7.2 子任务分发消息格式

```json
// Topic: task.subtask.execute
// 消息 Key: {taskId}:{nodeId}  (用于幂等去重)
// 消息 Tag: {tenantId}  (用于消费者按租户过滤)
{
  "eventId": "ev_uuid_v4",
  "eventType": "task.subtask.execute",
  "eventTime": "2026-06-26T10:00:01.000Z",
  "traceId": "trace_xxx",
  "tenantId": 1001,
  "payload": {
    "taskId": "tk_yyy",
    "dagId": 10086,
    "dagVersion": 1,
    "nodeId": "n1",
    "subtaskId": "st_001",
    "agentId": 1001,
    "title": "查询用户订单",
    "abilityTags": ["query", "order"],
    "inputs": {"userId": "u_123"},
    "config": {
      "maxRetries": 2,
      "timeoutMs": 30000,
      "modelTier": "middle",
      "requireHumanReview": false
    },
    "deadline": "2026-06-26T10:05:00Z",
    "costBudgetCent": 500
  }
}
```

### 7.3 并行批次调度流程

```
┌─────────────────────────────────────────────────────────────────┐
│  orchestrator: 推进到下一并行批次                                  │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
            ┌───────────────────────────────────┐
            │ 1. 从 parallel_batches 取下一批次   │
            │    (上一批次全部 success)            │
            └───────────────┬───────────────────┘
                            ▼
            ┌───────────────────────────────────┐
            │ 2. 参数注入：解析 edges.paramMapping │
            │    将上游节点 outputs 注入下游 inputs │
            │    从 Redis: dag:{taskId}:node:{id}:outputs 读取
            └───────────────┬───────────────────┘
                            ▼
            ┌───────────────────────────────────┐
            │ 3. Agent 匹配（若节点未绑定 agentId） │
            │    调用 agent-repo.RecallByAbility   │
            │    按 abilityTags + scene 匹配       │
            └───────────────┬───────────────────┘
                            ▼
            ┌───────────────────────────────────┐
            │ 4. 同批次节点同时投递                │
            │    producer.send(topic=task.subtask.execute, 
            │      keys=[{taskId}:{nodeId}], 
            │      tag={tenantId},
            │      payload=SubtaskExecuteMsg)
            │    使用 RocketMQ 事务消息保证 DB+MQ 一致性
            └───────────────┬───────────────────┘
                            ▼
            ┌───────────────────────────────────┐
            │ 5. 更新节点 status=running          │
            │    状态机转 SUBTASK_RUNNING          │
            │    更新 Redis DAG 缓存               │
            └───────────────┬───────────────────┘
                            ▼
            ┌───────────────────────────────────┐
            │ 6. 等待 task.subtask.done 消费       │
            │    设置超时监控（XXL-Job 扫描）       │
            └───────────────────────────────────┘
```

### 7.4 子任务完成回调处理

orchestrator 消费 `task.subtask.done` 消息后的处理逻辑：

```java
// 伪代码
function onSubtaskDone(SubtaskDoneEvent event):
    # 幂等校验
    if event_consume_log.exists(event.eventId):
        return Ack
    
    task = task_instance.findByTaskId(event.taskId)
    
    # 1. 更新节点状态与 outputs
    dag = loadDag(task.dagId, task.dagVersion)
    node = dag.findNode(event.nodeId)
    node.status = event.status  # success|failed
    node.outputs = event.outputs
    node.finishedAt = now()
    saveNodeToRedis(task.taskId, node)
    
    # 2. 写 task_step_log（每个 phase 一条记录）
    for step in event.steps:
        task_step_log.insert(step)
    
    # 3. 累加成本与 Token
    task.cost_used_cent += event.costCent
    task.token_used += event.tokenUsed
    if task.cost_used_cent > task.cost_limit_cent:
        triggerCostCircuitBreaker(task)
        return Ack
    
    # 4. 根据节点结果决定下一步
    if event.status == SUCCESS:
        # 检查同批次是否全部完成
        batch = dag.findBatchOfNode(event.nodeId)
        if batch.allSuccess():
            advanceToNextBatch(task, dag)
        else:
            # 等待同批次其他节点
            pass
    elif event.status == FAILED:
        handleNodeFailure(task, dag, node, event)
    
    return Ack
```

### 7.5 失败节点处理决策

```
function handleNodeFailure(task, dag, node, event):
    # 1. 子任务级重试判断
    if node.retryCount < node.config.maxRetries:
        node.retryCount += 1
        # 重置上下文重跑（依据 PRD 第三节·子任务级最多 2 次）
        redeliverSubtask(task, node, 
            additionalContext={"previousError": event.errorMsg, 
                              "hint": "avoid repeating the same mistake"})
        return
    
    # 2. 重试用尽，判断是否有降级路径
    if hasFallbackAgent(node):
        switchAgentAndRetry(task, node)
        return
    
    # 3. 触发重规划
    if canReplan(task):
        task.status = REPLANNING
        replanner.triggerIncremental(task, dag, [node.nodeId], event.errorMsg)
    else:
        # 4. 熔断：转人工或失败
        if task.constraints.allowHumanFallback:
            task.status = WAITING_HUMAN
            notifyUser(task, "subtask failed, manual intervention required")
        else:
            task.status = FAILED
            task.error_code = "SUBTASK_EXHAUSTED"
            task.error_msg = event.errorMsg
```

---

## 8. 核心类设计

本节给出关键 Java 类签名（不含方法体），包名遵循 [00-overview 第 4 节](../00-overview/tech-stack-and-architecture.md#4-项目目录结构) 的 `com.agentplatform.task.*` 约定。

### 8.1 task-orchestrator 模块

#### 8.1.1 gRPC 服务实现
```java
package com.agentplatform.task.orchestrator.api;

import com.agentplatform.task.proto.TaskOrchestratorGrpc;
import com.agentplatform.task.proto.SubmitTaskRequest;
import com.agentplatform.task.proto.SubmitTaskResponse;
// ... 其他 import

/**
 * TaskOrchestrator gRPC 服务端实现。
 * 对应 [02-api 第 6 节] TaskOrchestrator 服务定义。
 */
@GrpcService
public class TaskOrchestratorGrpcImpl extends TaskOrchestratorGrpc.TaskOrchestratorImplBase {
    
    public void submitTask(SubmitTaskRequest request, StreamObserver<SubmitTaskResponse> responseObserver);
    public void getTask(GetTaskRequest request, StreamObserver<TaskDetail> responseObserver);
    public void reportSubtaskResult(SubtaskResult request, StreamObserver<ReportAck> responseObserver);
    public void cancelTask(CancelTaskRequest request, StreamObserver<CancelAck> responseObserver);
    public void requestReplan(ReplanRequest request, StreamObserver<ReplanResponse> responseObserver);
}
```

#### 8.1.2 状态机引擎
```java
package com.agentplatform.task.orchestrator.statemachine;

import com.agentplatform.task.common.enums.TaskStatus;

/**
 * 任务状态机。
 * 校验状态流转合法性，写 task_state_change 审计。
 */
public interface TaskStateMachine {
    
    /**
     * 执行状态流转。
     * @param taskId 任务 ID
     * @param target 目标状态
     * @param trigger auto/manual/system
     * @param reason 变更原因
     * @throws IllegalStateException 非法流转
     */
    void transit(String taskId, TaskStatus target, String trigger, String reason);
    
    /**
     * 校验流转是否合法（不实际执行）。
     */
    boolean canTransit(TaskStatus from, TaskStatus to);
    
    /**
     * 查询当前状态。
     */
    TaskStatus currentStatus(String taskId);
}

/**
 * 基于内存流转矩阵的实现。
 * 矩阵定义见第 6.2 节。
 */
@Service
public class InMemoryTaskStateMachine implements TaskStateMachine {
    // ...
}
```

#### 8.1.3 DAG 执行器
```java
package com.agentplatform.task.orchestrator.dag;

import com.agentplatform.task.common.model.DagNode;
import com.agentplatform.task.common.model.DagEdge;

/**
 * DAG 运行时抽象，封装节点/边查询与状态变更。
 */
public interface DagRuntime {
    
    /**
     * 加载某任务的当前版本 DAG。
     */
    DagSnapshot loadCurrent(String taskId);
    
    /**
     * 更新节点状态（同步 Redis + 异步 MySQL）。
     */
    void updateNodeStatus(String taskId, String nodeId, DagNode.NodeStatus status);
    
    /**
     * 写入节点 outputs（供下游 paramMapping 解析）。
     */
    void writeNodeOutputs(String taskId, String nodeId, Map<String, Object> outputs);
    
    /**
     * 读取节点 inputs（已注入上游 outputs）。
     */
    Map<String, Object> readNodeInputs(String taskId, String nodeId);
    
    /**
     * 获取下一并行批次。
     */
    List<String> nextBatch(String taskId);
}

/**
 * DAG 执行器，负责推进并行批次。
 */
public interface DagExecutor {
    
    /**
     * 启动 DAG 执行（首个批次）。
     */
    void start(String taskId);
    
    /**
     * 推进到下一批次（当前批次全成功时调用）。
     */
    void advanceBatch(String taskId);
    
    /**
     * 处理节点失败。
     */
    void handleNodeFailure(String taskId, String nodeId, String errorCode, String errorMsg);
    
    /**
     * 取消 DAG 执行（用户取消或超时）。
     */
    void cancel(String taskId, String reason);
}

@Service
public class DagExecutorImpl implements DagExecutor {
    // 依赖: DagRuntime, SubtaskDispatcher, TaskStateMachine, Replanner, CostMonitor
}
```

#### 8.1.4 DAG 校验器
```java
package com.agentplatform.task.orchestrator.dag;

/**
 * DAG 结构合法性校验器。
 * 在落库前与重规划后调用。
 */
public interface DagValidator {
    
    /**
     * 综合校验：环检测 + 可达性 + 参数依赖。
     * @throws DagValidationException 含具体错误信息
     */
    void validate(List<DagNode> nodes, List<DagEdge> edges);
    
    boolean hasCycle(List<DagNode> nodes, List<DagEdge> edges);
    boolean checkReachability(List<DagNode> nodes, List<DagEdge> edges);
    boolean checkParamMapping(List<DagNode> nodes, List<DagEdge> edges);
}

@Service
public class DefaultDagValidator implements DagValidator {
    // ...
}
```

#### 8.1.5 子任务分发器
```java
package com.agentplatform.task.orchestrator.dispatcher;

import com.agentplatform.task.common.model.DagNode;

/**
 * 子任务分发器，基于 RocketMQ 异步投递。
 */
public interface SubtaskDispatcher {
    
    /**
     * 投递单个子任务到 task.subtask.execute Topic。
     * 使用事务消息保证 DB 节点状态变更与 MQ 投递的最终一致性。
     */
    String dispatch(String taskId, DagNode node);
    
    /**
     * 批量投递同批次节点（并行）。
     */
    List<String> dispatchBatch(String taskId, List<DagNode> nodes);
    
    /**
     * 取消已投递的子任务（发 task.subtask.cancel）。
     */
    void cancel(String taskId, String nodeId, String reason);
}

@Service
public class RocketMqSubtaskDispatcher implements SubtaskDispatcher {
    // 依赖: RocketMQTemplate, TransactionMQProducer, DagRuntime
}
```

#### 8.1.6 重规划触发器
```java
package com.agentplatform.task.orchestrator.replanner;

import com.agentplatform.task.common.enums.ReplanMode;

/**
 * 重规划触发器与熔断控制。
 */
public interface Replanner {
    
    /**
     * 触发增量重规划。
     */
    ReplanResult triggerIncremental(String taskId, String dagId, 
                                    List<String> failedNodeIds, String triggerReason);
    
    /**
     * 触发全量重规划（需先 snapshot 当前 DAG）。
     */
    ReplanResult triggerFull(String taskId, String triggerReason);
    
    /**
     * 检查是否允许重规划（熔断判断）。
     */
    ReplanQuota checkQuota(String taskId);
    
    /**
     * 熔断后转人工或失败。
     */
    void circuitBreak(String taskId, String reason);
}

public class ReplanResult {
    private boolean success;
    private Integer newDagVersion;
    private String errorCode;
    private String errorMsg;
    private Long costCent;
}

public class ReplanQuota {
    private int remainingIncremental;  // 剩余增量次数
    private int remainingFull;          // 剩余全量次数
    private Long remainingBudgetCent;   // 剩余重规划预算
    private boolean allowReplan;
}

@Service
public class DefaultReplanner implements Replanner {
    // 依赖: PlanningServiceClient (gRPC), DagRuntime, TaskStateMachine, ReplanLogRepository
}
```

#### 8.1.7 子任务完成回调
```java
package com.agentplatform.task.orchestrator.callback;

/**
 * 消费 task.subtask.done Topic。
 */
@RocketMQMessageListener(
    topic = "${rocketmq.topics.subtask-done}",
    consumerGroup = "${rocketmq.groups.orchestrator-cg}",
    selectorExpression = "*"  // 按租户 Tag 过滤可在此配置
)
public class SubtaskDoneConsumer implements RocketMQListener<SubtaskDoneEvent> {
    
    @Override
    public void onMessage(SubtaskDoneEvent event);
    // 内部委托给 SubtaskDoneHandler
}

public interface SubtaskDoneHandler {
    /**
     * 处理子任务完成事件。
     * @see 第 7.4 节处理逻辑伪代码
     */
    void handle(SubtaskDoneEvent event);
}

@Service
public class DefaultSubtaskDoneHandler implements SubtaskDoneHandler {
    // 依赖: DagRuntime, DagExecutor, TaskStateMachine, CostMonitor, StepLogRepository, IdempotentLogRepository
}
```

#### 8.1.8 成本与超时监控
```java
package com.agentplatform.task.orchestrator.replanner;

/**
 * 成本与超时熔断监控器。
 */
public interface CostMonitor {
    
    /**
     * 累加成本（线程安全，使用 Redis INCR）。
     */
    void accumulate(String taskId, long costCent, int tokenUsed);
    
    /**
     * 检查是否触发成本熔断。
     * @return true 表示已超限，应停止执行
     */
    boolean checkCircuitBreak(String taskId);
    
    /**
     * 检查任务是否超时（基于 deadline 与 cost_limit）。
     */
    boolean checkTimeout(String taskId);
}

/**
 * XXL-Job 定时任务：扫描超时任务。
 * 每 30s 执行一次，将超时任务转 TIMEOUT 状态。
 */
@Component
public class TaskTimeoutScanner {
    @XxlJob("scanTimeoutTasks")
    public void execute();
}
```

### 8.2 planning-service 模块

#### 8.2.1 gRPC 服务实现
```java
package com.agentplatform.task.planning.api;

@GrpcService
public class PlanningServiceGrpcImpl extends PlanningServiceGrpc.PlanningServiceImplBase {
    
    public void assessComplexity(AssessRequest request, StreamObserver<AssessResponse> responseObserver);
    public void plan(PlanRequest request, StreamObserver<PlanResponse> responseObserver);
    public void validatePlan(ValidateRequest request, StreamObserver<ValidateResponse> responseObserver);
}
```

#### 8.2.2 复杂度评估器
```java
package com.agentplatform.task.planning.assessor;

import com.agentplatform.task.common.enums.ComplexityLevel;

/**
 * 复杂度评估器，两级识别流程编排。
 * @see 第 2.3 节
 */
public interface ComplexityAssessor {
    
    /**
     * 评估任务复杂度。
     * @param taskId 任务 ID
     * @param goal 任务目标
     * @param taskSchemaJson Task Schema JSON
     * @param sceneTags 场景标签
     * @return 评估结果
     */
    AssessResult assess(String taskId, String goal, String taskSchemaJson, List<String> sceneTags);
}

public class AssessResult {
    private ComplexityLevel level;       // L1/L2/L3
    private Map<String, Integer> dimensions;  // 六维度打分
    private double weightedScore;
    private String reasoning;
    private String source;                // rule | model
    private double confidence;
}

/**
 * 规则初筛器。
 */
@Component
public class RuleBasedFilter {
    /**
     * 基于长度、关键词、场景标签、历史匹配做初筛。
     * @return null 表示规则不确定，需模型精判
     */
    public AssessResult quickFilter(String goal, List<String> sceneTags, String tenantId);
}

/**
 * 模型精判器。
 */
@Component
public class ModelBasedAssessor {
    /**
     * 调用 model-gateway scene=intent, tier=light 做精判。
     */
    public AssessResult modelAssess(String goal, String taskSchemaJson, List<String> historySamples);
}
```

#### 8.2.3 规划器
```java
package com.agentplatform.task.planning.planner;

import com.agentplatform.task.common.model.DagNode;
import com.agentplatform.task.common.model.DagEdge;

/**
 * 规划器抽象，两种实现：模板化 / 智能规划。
 */
public interface Planner {
    
    /**
     * 生成规划。
     */
    PlanResult plan(PlanContext context);
    
    /**
     * 该规划器是否支持当前任务。
     */
    boolean supports(PlanContext context);
}

public class PlanContext {
    private String taskId;
    private String goal;
    private String taskSchemaJson;
    private ComplexityLevel complexity;
    private boolean preferTemplate;
    private List<String> sceneTags;
    private Long costLimitCent;
    // 增量重规划上下文
    private List<DagNode> frozenNodes;
    private List<String> failedNodeIds;
    private String failureContext;
}

public class PlanResult {
    private List<DagNode> nodes;
    private List<DagEdge> edges;
    private List<List<String>> parallelBatches;
    private String source;       // template | ai
    private String templateId;
    private List<String> warnings;
}

/**
 * 模板化规划器。
 */
@Service
public class TemplatePlanner implements Planner {
    // 依赖: TemplateRepository, ParamFiller
}

/**
 * 通用智能规划器。
 */
@Service
public class AiPlanner implements Planner {
    // 依赖: ModelGatewayClient, DagBuilder, PlanValidator
}

/**
 * 规划器路由：依据 PlanContext 选择具体 Planner。
 */
@Service
public class PlannerRouter {
    /**
     * 选择策略：
     * 1. preferTemplate=true 且模板命中 → TemplatePlanner
     * 2. 否则 → AiPlanner
     */
    public Planner select(PlanContext context);
}
```

#### 8.2.4 DAG 构建器
```java
package com.agentplatform.task.planning.planner;

/**
 * 将规划器输出的子任务列表 + 依赖关系转为标准 DAG。
 */
@Component
public class DagBuilder {
    
    /**
     * 构建标准 DAG。
     * 1. 添加 start/end 节点
     * 2. 分类边类型 (DATA/LOGIC/NONE)
     * 3. 调用 DagValidator 校验
     * 4. 调用 ParallelBatchCalculator 计算并行批次
     */
    public PlanResult build(List<DagNode> subtaskNodes, List<DagEdge> dependencies);
}

/**
 * 并行批次计算器（实现 4.2 节算法）。
 */
@Component
public class ParallelBatchCalculator {
    public List<List<String>> calculate(List<DagNode> nodes, List<DagEdge> edges);
}
```

#### 8.2.5 规划自检器
```java
package com.agentplatform.task.planning.validator;

/**
 * 规划自检器，五维度校验。
 * @see 第 3.3 节 Step 5
 */
public interface PlanValidator {
    
    /**
     * 综合自检。
     * @return 校验结果，含失败原因与建议修正
     */
    ValidateResult validate(PlanResult plan, PlanContext context);
}

public class ValidateResult {
    private boolean passed;
    private List<String> errors;        // 阻塞性问题
    private List<String> warnings;      // 非阻塞警告
    private List<FixSuggestion> fixes; // 自动修正建议
}

public class FixSuggestion {
    private String nodeId;
    private String dimension;  // completeness|atomicity|efficiency|cost|fault_tolerance
    private String suggestion;
    private Object suggestedChange;
}

@Service
public class FiveDimensionPlanValidator implements PlanValidator {
    // 五个独立校验器
    @Autowired private CompletenessValidator completenessValidator;
    @Autowired private AtomicityValidator atomicityValidator;
    @Autowired private EfficiencyValidator efficiencyValidator;
    @Autowired private CostValidator costValidator;
    @Autowired private FaultToleranceValidator faultToleranceValidator;
}
```

#### 8.2.6 模板仓库
```java
package com.agentplatform.task.planning.template;

/**
 * 任务模板仓库。
 */
public interface TemplateRepository {
    
    /**
     * 按 scene_tags 检索启用中的模板。
     */
    List<TaskTemplate> findBySceneTags(List<String> sceneTags);
    
    /**
     * 查询单个模板。
     */
    TaskTemplate findByTemplateId(String templateId);
    
    /**
     * 增加使用次数（异步）。
     */
    void incrementUsage(String templateId);
    
    /**
     * 更新成功率（异步，任务完成后回调）。
     */
    void updateSuccessRate(String templateId, boolean success);
}

/**
 * 模板参数填充器。
 * 支持 ${var} 占位符替换与参数 schema 校验。
 */
@Component
public class ParamFiller {
    /**
     * @throws ParamFillException 参数缺失或类型不匹配
     */
    public List<DagNode> fill(List<DagNode> templateNodes, Map<String, Object> params, 
                              JsonSchema paramSchema);
}

/**
 * 模板匹配器。
 */
@Component
public class TemplateMatcher {
    /**
     * 匹配最合适的模板。
     * @return null 表示无匹配
     */
    public TaskTemplate match(String taskSchemaJson, List<String> sceneTags);
}
```

### 8.3 共享模型与枚举

```java
package com.agentplatform.task.common.enums;

public enum TaskStatus {
    PENDING, PLANNING, RUNNING, SUBTASK_RUNNING, 
    WAITING_HUMAN, REPLANNING, 
    SUCCESS, FAILED, CANCELLED, TIMEOUT
}

public enum ComplexityLevel {
    L1, L2, L3
}

public enum ReplanMode {
    INCREMENTAL, FULL
}

public enum DagSource {
    TEMPLATE, AI
}

public enum NodeStatus {
    PENDING, RUNNING, SUCCESS, FAILED, SKIPPED, BLOCKED
}

public enum DepType {
    DATA, LOGIC, NONE
}

public enum TaskPhase {
    THINK, ACT, OBSERVE, REFLECT
}
```

```java
package com.agentplatform.task.common.model;

/**
 * DAG 快照，运行时全量加载到内存。
 */
public class DagSnapshot {
    private String taskId;
    private Long dagId;
    private Integer version;
    private List<DagNode> nodes;
    private List<DagEdge> edges;
    private List<List<String>> parallelBatches;
    private String source;
    private String templateId;
}

/**
 * 标准 Task Schema（与 task_instance.task_schema JSON 对应）。
 */
public class TaskSchema {
    private String overallGoal;
    private List<String> deliverables;
    private TaskConstraints constraints;
    private TaskResources resources;
    private String domain;
}

public class TaskConstraints {
    private String deadline;
    private String format;
    private String language;
    private Boolean allowHumanFallback;
}

public class TaskResources {
    private List<String> dataSources;
    private List<String> agentPool;
}
```

---

## 9. 关键时序（文字描述）

具体 Mermaid 时序图留待 [08-flow 文档](../08-flow/state-machines-and-sequences.md)，本节用文字描述主流程。

### 9.1 任务提交流程（PENDING → SUCCESS 完整路径）

1. **用户提交任务**：用户通过 gateway POST `/api/v1/tasks`，gateway 转发到 task-orchestrator 的 gRPC `SubmitTask`。
2. **任务落库**：orchestrator 创建 task_instance（status=PENDING），生成 task_id。
3. **复杂度评估**：orchestrator 调用 planning-service `AssessComplexity`（gRPC 同步），planning-service 执行两级识别（规则初筛 → 模型精判），返回 L1/L2/L3 与六维度评分。orchestrator 回写 task_instance.complexity，状态转 PLANNING。
4. **规划生成**：orchestrator 调用 planning-service `Plan`（gRPC 同步），planning-service 内部执行 6 步流程（语义形式化 → 拆解策略决策 → 子任务生成 → DAG 构建 → 自检 → 输出）。返回 PlanResponse（含 dagJson、version、source）。
5. **DAG 落库**：planning-service 写 task_dag（version=1），orchestrator 回写 task_instance.dag_id。状态转 RUNNING。
6. **首批次分发**：DagExecutor 加载 DAG，计算首个并行批次，对批次内每个节点：
   - 解析 paramMapping 注入上游 outputs（首批次无上游）
   - 若节点未绑定 agentId，调用 agent-repo 按 abilityTags 召回 Agent
   - 通过 RocketMQ 事务消息投递到 `task.subtask.execute` Topic
   - 更新节点 status=running
7. **状态切换**：首批投递后，task 状态转 SUBTASK_RUNNING，广播 `task.state.change` 事件。
8. **子任务执行**：agent-runtime 消费 `task.subtask.execute`，执行 ReAct 循环（详见 06-agent-runtime 文档），逐步通过 `ReportStep` gRPC 上报步骤日志。
9. **子任务完成上报**：agent-runtime 完成后向 `task.subtask.done` Topic 发送完成事件（含 status/outputs/cost/token）。
10. **回调处理**：orchestrator 的 SubtaskDoneConsumer 消费该事件：
    - 幂等校验（event_consume_log 去重）
    - 更新节点 status 与 outputs（Redis + MySQL）
    - 写 task_step_log
    - 累加 cost_used_cent 与 token_used
    - 检查成本熔断
11. **批次推进**：若当前批次全部 success，DagExecutor 推进到下一批次（重复 6-10）；若有节点 failed，走 7.5 节失败处理决策。
12. **结果聚合**：所有节点 success 后，orchestrator 调用聚合逻辑（可调用 model-gateway scene=summary 做结果润色），生成 result_summary。
13. **记忆沉淀**：orchestrator 发送 `memory.write` 事件到 memory-service，写入情景记忆（任务执行轨迹）与流程记忆（如有新模板沉淀价值）。
14. **状态收尾**：task 状态转 SUCCESS，广播 `task.state.change`，写 task_state_change 审计，更新 task_template.usage_count 与 success_rate（如使用了模板）。

### 9.2 重规划子流程（SUBTASK_RUNNING → REPLANNING → SUBTASK_RUNNING）

1. **触发**：SubtaskDoneHandler 检测到节点 failed 且 retryCount ≥ maxRetries，调用 `Replanner.triggerIncremental`。
2. **熔断检查**：Replanner 调用 `checkQuota` 验证剩余次数与预算。
3. **状态切换**：task 状态转 REPLANNING，广播 `task.state.change`。
4. **调用规划**：Replanner 通过 gRPC 调用 planning-service `Plan`（携带 frozenNodes、failedNodeIds、failureContext）。
5. **DAG 新版本落库**：planning-service 生成新 DAG，写 task_dag（version+1），写 task_replan_log。
6. **状态恢复**：task 状态转 SUBTASK_RUNNING，DagExecutor 从新版本的失败批次重新推进。
7. **熔断兜底**：若重规划失败或熔断，task 状态转 WAITING_HUMAN（如允许人工兜底）或 FAILED。

### 9.3 人工介入子流程（WAITING_HUMAN）

1. **进入**：任务进入 WAITING_HUMAN 后，orchestrator 通过 session-service 推送 SSE 通知用户。
2. **用户操作**：用户可选「修正结果」「调整路径」「终止任务」「指定重规划」。
3. **恢复执行**：用户提交修正后，orchestrator 调用 TaskStateMachine.transit(WAITING_HUMAN → RUNNING 或 REPLANNING)。
4. **审计留痕**：所有人工操作写 task_state_change（trigger=manual, operator=userId）。

---

## 10. 异常处理

### 10.1 异常分级与重试策略对接

依据 [PRD 第三节(二)1](../../PRD.md#1-失败重试机制) 与 [09-governance 文档](../09-governance-and-deployment/governance-and-middleware.md)，本模块异常分级处理如下：

| 异常分级 | 典型场景 | 本模块处理 | 配合表/字段 |
|---|---|---|---|
| **瞬时异常** | 模型超时、工具网关抖动、DB 死锁 | 子任务级重试（最多 2 次，重置上下文） | task_step_log.status=retry |
| **业务异常** | 参数非法、权限拒绝、工具不存在 | 降级换路（fallback Agent / fallback tool） | task_step_log.error_code |
| **质量异常** | 校验不通过、幻觉检测命中 | 单步重跑（带修正提示）或子任务级重跑 | task_step_log.phase=reflect |
| **致命异常** | 路径完全不可行、重规划熔断、成本超限 | 回滚 + 重规划或转人工 | task_instance.error_code |

### 10.2 三级重试层次

| 层次 | 责任方 | 最大次数 | 触发条件 | 本模块协同 |
|---|---|---|---|---|
| **接口级** | model-gateway / tool-engine | 3 次（指数退避） | 网络抖动、限流 | 不感知，由下游处理 |
| **单步执行级** | agent-runtime | 2 次（带修正提示） | 单步 think/act 失败 | 收到 StepReport(status=retry) 写 task_step_log |
| **子任务级** | task-orchestrator | 2 次（重置上下文重跑） | 子任务整体失败 | 见 7.5 节失败处理决策 |
| **重规划级** | task-orchestrator.Replanner | 增量 3 次 + 全量 1 次 | 子任务级重试用尽 | 见第 5 节 |

### 10.3 与 task_step_log / 状态机的协同

**task_step_log 写入时机**（参考 [01-database 第 2.5 节](../01-database/database-schema-design.md#25-task_step_log-步骤执行日志表)）：

| 触发事件 | phase | action_type | status | 说明 |
|---|---|---|---|---|
| 复杂度评估完成 | think | model_call | success | 记录评估模型输出 |
| 规划生成完成 | think | model_call | success/failed | 记录规划模型输出 |
| 子任务重试 | act | tool_call | retry | 记录重试原因与修正提示 |
| 子任务成功 | observe | none | success | 记录 outputs 快照 |
| 子任务失败（重试用尽） | reflect | none | failed | 记录根因分析 |
| 重规划触发 | think | model_call | success | 记录重规划模型调用 |
| 成本熔断 | observe | none | failed | error_code=COST_BUDGET_EXCEEDED |

**幂等性保障**：
- 所有写操作（task_step_log、task_state_change、task_instance 更新）通过 `task_id + step_no` 唯一索引 + 乐观锁（version 字段）保证幂等
- RocketMQ 消费者通过 `event_consume_log` 表去重
- 子任务级重试通过 `node.retryCount` 计数器防止无限重试

### 10.4 关键错误码（task-orchestrator + planning-service 域）

| 错误码 | HTTP | 含义 | 处理建议 |
|---|---|---|---|
| `TASK_NOT_FOUND` | 404 | 任务不存在 | 检查 task_id |
| `TASK_STATUS_CONFLICT` | 409 | 状态机非法流转 | 检查当前状态与目标状态 |
| `DAG_VALIDATION_FAILED` | 400 | DAG 校验失败（环/不可达/参数映射错） | 重新规划 |
| `DAG_VERSION_CONFLICT` | 409 | 并发修改 DAG 版本冲突 | 重试（乐观锁） |
| `PLAN_FORMALIZE_FAILED` | 500 | 任务语义形式化失败（模型输出非法） | 转 WAITING_HUMAN |
| `PLAN_INCOMPLETE` | 400 | 规划不完备（缺失 deliverable 节点） | 触发增量重规划 |
| `PLAN_TOO_EXPENSIVE` | 400 | 规划成本超限 | 削减节点或转人工 |
| `PLAN_VALIDATION_FAILED` | 500 | 规划自检失败且 2 轮修正未通过 | 转 WAITING_HUMAN |
| `SUBTASK_EXHAUSTED` | 500 | 子任务重试与重规划均用尽 | 转 FAILED 或 WAITING_HUMAN |
| `REPLAN_QUOTA_EXHAUSTED` | 429 | 重规划次数/预算超限 | 转 WAITING_HUMAN |
| `COST_BUDGET_EXCEEDED` | 429 | 任务成本超限 | 转 TIMEOUT 或 FAILED |
| `TASK_DEADLINE_EXCEEDED` | 504 | 任务超时 | 转 TIMEOUT |
| `MODEL_GATEWAY_ERROR` | 503 | 模型网关错误 | 降级 fallback 模型 |
| `TEMPLATE_PARAM_MISSING` | 400 | 模板参数缺失 | 回退智能规划 |

### 10.5 与横向体系的协同

| 横向体系 | 协同点 |
|---|---|
| **risk-control** | (1) 子任务分发前校验 Agent 对工具的权限（permission_policy）<br>(2) R3 高危节点 requireHumanReview=true → 转 WAITING_HUMAN<br>(3) 所有 task_step_log 写入前经 risk-control 脱敏过滤器 |
| **observability** | (1) 全链路 TraceID 透传到 task_step_log.trace_id<br>(2) 任务状态变更广播 `task.state.change` 供 observability 采集<br>(3) 关键指标：任务完成率、平均重规划次数、子任务平均耗时由 Prometheus 采集 |
| **cost-control** | (1) CostMonitor 实时累加 cost_used_cent<br>(2) 接近 90% 阈值时预警，超 100% 熔断<br>(3) 重规划成本独立预算（cost_limit_cent × 30%） |

---

## 11. 配置参数清单

本模块关键可调参数（Nacos 配置）：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `task.orchestrator.maxSubtaskRetries` | 2 | 子任务级最大重试次数 |
| `task.orchestrator.maxIncrementalReplan` | 3 | 增量重规划上限 |
| `task.orchestrator.maxFullReplan` | 1 | 全量重规划上限 |
| `task.orchestrator.replanBudgetPercent` | 0.3 | 重规划预算占任务成本比例 |
| `task.orchestrator.taskTimeoutScanIntervalSec` | 30 | 超时扫描间隔 |
| `task.orchestrator.subtaskDispatchTimeoutMs` | 5000 | 子任务投递超时 |
| `task.planning.maxNodesPerDag` | 15 | 单 DAG 最大节点数 |
| `task.planning.modelTier` | strong | 规划模型档位 |
| `task.planning.modelTemperature` | 0.2 | 规划模型温度 |
| `task.planning.complexityModelTier` | light | 复杂度评估模型档位 |
| `task.planning.templateMinSuccessRate` | 0.85 | 模板启用最低成功率 |
| `task.planning.ruleFilterConfidenceThreshold` | 0.9 | 规则初筛置信度阈值 |
| `rocketmq.topics.subtask-execute` | `task.subtask.execute` | 子任务分发 Topic |
| `rocketmq.topics.subtask-done` | `task.subtask.done` | 子任务完成 Topic |
| `rocketmq.topics.subtask-cancel` | `task.subtask.cancel` | 子任务取消 Topic |
| `rocketmq.topics.state-change` | `task.state.change` | 状态变更 Topic |

---

## 12. 与其他文档的关联

| 本文档章节 | 关联文档 | 关联内容 |
|---|---|---|
| §1 模块职责 | [00-overview §3.1](../00-overview/tech-stack-and-architecture.md#31-微服务拆分清单11-个核心服务--2-个横向服务) | 微服务清单 |
| §2 复杂度识别 | [PRD 第二节(三)1](../../PRD.md) | 三级分级标准 |
| §3 两种规划模式 | [PRD 第二节(三)2](../../PRD.md) | 完整规划生成流程 |
| §4 DAG 引擎 | [01-database §2.2-2.4](../01-database/database-schema-design.md#22-task_dag-dag-定义表) | task_dag 表与 JSON 结构 |
| §4 DAG 校验 | [00-overview ADR-001](../00-overview/tech-stack-and-architecture.md#adr-001采用自研-dag-引擎而非-camundaactiviti) | 自研 DAG 决策 |
| §5 重规划 | [01-database §2.7](../01-database/database-schema-design.md#27-task_replan_log-重规划日志表) | task_replan_log 表 |
| §6 状态机 | [08-flow/state-machines-and-sequences.md](../08-flow/state-machines-and-sequences.md) | 完整时序图 |
| §7 子任务分发 | [02-api §11](../02-api/api-specification.md#11-异步事件规范rocketmq-topics) | RocketMQ Topic 规范 |
| §7 子任务执行 | [06-agent-runtime/agent-runtime-engine.md](../06-agent-runtime/agent-runtime-engine.md) | ReAct 循环与上报 |
| §8 核心类 | [02-api §6](../02-api/api-specification.md#6-任务编排-apigrpc-内部) | gRPC 接口契约 |
| §10 异常处理 | [PRD 第三节](../../PRD.md) | 异常分级与重试策略 |
| §10 异常处理 | [09-governance-and-deployment/governance-and-middleware.md](../09-governance-and-deployment/governance-and-middleware.md) | 熔断与成本管控 |
| §11 配置 | [09-governance-and-deployment/governance-and-middleware.md](../09-governance-and-deployment/governance-and-middleware.md) | Nacos 配置管理 |

---

## 13. 设计决策记录（ADR 摘要）

### ADR-T1: 重规划采用"增量优先 + 全量兜底"而非"每次全量重规划"

**背景**：L3 复杂任务执行中常出现单点失败，若每次全量重规划会导致已完成节点重跑，成本与时间不可接受。
**决策**：默认增量重规划（保留 frozenNodes），仅当增量失败 2 次或触发极端场景（用户需求变更）才允许全量重规划，且限次 1 次。
**代价**：增量合并算法复杂度高（需处理节点合并、边重建、并行批次重算）；实现风险通过单测覆盖。

### ADR-T2: DAG 节点 outputs 实时写 Redis 而非直接读 MySQL

**背景**：参数依赖（edges.paramMapping）要求下游节点能立即读取上游 outputs，若每次读 MySQL 延迟与压力不可接受。
**决策**：节点 outputs 同步写 Redis（Key: `dag:{taskId}:node:{nodeId}:outputs`），MySQL 异步刷盘。
**代价**：Redis 与 MySQL 短暂不一致；通过任务完成后兜底校验保证最终一致。

### ADR-T3: 子任务分发使用 RocketMQ 事务消息而非普通消息

**背景**：节点状态变更（DB）与子任务投递（MQ）必须一致，避免"DB 已 running 但 MQ 未投递"或"MQ 已投递但 DB 未更新"。
**决策**：使用 RocketMQ 事务消息，先发 half 消息 → 本地事务更新节点状态 → commit/rollback。
**代价**：事务消息性能略低于普通消息（多一次回查），但一致性保障值得。

### ADR-T4: 复杂度评估采用"两级识别"而非"纯模型评估"

**背景**：纯模型评估延迟 200~500ms，对 L1 闲聊类任务过于昂贵。
**决策**：规则初筛（< 5ms）优先，仅规则不确定时（置信度 < 0.9）才调用模型精判。
**代价**：规则覆盖不全的长尾场景仍需模型；通过持续迭代规则库提升命中率。

### ADR-T5: 模板化规划与智能规划共用同一套 DAG 数据结构

**背景**：两种规划模式输出需统一管理（校验、执行、版本）。
**决策**：统一使用 task_dag 表，通过 source 字段区分来源；TemplatePlanner 与 AiPlanner 实现同一 Planner 接口。
**代价**：模板 DAG 需在加载时做参数填充，不能像传统模板引擎直接执行；通过 ParamFiller 统一处理。

---

**文档结束**。后续编码实现将以本文档为契约，分模块交付：
1. agent-task-orchestrator 模块（状态机 + DAG 执行器 + 分发器 + 重规划器）
2. agent-planning 模块（评估器 + 规划器 + 模板仓库 + 自检器）
3. agent-proto 模块（task.proto 完善）
4. agent-common 模块（共享 POJO 与枚举）
