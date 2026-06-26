# 项目记忆 - Agent 智能体平台系统

> 本文件记录项目历史会话与决策，供后续 Agent 接续工作。请勿覆盖，仅在文末追加新条目，用分隔线区隔。

---

## 📅 2026-06-26 会话记录：生成系统设计文档全集

### 会话目标
用户基于已有的 `PRD.md`（Agent 智能体平台需求说明书，覆盖 7 大章节：整体架构/核心组件/异常容错/幻觉治理/漂移管控/成本管控/工程输出要求），要求使用 brainstorming + prd + write-a-prd + writing-plans 技能生成"需要开发这一系统的各种文档"。

### 关键决策（brainstorming 结构化提问后用户确认）
1. **技术栈**：Java 17 / Spring Cloud Alibaba（非 Python/Go/Node）
2. **本轮范围**：先出整体架构 + 模块设计文档（约 10 份），不含代码实现
3. **存储组织**：项目 docs/ 目录分模块存放
4. **详细程度**：工程级详细（具体表名/字段/索引、REST/gRPC 接口签名+示例、Mermaid 状态机、时序图）

### 产出清单（11 份文档，均已落盘）

| # | 文档路径 | 内容摘要 |
|---|---|---|
| 1 | `docs/00-overview/tech-stack-and-architecture.md` | 技术栈选型、11+2 微服务拆分、Monorepo 目录结构、5 条 ADR |
| 2 | `docs/01-database/database-schema-design.md` | MySQL 9 域表结构、Milvus 6 Collection、Neo4j 图谱、ClickHouse 指标表 |
| 3 | `docs/02-api/api-specification.md` | REST/gRPC/SSE/RocketMQ 全协议、错误码、限流熔断 |
| 4 | `docs/03-task-engine/task-orchestration-and-planning.md` | 任务状态机 10 状态、DAG 引擎、重规划、并行调度 |
| 5 | `docs/04-memory/memory-system-design.md` | 三级记忆、多路召回重排、四级水位压缩、蒸馏调度 |
| 6 | `docs/05-tool-engine/tool-and-invocation-system.md` | 工具全链路 9 步、R1/R2/R3 权限、沙箱、成本熔断 |
| 7 | `docs/06-agent-runtime/agent-runtime-engine.md` | 无状态设计、ReAct 循环、Reflexion、断点续跑 |
| 8 | `docs/07-code-retrieval/code-retrieval-system.md` | AST 解析、Neo4j 图谱、三路召回、融合重排 |
| 9 | `docs/08-flow/state-machines-and-sequences.md` | 12 张 Mermaid 图（状态机/时序图/流程图） |
| 10 | `docs/09-governance-and-deployment/governance-and-middleware.md` | 幻觉六层治理、漂移四层管控、成本、11 类中间件、Nacos/Dockerfile/K8s 部署 |
| 11 | `docs/README.md` | 文档索引导航 + 依赖关系图 + 阅读路径 |

### PRD 第七章交付物覆盖情况
- 交付物 1（目录结构+技术栈）：✅ doc 1
- 交付物 2（核心代码实现）：⏸ 设计完成，代码留待后续 coding plan
- 交付物 3（数据库表结构）：✅ doc 2
- 交付物 4（API 规范）：✅ doc 3
- 交付物 5（配置部署）：✅ doc 10
- 交付物 6（状态机时序）：✅ doc 9
- 交付物 7（中间件集成）：✅ doc 10

### 关键技术约定（后续编码必须遵循）
- 微服务：11 核心服务 + 2 横向服务，端口 8080-8104
- 数据库：按服务边界划分 9 个逻辑库，task_instance 等按 tenant_id 取模 16 库分片
- 向量库：Milvus 2.4，记忆按 episodic/semantic/procedural 分 Collection，HNSW 索引
- 服务间通信：内部 gRPC + Protobuf（agent-proto 模块），对外 REST，流式 SSE
- 事件驱动：RocketMQ 7 个 Topic（task.subtask.execute/done/state.change 等）
- Agent 运行时无状态（ADR-002）：执行态外置 Redis `runtime:{agentInstanceId}:state`
- 工具调用必经 ToolGateway（ADR-005）：禁止 Agent 直连工具
- 任务状态机：10 个状态 PENDING/PLANNING/RUNNING/SUBTASK_RUNNING/WAITING_HUMAN/REPLANNING/SUCCESS/FAILED/CANCELLED/TIMEOUT

### 后续建议（next session）
1. 基于 writing-plans 技能拆分编码计划，保存至 `docs/plans/`
2. 建议优先级：`agent-proto`（Protobuf）→ `agent-common`（公共模块）→ `infra/sql`（DDL）→ 基础服务（gateway/session）→ 核心引擎（task/memory/tool/runtime）
3. 每个编码计划采用 TDD 红绿循环
4. 本轮设计的 5 条 ADR 已固化在 doc 1，编码时不得违反

### 执行反思
- ✅ 采用"先地基（架构/数据库/API）→ 并行模块详设"策略，确保跨文档一致性
- ✅ 5 份模块详设 + 2 份流程/治理文档通过 Task 子代理并行生成，主 Agent 控制地基与索引
- ⚠️ 子代理产出的文档量大（单文档 1500-3000 行），后续如需精简可按模块拆分
- ⚠️ 尚未生成实际代码与 DDL 脚本，需后续 coding plan 推进

### 推荐技能
- 后续编码：`writing-plans` + `executing-plans` + `tdd`
- 代码审查：`TRAE-code-review`
- 安全审查：`TRAE-security-review`

---

## 📅 2026-06-26 会话记录：对照 detail-MRD.md 补遗遗漏

### 会话目标
用户追加要求"也要参考 detail-MRD.md，看看有没有什么遗漏"。基于上一轮生成的 11 份设计文档，对照 `detail-MRD.md`（1370 行详细 MRD，包含主流模型计费表、代码 Token 测算、BPE 分词规则、完整业务示例、Agent 内聚架构等细节）做差异分析，补齐遗漏内容。

### 差异分析方法
1. 完整阅读 `detail-MRD.md` 1370 行，识别其中明确具体而 11 份设计文档未涉及的工程细节
2. 用 Grep 在已生成文档中检索关键概念（模型名、BPE、Token 估算、调研融资示例等）验证是否真缺失
3. 对识别出的 6 类遗漏归集到单一补遗文档，避免修改已稳定的 11 份主设计文档以保持审计轨迹

### 识别的 6 类遗漏

| # | 遗漏类别 | detail-MRD 出处 | 补遗落点 |
|---|---|---|---|
| 1 | Agent 内部「1主控+4模块」内聚架构视图（与平台服务映射） | detail-MRD §「Agent 核心组件整体架构」 | 补遗 §1 |
| 2 | 主流模型详细计费参考表（海外 $/Mtoken + 国内 元/千Token） | detail-MRD 成本篇 §三 | 补遗 §2 |
| 3 | 代码场景 Token 消耗估算表（简单/中等/复杂 × 单轮/多轮） | detail-MRD 成本篇 §二 | 补遗 §3 |
| 4 | BPE 分词规则 / Prompt 缓存折扣 / 长上下文加价细节 | detail-MRD 成本篇 §三.2 | 补遗 §4 |
| 5 | 3 个完整业务示例（调研融资 6 子任务 DAG + 异常处理 walkthrough + 漂移处理示例） | detail-MRD §三 + §四 + 漂移示例 | 补遗 §5 |
| 6 | 低代码配置台范围（5 个前端模块） | detail-MRD 接入交互层 | 补遗 §6 |

### 产出文档

| 文档路径 | 内容摘要 |
|---|---|
| `docs/10-supplement/detail-mrd-gap-fill.md` | 7 章节：1) Agent 1+4 内聚架构视图 2) 主流模型计费参考表（含 GPT-4o $5/$15、Claude 3.5 Sonnet $3/$15、Gemini 1.5 Pro $1.25/$5、国内强模型 0.02-0.05 元/千Token 等）3) 代码 Token 估算表（简单 4K-7K/8K-15K，中等 14K-28K/40K-80K，复杂 40K-120K/120K-300K）4) BPE 分词规则（英 4 字符≈1 Token，中 1.3-2 字符≈1 Token）+ Prompt 缓存折扣（Anthropic 10%，OpenAI 50%）+ 长上下文加价 5) 3 个完整业务示例 6) 低代码配置台范围（运营后台/Agent 配置工作台/调试沙箱/终端对话界面/任务监控大屏）7) 回填建议（建议哪些内容回填到 doc 09-governance §3.2） |

### 索引与项目记忆更新
- `docs/README.md`：已更新为 12 份文档（11 主设计 + 1 补遗），新增「四、补遗文档」分组，依赖图加入 `11. supplement` 节点，阅读路径新增「产品/业务方」路径
- `project_memory.md`：本条记录

### 关键决策与反思
- ✅ **采用单一补遗文档**而非逐个修改 11 份主文档：保持审计轨迹，避免已稳定文档被改动引入风险，便于审阅者一眼看到本轮新增内容
- ✅ **补遗 §7 给出回填建议**：明确建议哪些内容应回填到 `doc 09-governance §3.2`（成本与模型选型章节），但实际回填操作留待用户决策
- ⚠️ **Grep 大小写敏感问题**：初次检索 `GPT-4o|Claude 3.5|Gemini`（大写）得 "No matches found"，误判模型名缺失。后通过 Read 实际文件内容发现 doc 09 §3.1 中已有小写模型名 `gpt-4o` / `claude-3-5-sonnet` / `qwen-max`。这是大小写匹配问题，不是真遗漏。教训：先用 Read 验证再下结论
- ⚠️ **补遗文档跨文档引用**：补遗引用了 doc 01/02/09 的具体章节锚点，后续若主文档章节编号变动需同步更新补遗中的引用

### 后续建议（next session）
1. 若用户接受补遗 §7 回填建议，可启动一轮"主文档回填"任务：将主流模型计费表、BPE 规则、Prompt 缓存折扣等回填到 doc 09-governance §3.2，回填后将补遗 §2/§3/§4 标记为「已回填，仅留作历史参考」
2. 低代码配置台范围（补遗 §6）目前是范围声明，未细化为前端设计文档；若启动前端开发，需独立出前端设计 plan
3. 3 个业务示例（补遗 §5）可作为端到端集成测试用例的输入，编码阶段应据此编写 E2E 测试
4. 主流模型计费表（补遗 §2）价格变动频繁，建议后续编码时将其抽为 `model_provider` 表的初始化数据（已在 doc 02 数据库设计中定义），由运营定期更新而非固化在文档中

### 推荐技能
- 主文档回填（若用户决定执行）：`writing-plans` + `executing-plans`
- 前端低代码控制台设计：`brainstorming` + `frontend-design` + `prd`
- 端到端测试用例编写：`tdd` + `executing-plans`

---

## 📅 2026-06-26 会话记录：补遗回填 + 决策逻辑层流程图详设

### 会话目标
用户追加要求："回填，然后开始做详细设计，尤其是每个环节的详细逻辑的流程图"。两个任务：
1. 把补遗文档 §2/§3/§4 回填到 doc 09-governance §3.2
2. 生成决策逻辑层级的流程图（深化 doc 08 的行为契约级时序图到判断节点级）

### 任务一：补遗回填

**回填位置**：`docs/09-governance-and-deployment/governance-and-middleware.md` §3.2「核心成本测算规则」之后，新增 d/e/f 三个小节：

| 新增小节 | 内容 | 来源 |
|---|---|---|
| §3.2.d 主流模型计费参考表 | 海外 7 款模型 $/Mtoken + 国内 3 档 元/千Token + 计费规则要点 | 补遗 §2 |
| §3.2.e 代码场景 Token 消耗测算 | Token 构成公式 + 三级量化表（简单 4K-7K/中等 14K-28K/复杂 40K-120K）+ 换算参考 | 补遗 §3 |
| §3.2.f BPE 分词规则与计费细节 | f-1 BPE 分词 / f-2 Prompt 缓存折扣（Anthropic 10%、OpenAI 50%）/ f-3 超长上下文加价 | 补遗 §4 |

**补遗文档 §7 同步更新**：从「回填建议」改为「回填状态跟踪」，三章节标记为 ✅ 已回填 2026-06-26，主文档成为单一事实来源。

### 任务二：决策逻辑层流程图

**层次差异**：
- doc 08（已有）：行为契约级时序图/状态机——回答"什么状态、什么时序"
- doc 11（本轮新增）：决策逻辑级流程图——回答"什么条件、走哪个分支、为什么"

**12 张流程图分 3 个子文档**：

| 文档 | 图编号 | 流程图名称 | 决策节点数 |
|---|---|---|---|
| `docs/11-detail-flow/01-access-and-planning-flow.md`（550 行） | F1 | 接入网关请求处理流程 | 6 |
| | F2 | 意图识别与复杂度判定决策树 | 7 |
| | F3 | 任务规划与 DAG 生成流程 | 14 |
| | F4 | 子任务分发与并行调度流程 | 10 |
| `docs/11-detail-flow/02-runtime-and-replan-flow.md`（631 行） | F5 | 动态重规划决策流程 | 7 |
| | F6 | ReAct 循环详细决策流程 | 8 |
| | F7 | Token 水位压缩决策流程 | 5 |
| | F8 | 工具选择与调用决策流程 | 13 |
| `docs/11-detail-flow/03-quality-and-memory-flow.md`（752 行） | F9 | 三级质量校验决策流程 | 6 |
| | F10 | 幻觉治理六层联动流程 | 12 |
| | F11 | 漂移监测与纠偏决策流程 | 9 |
| | F12 | 长期记忆写入与召回决策流程 | 9 |
| **合计** | **12 张** | **总行数 1933** | **106 个决策节点** |

**关键决策点示例**：
- F2：双阶段评分（规则初筛 `rule.confidence >= 0.9` 跳过模型精判）+ 阈值链（`total_score <= 8 → L1`，`<= 14 → L2`，否则 L3）+ 风险强制升级（`risk_level == 高 → 强制 ≥ L2`）
- F4：批次入度归零判断 + Agent 4 维度加权（0.40 标签 + 0.30 成功率 + 0.15 延迟 + 0.15 成本）+ 失败分级（`failed_count > batch.size * 0.5` 触发增量重规划）
- F6：Think 自检三问 + Act 四标签分支（`<tool_call>` / `<handoff>` / `<final_answer>` / `NEED_MORE_INFO`）+ Reflexion 三模式（none/single/multi）
- F7：四级水位（`token_ratio < 0.70` / `< 0.85` / `< 0.95` / `>= 0.95`）+ 6 类内容压缩优先级
- F8：Top-K 召回 + 5 步前置校验链 + R1/R2/R3 三级执行 + 成本熔断（`cost_used_cent >= cost_limit_cent`）
- F10：6 个 subgraph 分层展示 L1-L6，层间跳转条件明确
- F12：写入分支（3 类触发 + 重要性评分 + 去重合并 0.85/0.95 阈值）+ 召回分支（4 路并行 + 融合重排 4 权重）

### 质量校验结果

- ✅ 决策深度：每图 ≥5 判断节点、≥3 分支
- ✅ 可执行性：动作节点全部带类/方法签名（如 `gateway.AuthFilter.authenticate()`、`planning.PromptAssembler.assemble()`、`quality-service.L4HardValidator.validate()`）
- ✅ 错误码完整：异常分支全部带错误码（`UNAUTHENTICATED` / `RATE_LIMITED` / `CONTENT_BLOCKED` / `DAG_CYCLE_DETECTED` / `HALLUCINATION_SUSPECTED` / `FACT_INCONSISTENCY` / `COST_BUDGET_EXCEEDED` 等），对齐 doc 02 §0.5 规范
- ✅ 与已有文档一致：状态名（10 状态机）、字段名（abilityTags/cost_limit_cent/parallel_batches）、Collection 名（mem_episodic/mem_semantic/mem_procedural）、API 路径（`/api/v1/tasks`、ToolGateway.Invoke）均与 doc 01-09 一致
- ✅ 不重复 doc 08：聚焦决策逻辑层，仅深化到判断节点级
- ⚠️ 一处待对齐项：F5 新增 `REPLAN_EXHAUSTED` 错误码，建议归入 doc 02 §0.5 的 `UNAVAILABLE`(503) 域，已在 01 子文档 §5.3 标注

### 索引更新
- `docs/README.md`：文档总数 12 → 15，新增「五、详细逻辑流程图」分组（3 个子文档 12 张图），依赖图加入 `12-14. detail-flow` 节点，阅读路径更新（后端开发/QA/算法工程师新增 detail-flow 引用）

### 关键决策与反思

- ✅ **采用 3 子代理并行生成**而非单代理串行：3 个子文档独立无依赖，并行节省时间，主 Agent 控制索引
- ✅ **决策节点用具体表达式**而非模糊描述：所有判断节点带可计算条件（如 `cosine_sim >= 0.75`，不写"事实一致？"），便于后续编码与测试用例编写
- ✅ **子代理复查机制**：每个子代理生成后用 Read 工具复查 Mermaid 语法（如子图闭合、入口连接修正）
- ⚠️ **Mermaid 语法陷阱**：含 `<` 的边标签可能破坏解析，子代理 03 已将 2 处改为中文表述；后续若需精调可统一改为 HTML 实体 `&lt;`
- ⚠️ **决策节点计数差异**：子代理 01 报告 F6=7、F7=6，实际复查为 F6=8、F7=5，已在索引中修正；说明子代理自报数字需人工抽查

### 后续建议（next session）

1. **编码阶段**：12 张决策流程图可作为编码实现的直接输入——每个判断节点对应一个 `if/switch` 分支，每个动作节点对应一个方法调用，错误码对应异常类
2. **测试用例**：每张图的每条路径应生成至少 1 个测试用例（正常路径 + 每个异常分支），12 张图预计可衍生 100+ 测试用例
3. **Mermaid 渲染校验**：建议在 IDE 中安装 Mermaid 预览插件，逐一渲染 12 张图检查语法（重点关注 subgraph 嵌套与含特殊字符的边标签）
4. **错误码对齐**：将 `REPLAN_EXHAUSTED` 补充到 doc 02 §0.5 错误码规范
5. **下一步可推进**：① 编码计划（writing-plans 技能，按子系统拆分）② 前端低代码控制台详设（brainstorming + frontend-design）③ DDL 脚本编写

### 推荐技能
- 编码计划：`writing-plans` + `executing-plans` + `tdd`
- 前端详设：`brainstorming` + `frontend-design` + `prd`
- 代码审查：`TRAE-code-review` + `TRAE-security-review`

---

## 📅 2026-06-27 会话记录：生成 agent-proto + agent-common 编码实现计划

### 会话目标
用户基于已落盘的 15 份设计文档（doc 00-overview 至 doc 11-detail-flow），要求使用 writing-plans 技能编写第一份 Java 编码实现计划，覆盖 agent-proto（Protobuf 契约层）+ agent-common（公共工具层）两个基础模块，作为整个 11 微服务体系的地基。

### 产出文档

| 文档路径 | 内容摘要 |
|---|---|
| `docs/plans/01-agent-proto-and-common-plan.md` | 完整编码计划，2785 行，8 个 Task，56 个带编号步骤 + 3 个 Final Step |

### 计划结构

**Plan Header**：包含 Goal / Architecture / Tech Stack（Java 17 / Spring Boot 3.2 / protobuf-maven-plugin 0.6.1 / grpc-java 1.62.2 / Lombok / Jackson / JUnit 5 / Mockito）

**8 个 Task 概览**：

| Task | 内容 | 关键产出 |
|---|---|---|
| 1 | agent-proto Maven 骨架 | pom.xml（protobuf-maven-plugin 0.6.1 + grpc 1.62.2 + os-maven-plugin 1.7.1 扩展）|
| 2 | common.proto 公共消息 | TraceContext（tenantId/userId/sessionId/taskId/subtaskId/traceId/spanId）+ Error + Pagination |
| 3 | task.proto TaskOrchestrator | 4 个 RPC + TaskInstance 23 字段严格对齐 doc 01-database §2.1 task_instance 表 |
| 4 | planning.proto + memory.proto + model.proto | PlanningService 4 RPC + DagNode/DagEdge + MemoryService 4 RPC + RecalledMemory + ModelGateway 4 RPC（含 StreamChat server streaming + ModelParams enable_cot/enable_prompt_cache/temperature/top_p）|
| 5 | tool.proto + knowledge.proto + agent_runtime.proto | ToolGateway 4 RPC（含 risk_level/prompt_cache_key）+ KnowledgeService 3 RPC + AgentRuntime 5 RPC（AgentState 含 currentStep/currentThink/tokenUsed）|
| 6 | agent-common 骨架 + ErrorCode + BusinessException | pom.xml（依赖 agent-proto + Lombok + Jackson + spring-boot-starter）+ ErrorCode 枚举 26 个错误码 + BusinessException 4 个构造器 |
| 7 | 4 个常量枚举 | TaskStatus(10 状态 + isTerminal) + ComplexityLevel(L1/L2/L3 含 stepRange/toolRange/costLimitCent) + AgentStatus(0/1/2/3) + RiskLevel(R1/R2/R3 含 executor) |
| 8 | 工具类 + TraceContext | JsonUtils(Jackson 单例 + toJson/fromJson/toMap) + TraceUtils(generateTraceId 32 hex + ThreadLocal) + TokenEstimator(中文 1.7 倍系数) + TraceContext(Lombok @Builder) |

### TDD 红绿循环结构

每个 Task 严格遵循 writing-plans 方法论的 bite-sized 步骤（2-5 分钟一步）：
1. **Step N.1**: 编写失败测试（完整测试代码）
2. **Step N.2**: 运行测试验证失败（红，含 `mvn` 命令与预期错误信息）
3. **Step N.3**: 编写最小实现（完整实现代码）
4. **Step N.4**: 运行 protobuf:compile 或 mvn test 验证通过（绿）
5. **Step N.5**: git commit（含规范的 conventional commit message）

总计 47 个测试用例：agent-proto 16 个 + agent-common 31 个

### 关键设计决策

- **.proto 文件命名规则统一**：`package agentplatform.<domain>.v1` + `java_package agentplatform.<domain>.v1` + `java_multiple_files = true`
- **TraceContext 字段位置 99**：所有 RPC 请求消息的 TraceContext 字段统一使用编号 99，预留 1-50 给业务字段，51-98 给扩展字段
- **TaskInstance 字段严格对齐数据库表**：23 个字段一对一映射 doc 01-database §2.1，包括 task_id/tenant_id/session_id/user_id/title/goal/complexity/status/task_schema/dag_id/agent_id/priority/parent_task_id/replan_count/cost_limit_cent/cost_used_cent/token_used/started_at/finished_at/error_code/error_msg/result_summary/created_at/updated_at
- **StreamChat 使用 server streaming**：`rpc StreamChat(ChatRequest) returns (stream ChatChunk)`，ChatChunk 用 oneof 持有 tool_call 或 finish_reason
- **TokenEstimator 中文 1.7 倍系数**：参考 doc 09 §3.2.c 与补遗 §4 BPE 规则，中文字符按 1.7 token 估算，英文按 4 字符/token 估算，向下取整
- **ErrorCode 26 个错误码**：覆盖 200/400/401/403/404/409/429/500/503/504 全部 HTTP 状态码，命名规则 `大写下划线`，按域分组与 doc 02-api §0.5 一致
- **Java TraceContext 与 proto TraceContext 分离**：Java 业务代码使用 Lombok @Builder POJO，避免直接依赖 protobuf 类，字段名与 proto 一致

### 自检结果

1. **Spec 覆盖**：用户任务描述的 8 个 Task 全部覆盖，无遗漏
2. **占位符扫描**：Grep 检索 `TODO|TBD|implement later|fill in details|add appropriate|add error handling|similar to Task`，实际代码步骤中无任何占位符
3. **类型一致性**：TraceContext 字段名、TaskStatus 枚举值、ComplexityLevel.getLevel()、RiskLevel.getLevel()、ErrorCode.getCode() 在 proto 与 Java 间完全一致

### 执行反思

- ✅ **TDD 红绿循环严格执行**：每个 Task 先写失败测试 → 运行验证红 → 写实现 → 运行验证绿，符合 writing-plans 方法论
- ✅ **字段严格对齐数据库设计文档**：TaskInstance 23 字段一对一映射 task_instance 表，避免编码阶段字段名/类型不一致返工
- ✅ **proto 与 Java 双层 TraceContext**：proto 用于 gRPC 传输，Java POJO 用于业务代码，避免业务代码直接依赖 protobuf 类
- ⚠️ **agent-common pom 依赖 agent-proto**：需先执行 `mvn install` 安装 agent-proto 至本地仓库，否则 agent-common 编译失败。计划 Final Step 已明确此顺序
- ⚠️ **proto 文件 import 路径**：所有 .proto 文件 import "common.proto"，依赖 protobuf-maven-plugin 默认 protoSourceRoot = src/main/proto，无需额外配置
- ⚠️ **os.detected.classifier**：pom.xml 中 protobuf-maven-plugin 依赖 os-maven-plugin 扩展自动检测 OS arch，Windows 下会下载 windows-x86_64 版 protoc，需联网

### 后续建议（next session）

1. **执行该 Plan**：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 按 Task 顺序执行，每个 Task 完成后 git commit
2. **后续 Plan 优先级**：① infra/sql（DDL 建表脚本，按 doc 01-database）② agent-gateway + session-service（接入层）③ task-orchestrator + planning-service（核心引擎）④ memory-service + tool-engine + model-gateway + agent-runtime（核心引擎）⑤ knowledge-service + agent-repo + quality-service（能力服务）
3. **建议补一个根 pom.xml**：当前 agent-proto 与 agent-common 各自独立 pom，未来 13 个微服务需要统一版本管理，建议在项目根目录创建 parent pom 统一管理依赖版本
4. **测试覆盖**：本轮 Plan 的测试聚焦于"字段可序列化/反序列化"与"枚举值正确性"，未覆盖 gRPC 服务端业务逻辑测试（业务逻辑测试应在每个微服务的实现 Plan 中编写）
5. **CI/CD 提示**：本轮 Plan 假设 Windows 本机环境执行 `mvn` 命令，CI 环境需配置 Maven + protoc + grpc-java 插件

### 推荐技能
- 执行 Plan：`superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`
- TDD 编码：`tdd` + `test-driven-development`
- 后续模块 Plan：`writing-plans`
- 代码审查：`TRAE-code-review`

---

## 📅 2026-06-27 会话记录：阶段 1 编码计划总览 + 接入层 plan + 阶段成果汇总

### 会话目标
用户追加要求"按 1、2、3、4 的顺序执行，每个阶段完成都要记录阶段成果到项目开发记录中"。其中：
1. 编码计划（writing-plans 技能，按子系统拆分）
2. 前端低代码控制台详设
3. DDL 脚本编写
4. Mermaid 渲染校验

本条记录阶段 1 成果。

### 阶段 1 产出

| # | 文档 | Task 数 | 行数 | 覆盖模块 | 状态 |
|---|---|---|---|---|---|
| - | `docs/plans/00-coding-plans-overview.md` | - | 180 | 10 个子系统总览 + 依赖图 + 执行顺序 + 关键约定 | ✅ |
| 01 | `docs/plans/01-agent-proto-and-common-plan.md` | 8 | 2785 | agent-proto（8 .proto）+ agent-common（11 Java 类），47 测试用例 | ✅（详见上一节记录） |
| 02 | `docs/plans/02-agent-gateway-session-plan.md` | 10 | 4339 | agent-gateway(8080) + agent-session(8082)，43 Java 类 | ✅ |

**阶段 1 总计**：3 份文档，7304 行，18 Task，90 测试用例。

### Plan 02 关键内容

覆盖接入交互层两个微服务的完整 TDD 红绿循环编码计划：

**agent-gateway 模块（Task 1-5）**：
- T1 项目骨架（pom + application.yml + GatewayApplication，端口 8080）
- T2 AuthFilter JWT/API-Key 双模式鉴权（jjwt 0.12.5，使用 `Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()` 新 API）
- T3 RateLimitFilter Bucket4j 8.10.1 令牌桶限流（每秒 10 QPS，突发 20）
- T4 ContentSafetyFilter 内容安全检测（调用 risk-control.preCheck）
- T5 TaskController + TaskRouterService 任务路由（chat/single_step/complex 三分支）

**agent-session 模块（Task 6-10）**：
- T6 项目骨架 + Session/Message 实体（对齐 doc 01-database §1，SessionStatus 1活跃/2空闲/3关闭/4归档）
- T7 SessionRepository MySQL 持久化（Testcontainers MySQL 集成测试）
- T8 ShortTermMemoryService Redis 短期记忆（Hash Key `sm:{sessionId}:ctx`，TTL 24h，Testcontainers Redis）
- T9 SessionService + SessionController 6 个 REST 端点（POST/GET/DELETE/messages）
- T10 SsePushService SSE 流式推送（Redis Pub/Sub `session:{sessionId}:events`）+ EndToEndTest 集成测试

### 关键设计决策（Plan 02）

- **jjwt 0.12.5 API 适配**：使用新 API `verifyWith` + `parseSignedClaims` + `getPayload`，非旧版 `parseClaimsJws`，注释中明确标注
- **Bucket4j 8.10.1**：采用 `bucket4j_jdk17-core` 而非 spring-boot-starter，规避与 Spring Cloud Gateway starter 配置冲突，更易控制 per-tenant 维度
- **gRPC 客户端**：`net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE`，排除 `grpc-netty-shaded` 避免与 web 服务器冲突
- **Stub 策略**：RiskControlClient/TaskOrchestratorClient/SessionServiceClient 标注为「待 agent-proto 发布后替换」，但提供完整可运行的本地 stub，确保 Task 1-10 全链路可独立验证
- **Testcontainers 集成测试**：Task 7（MySQL）/ Task 8（Redis）/ Task 10（EndToEnd）涉及 Testcontainers，需 Docker 环境

### 总览文档（Plan 00）关键内容

- **10 个子系统拆分**：按依赖关系分 P0（DDL+proto+common）/ P1（task+memory+tool+model）/ P2（runtime+repo+knowledge+quality）/ P3（基础设施）
- **依赖关系图**：ASCII 图展示 P0→P1→P2→P3 的分层依赖
- **建议执行顺序**：阶段 A（P0 并行）→ 阶段 B（P1 并行）→ 阶段 C（P2 依赖 B）→ 阶段 D（接入层联调）→ 阶段 E（基础设施部署）
- **关键约定**：技术栈版本、Monorepo 目录、5 条 ADR、TDD 红绿循环、错误码规范
- **待生成 8 份 plan 的任务大纲**：每个 plan 列出 13-16 个 Task 标题，待用户确认后深化为完整 TDD 编码计划

### 索引更新
- `docs/README.md`：文档总数 15 → 18，新增「六、编码计划」分组（3 份已完成 + 8 份待生成）

### 阶段 1 完成确认

✅ 已完成：
- 1 份总览文档（10 子系统任务大纲 + 依赖图 + 执行顺序）
- 2 份完整 TDD 红绿循环编码计划（agent-proto+common / agent-gateway+session）
- README.md 索引更新
- project_memory.md 阶段成果记录

⏸ 待用户决策：
- 是否立即执行 Plan 01（agent-proto+common）开始实际编码？
- 是否继续生成剩余 8 份完整编码计划（Plan 03-10）？
- 还是先推进阶段 2（前端低代码控制台详设）？

### 推荐技能
- 执行 Plan：`superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`
- 后续模块 Plan：`writing-plans`
- 阶段 2 前端详设：`brainstorming` + `frontend-design` + `prd`

---

## 📅 2026-06-27 会话记录：阶段 2 前端低代码控制台详设

### 会话目标
按用户要求"按 1、2、3、4 顺序执行"，推进阶段 2：基于补遗 §6 的 5 个前端模块，生成前端控制台详细设计文档。

### 阶段 2 产出

| 文档 | 行数 | 5 大模块 | 子组件数 | API 映射 | Mermaid 图 |
|---|---|---|---|---|---|
| `docs/12-frontend/frontend-console-design.md` | 2110 | 运营后台 / Agent 配置工作台（低代码）/ 调试沙箱 / 终端对话界面 / 任务监控大屏 | 47 | 72 条 | 5 张 |

### 技术栈选型
- React 18 + TypeScript 5.4 + Vite 5.2 + pnpm workspace
- Ant Design 5.16（企业级管理后台）
- Monaco Editor 0.47（Prompt 编辑）
- ReactFlow 12（DAG 可视化）
- ECharts 5.5（监控大屏）
- Zustand 4.5（状态管理）
- SSE EventSource + WebSocket（实时通信）

### 5 个模块设计要点

**1. 运营后台**（§2，12 子组件，15 API）
- 租户管理 CRUD + Agent 上下线审批流 + 配额配置矩阵 + 审计查询 + 运营报表 ECharts
- Mermaid sequenceDiagram：租户创建 → Agent 审批 → 上线

**2. Agent 配置工作台（低代码）**（§3，11 子组件，17 API）— 重点详设
- 9 大配置组件：角色设定（Monaco + core_constraints 只读保护）/ 工具权限（R1/R2/R3 分组勾选）/ 记忆范围 / 输出规则 / 模型档位 / 反思模式 / Prompt 预览 / 沙箱试跑 / 版本管理（草稿→发布→灰度→全量）
- Mermaid flowchart TD：配置 → 预览 → 试跑 → 发布 → 灰度 → 全量

**3. 调试沙箱**（§4，8 子组件，13 API）
- 任务模拟输入 + DAG 可视化（ReactFlow）+ 断点调试 + 步骤回放 + Token 水位实时展示 + 执行日志
- Mermaid sequenceDiagram：启动 → DAG 渲染 → 断点 → 步进 → 回放

**4. 终端对话界面**（§5，8 子组件，14 API）
- 消息流（流式打字效果）+ SSE 流式输出 + 文件上传 + 任务进度展示 + 结果反馈
- Mermaid sequenceDiagram：发送消息 → SSE 流式 → 任务进度 → 结果反馈

**5. 任务监控大屏**（§6，8 子组件，13 API）
- KPI 指标卡片 + 实时任务状态仪表盘 + 链路追踪树形 + 异常告警 + 成本看板趋势图
- Mermaid sequenceDiagram：数据加载 → 实时更新 → 告警 → 下钻追踪

### 关键设计决策

- **低代码工作台为核心**：9 大子组件每个都明确了 props/state/事件，是 5 个模块中设计最详尽的
- **API 端点严格对齐** doc 02-api：所有 REST 路径、SSE 事件类型（token/tool_call/tool_result/done）均与后端定义一致
- **WebSocket 通道补充**：补遗 §6 未明示的实时推送端点（`/ws/tasks/{id}/token-water`、`/ws/monitor/screen`）作为前端约定，待后端实现时对齐
- **状态管理三层**：Zustand 全局（鉴权/用户）+ 模块（会话/任务）+ 局部（组件内），避免巨型 store
- **微服务端口对应**：8 个后端服务（8080-8104）全部映射到对应前端模块的 API 调用

### 阶段 2 完成确认

✅ 已完成：
- 1 份前端控制台详设文档（2110 行，47 子组件，72 API 映射，5 Mermaid 流程图）
- README.md 索引更新（文档总数 18 → 19，新增「七、前端控制台详设」分组）
- project_memory.md 阶段成果记录

### 推荐技能
- 前端编码实施：`frontend-dev` + `web-design-guidelines` + `artifacts-builder`
- 前端组件开发：`frontend-skill` + `design-brief`
- 阶段 3 DDL：`writing-plans` + `executing-plans`

---

## 📅 2026-06-27 会话记录：阶段 3 DDL 脚本编写

### 会话目标
按用户要求"按 1、2、3、4 顺序执行，每个阶段完成都要记录阶段成果到项目开发记录中"，推进阶段 3：基于 doc 01-database 设计，生成全量 DDL 初始化脚本（MySQL / ClickHouse / Milvus / Neo4j / Redis）+ 种子数据 + 编排脚本。

### 阶段 3 产出

**目录**：`infra/sql/`（项目根目录下，与 docs/ 平级）

| # | 脚本 | 行数 | 内容 | 对应 doc 01 章节 |
|---|---|---|---|---|
| 1 | `mysql/01-agent-session.sql` | 64 | session / session_message | §1 |
| 2 | `mysql/02-agent-task.sql` | 216 | task_instance(23 业务字段一对一映射) / subtask_instance / dag_definition / dag_node / dag_edge / replan_log / human_input | §2 |
| 3 | `mysql/03-agent-memory.sql` | 95 | memory_long_term / memory_distill_log / memory_recall_log | §3 |
| 4 | `mysql/04-agent-tool.sql` | 133 | tool_registry / tool_call_log / tool_quota / tool_approval | §4 |
| 5 | `mysql/05-agent-model.sql` | 82 | model_provider / model_route_rule / model_usage_log | §5 |
| 6 | `mysql/06-agent-repo.sql` | 87 | agent_definition / agent_version / agent_metrics_daily_snapshot | §6 |
| 7 | `mysql/07-agent-knowledge.sql` | 90 | knowledge_base / knowledge_document / knowledge_chunk | §7 |
| 8 | `mysql/08-agent-quality.sql` | 115 | eval_task / eval_baseline / badcase / audit_log | §8 |
| 9 | `mysql/09-agent-risk.sql` | 81 | permission_policy / role / role_permission | §9 |
| 10 | `mysql/10-clickhouse-metrics.sql` | 56 | agent_metrics_daily + drift_metrics_daily（ClickHouse MergeTree 引擎） | §8.4 / §2.4 |
| 11 | `mysql/11-seed-data.sql` | 323 | 5 供应商 14 模型 / 6 路由规则 / 3 任务模板 / 4 角色 38 权限 / 5 工具 / 3 Agent / 3 基准集 | §5.1 / §6.1 / §4.1 / §9 |
| 12 | `milvus/01-init-collections.py` | 211 | 6 Collection（mem_episodic/mem_semantic/mem_procedural/code_snippet/code_graph/agent_skill）HNSW M=16 efC=256 COSINE + Partition | §10.1 |
| 13 | `neo4j/01-init-constraints.cypher` | 65 | 7 节点类型唯一约束 + 6 索引 | §11.1 |
| 14 | `neo4j/02-init-relationships.cypher` | 89 | 6 关系类型（CONTAINS/CALLS/DEFINES/DEPENDS_ON/INHERITS/IMPORTS）+ 示例图谱 | §11.2 |
| 15 | `redis/01-init-data.redis` | 92 | 热点配置 / 限流桶 / TTL / 灰度配置 | §12 |
| 16 | `init-all.ps1` | 359 | 参数化编排脚本（-DbType / -TenantId / -DryRun / -LogFile），纯英文 | - |

**总计**：16 个文件，2158 行，覆盖 9 个 MySQL 逻辑库 32 张表 + 1 个 ClickHouse 指标库 2 张表 + 6 个 Milvus Collection + 7 个 Neo4j 节点类型 + 6 关系类型 + Redis 热点数据。

### 关键设计决策

- **task_instance 23 字段严格对齐** doc 01-database §2.1：task_id/tenant_id/session_id/user_id/title/goal/complexity/status/task_schema/dag_id/agent_id/priority/parent_task_id/replan_count/cost_limit_cent/cost_used_cent/token_used/started_at/finished_at/error_code/error_msg/result_summary/created_at/updated_at 一对一映射，避免编码阶段字段名/类型不一致返工
- **ClickHouse 独立脚本**：agent_metrics_daily 与 drift_metrics_daily 使用 MergeTree 引擎 + 分区 by toYYYYMMDD(event_date)，与 MySQL 分开存储，避免混入 MySQL DDL
- **Milvus HNSW 参数统一**：M=16 / efC=256 / COSINE 距离，6 个 Collection 共用，分域通过 Partition 实现（domain 维度）
- **Neo4j 关系类型预定义**：6 种关系类型与 doc 07-code-retrieval 一致（CONTAINS/CALLS/DEFINES/DEPENDS_ON/INHERITS/IMPORTS），约束先于关系建立
- **种子数据价格对齐** doc 09 §3.2.d：GPT-4o $5/$15、Claude 3.5 Sonnet $3/$15、Gemini 1.5 Pro $1.25/$5、qwen-max 0.04/0.12 元/千Token 等价格以 model_provider 表 input_price_per_million / output_price_per_million 字段存储
- **编排脚本纯英文**：遵循用户规则避免 GBK 编码问题，参数化 -DbType 支持单库/全量执行，-DryRun 支持预检
- **Redis 限流桶按租户维度**：`ratelimit:{tenantId}:default` Hash 结构存储令牌桶配置，与 doc 09 §4 限流策略一致

### 自检结果

1. **doc 01 覆盖**：MySQL 9 个逻辑库 32 张表全部覆盖，无遗漏
2. **字段一致性**：task_instance 23 字段与 doc 01-database §2.1 一对一映射，类型一致（VARCHAR/BIGINT/INT/JSON/DATETIME）
3. **索引覆盖**：所有高频查询字段（tenant_id/session_id/task_id/agent_id/status/created_at）均建立索引
4. **外键策略**：采用逻辑外键（无物理 FK 约束），便于分库分表，与 doc 01 一致
5. **字符集统一**：所有 MySQL 表 utf8mb4_unicode_ci，与 doc 01 一致

### 索引更新
- `docs/README.md`：新增「八、基础设施脚本（DDL 初始化）」分组（16 脚本明细 + 行数 + 对应章节），顶部描述更新为「19 份文档 + infra/sql/ 16 个 DDL 脚本」

### 阶段 3 完成确认

✅ 已完成：
- 16 个 DDL 初始化脚本落盘（2158 行，9 MySQL 库 32 表 + ClickHouse 2 表 + Milvus 6 Collection + Neo4j 7 节点 + Redis 热点数据）
- 完整种子数据（14 模型 / 6 路由 / 3 任务模板 / 4 角色 38 权限 / 5 工具 / 3 Agent / 3 基准集）
- 参数化编排脚本 init-all.ps1（纯英文，-DbType/-TenantId/-DryRun/-LogFile）
- README.md 索引更新
- project_memory.md 阶段成果记录

### 执行反思

- ✅ **采用子代理完整读取 doc 01 后生成**：避免字段遗漏，task_instance 23 字段一对一映射
- ✅ **种子数据价格对齐 doc 09 §3.2.d**：14 模型价格与回填后的主文档一致
- ✅ **编排脚本纯英文**：遵循用户规则避免 GBK 编码问题
- ⚠️ **尚未实际执行 DDL**：本轮仅生成脚本，未在 MySQL/ClickHouse/Milvus/Neo4j/Redis 实际执行验证。建议后续编码阶段使用 Docker Compose 启动依赖中间件后执行 `init-all.ps1` 验证
- ⚠️ **Milvus Partition 按域**：6 个 Collection 的 Partition 设计按 domain 切分，如未来跨域查询需求增加可能需重新设计
- ⚠️ **Neo4j 示例图谱**：02-init-relationships.cypher 含示例数据用于验证关系类型，生产部署时可删除示例行

### 后续建议（next session）

1. **执行 DDL 验证**：编写 docker-compose.yml 启动 MySQL 8.0.36 + Milvus 2.4 + Neo4j 5.18 + Redis 7.2 + ClickHouse，运行 `init-all.ps1` 验证脚本正确性
2. **进入阶段 4**：Mermaid 渲染校验，逐一渲染 12 张决策流程图检查语法（重点：subgraph 嵌套、含 `<` 的边标签、HTML 实体转义）
3. **编码阶段**：DDL 脚本可作为后续所有微服务编码的数据基础，Testcontainers 集成测试可直接 import 这些脚本初始化数据库

### 推荐技能
- DDL 验证：`executing-plans` + Docker Compose
- 阶段 4 Mermaid 校验：`agent-browser`（浏览器渲染 Mermaid）或本地 mmdc CLI
- 后续微服务编码：`writing-plans` + `tdd` + `executing-plans`

---

## 📅 2026-06-27 会话记录：阶段 4 Mermaid 渲染校验

### 会话目标

按用户要求"按 1、2、3、4 顺序执行，每个阶段完成都要记录阶段成果到项目开发记录中"，推进阶段 4：对 3 个详细逻辑流程图文档中的 12 张决策流程图（F1-F12）做 Mermaid 语法校验，捕获语法/节点声明/边定义错误并修复。

### 阶段 4 工作目录

`docs/11-detail-flow/_mermaid-validate/`

| # | 文件 | 行为 | 作用 |
|---|---|---|---|
| 1 | `extract-and-build-html.mjs` | 保留 | 从 3 个流程图文档提取 12 个 mermaid 代码块（过滤掉仅含 classDef 的全局样式块），生成 HTML 校验页 |
| 2 | `validate.html` | 保留 | HTML 校验页（备用，引入 mermaid@10.9.1 CDN jsdelivr） |
| 3 | `validate-jsdom.mjs` | 新建（本轮主力） | Node.js 服务端校验脚本：用 jsdom 创建虚拟 DOM + 挂载 mermaid 所需全局对象 + 调用 `mermaid.parse(code)` 逐一校验语法 |
| 4 | `validate-report.md` | 新建 | 校验报告（Markdown 表格 + 失败列表 + 校验方法说明），12/12 OK |
| 5 | `validate.mjs` | 保留（弃用） | puppeteer-core + 便携版 Chrome 方案，因便携 Chrome 149 不支持 headless 而弃用 |
| 6 | `test.mmd` | 保留 | mmdc CLI 测试文件，因 chromium 下载被墙而弃用 |
| 7 | `package.json` | 保留 | 依赖：jsdom@^29.1.1 + mermaid@^10.9.1 + puppeteer-core@^25.2.1（puppeteer-core 已无用，可后续清理） |
| 8 | `package-lock.json` | 保留 | 锁定依赖版本 |

### 校验方案演进（4 次尝试）

| # | 方案 | 结果 | 原因 |
|---|---|---|---|
| 1 | puppeteer-core + 便携版 Chrome（D:\_program\Chrome\App\chrome.exe） | ❌ 失败 | 便携版 Chrome 149 不支持标准 headless 模式，puppeteer.launch 退出 Code: 0 无 stderr；尝试 headless:'new' / headless:true / 唯一 tmpUserDataDir / dumpio 均无效 |
| 2 | npx -y -p @mermaid-js/mermaid-cli@10.6.1 mmdc | ❌ 失败 | chromium 二进制下载被墙（ENOENT），puppeteer 下载了但 chromium 不在 `C:\Users\Administrator\.cache\puppeteer\...`；`--puppeteer-config` 选项不存在 |
| 3 | Edge 浏览器（替代 Chrome） | ❌ 失败 | `C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe` 不存在 |
| 4 | jsdom + mermaid@10.9.1 npm 包 | ✅ 成功 | npm install 安装 143 packages；mermaid.parse() 在 jsdom 虚拟 DOM 下能正常解析 mermaid 语法 |

### 校验结果

**12/12 全部通过**（exit code 0）。校验报告：`docs/11-detail-flow/_mermaid-validate/validate-report.md`。

| # | 标题 | 源文件 | 行数 | 状态 |
|---|---|---|---|---|
| 1 | F1 接入网关请求处理流程 | 01-access-and-planning-flow.md | 66 | ✅ OK |
| 2 | F2 意图识别与复杂度判定决策树 | 01-access-and-planning-flow.md | 63 | ✅ OK |
| 3 | F3 任务规划与 DAG 生成流程 | 01-access-and-planning-flow.md | 83 | ✅ OK |
| 4 | F4 子任务分发与并行调度流程 | 01-access-and-planning-flow.md | 76 | ✅ OK |
| 5 | F5 动态重规划决策流程 | 02-runtime-and-replan-flow.md | 67 | ✅ OK（修复后） |
| 6 | F6 ReAct 循环详细决策流程 | 02-runtime-and-replan-flow.md | 78 | ✅ OK |
| 7 | F7 Token 水位压缩决策流程 | 02-runtime-and-replan-flow.md | 57 | ✅ OK |
| 8 | F8 工具选择与调用决策流程 | 02-runtime-and-replan-flow.md | 100 | ✅ OK |
| 9 | F9 三级质量校验决策流程 | 03-quality-and-memory-flow.md | 54 | ✅ OK |
| 10 | F10 幻觉治理六层联动流程 | 03-quality-and-memory-flow.md | 92 | ✅ OK |
| 11 | F11 漂移监测与纠偏决策流程 | 03-quality-and-memory-flow.md | 84 | ✅ OK |
| 12 | F12 长期记忆写入与召回决策流程 | 03-quality-and-memory-flow.md | 102 | ✅ OK |

### 修复的真实语法错误

| # | 文件 | 行号 | 修复前 | 修复后 | 原因 |
|---|---|---|---|---|---|
| 1 | `docs/11-detail-flow/02-runtime-and-replan-flow.md` | 71 | `I5{detectCycle(mergedDag) == true?}:::decision` | `I5{"detectCycle(mergedDag) == true?"}:::decision` | 菱形判断节点 `{...}` 内未用双引号包裹且含括号 `()`，mermaid 解析器将 `(` 识别为 PS token 触发 Parse error。其他判断节点（如 D4）含括号但因已用 `"..."` 包裹而通过。 |

### 工具限制（非真实语法问题，记录备查）

- **mermaid.render() 在 jsdom 下不可用**：mermaid 的 render 阶段调用 SVG 元素的 `getBBox()` 方法测量文本尺寸，jsdom 不实现该 API，会抛出 `text.getBBox is not a function`。这不是 mermaid 语法错误，是 jsdom 的 SVG 实现不完整。`mermaid.parse()` 是 mermaid 官方推荐的纯语法校验入口，已覆盖本阶段目标。如需完整渲染校验，可改用浏览器自动化（agent-browser / playwright + 真实 Chrome）方案。
- **先前担心的语法陷阱全部经 parse 验证为正常**：
  - F2/F3 的内联节点声明 `D1a{retry.count < 2?}`（边中内联声明 + 含 `<`）：parse 通过
  - F7 的边标签 `D1 -->|true 安全 <=89.6K| Safe`（含 `<=`）：parse 通过
  - F12 的虚线边 `EndW -. 触发 Agent 推理 .-> R1`：parse 通过
  - F10 的 6 个 subgraph 嵌套 + 边跨 subgraph 引用：parse 通过
  - F12 的 2 个 subgraph + 虚线边：parse 通过

### 索引更新

- `docs/README.md` 第五节「详细逻辑流程图」末尾追加「Mermaid 语法校验」说明，列出校验工具入口（`extract-and-build-html.mjs` / `validate-jsdom.mjs` / `validate-report.md`）与结果（12/12 OK）

### 阶段 4 完成确认

✅ 已完成：
- 12 张 Mermaid 决策流程图（F1-F12）全部语法校验通过（mermaid.parse 在 jsdom 下）
- 修复 1 处真实语法错误（F5 的 I5 节点未加双引号）
- 校验脚本与报告归档到 `docs/11-detail-flow/_mermaid-validate/`
- README.md 第五节追加校验入口
- project_memory.md 阶段成果记录

### 执行反思

- ✅ **校验方案最终落地**：经历 4 次尝试（puppeteer/Chrome → mmdc → Edge → jsdom+mermaid），最终用 jsdom + mermaid npm 包在 Node.js 服务端完成校验，无需浏览器依赖
- ✅ **parse 校验覆盖足够**：mermaid.parse 是 mermaid 官方推荐的语法校验入口，能捕获语法/节点声明/边定义等真实问题；render 失败属于 jsdom 工具限制而非语法问题
- ✅ **保留弃用脚本作为决策证据**：validate.mjs（puppeteer 方案）和 test.mmd（mmdc 测试）保留在 _mermaid-validate/ 目录，便于后续 Agent 了解方案演进历史
- ⚠️ **未做完整渲染校验**：jsdom 不支持 mermaid.render（缺 getBBox），如需可视化渲染校验可后续用 agent-browser + 真实 Chrome 方案；但本阶段目标是语法校验，已达成
- ⚠️ **puppeteer-core 依赖未清理**：package.json 中仍保留 puppeteer-core 依赖（实际已弃用），后续清理项目时可移除
- ⚠️ **doc 02 §0.3 Mermaid 约定已用双引号**：`{"表达式 == true?"}`；但 doc 01 §0.3 与 doc 03 §0.3 仍写为 `{表达式 == true?}`（无引号），实际图中所有判断节点都已加引号，仅约定表文字描述未同步。后续可统一约定表文字（低优先级，非阻塞）

### 后续建议（next session）

1. **进入编码阶段**：4 个阶段全部完成，可基于编码计划（`docs/plans/01-02`）+ DDL 脚本（`infra/sql/`）+ 设计文档（`docs/00-12`）开始落地代码
2. **建议补一份根 pom.xml**：13 个微服务统一版本管理（已在阶段 1 后续建议提及，仍未落实）
3. **可视渲染校验（可选）**：如需对 12 张图做可视化渲染（捕获视觉重叠/排版问题），可用 `agent-browser` 技能 + 真实 Chrome 打开 `validate.html`，等待 `window.__validationDone` 后截图检查
4. **清理 _mermaid-validate 临时目录**：本次校验脚本属于工具产出，按用户规则「探索类脚本不要立即删除，等明确下命令统一清理」，保留待后续用户决定

### 推荐技能

- 编码阶段：`writing-plans` + `tdd` + `test-driven-development` + `executing-plans`
- 可视渲染校验（可选）：`agent-browser`（用真实 Chrome 打开 validate.html）
- 代码审查：`TRAE-code-review`

---
