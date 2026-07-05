# Agent 平台编码计划总览

> 文档版本：v2.3 | 更新日期：2026-07-04 | 基于 15 份设计文档（11 主设计 + 1 补遗 + 3 详细流程图）
> 方法论：[writing-plans](../../project_memory.md) | TDD 红绿循环 | 无占位符 | bite-sized tasks
> 维护原则：每完成一个 Wave 后更新本表（Plan 状态 / Task 进度 / CI streak）

## 0. 总览

本平台共 11 个核心微服务 + 2 个横向服务，按依赖关系拆分为 **9 个独立编码计划**（Plan 01~Plan 09）。每个计划可独立交付可测试的软件单元，遵循 TDD 红绿循环。

### 当前总体进度（截至 Wave 40，CI streak=42）

| # | 计划名称 | 模块 | Task 进度 | 状态 | 最新 Wave |
|---|---|---|---|---|---|
| 01 | [agent-proto-and-common](./01-agent-proto-and-common-plan.md) | agent-proto + agent-common | 8/8 | ✅ 已完成 | Wave 1~4 |
| 02 | [agent-gateway-session](./02-agent-gateway-session-plan.md) | agent-gateway(8080) + agent-session(8082) | 10/10 | ✅ 已完成 | Wave 5~11 |
| 03 | [agent-memory](./03-agent-memory-plan.md) | agent-memory(8088/9088) | 10/10 | ✅ 已完成 | Wave 30~39 |
| 04 | [task-orchestrator-planning](./04-task-orchestrator-planning-plan.md) | agent-task-orchestrator(8084) + agent-planning(8086) | 13/13 | ✅ 已完成 | P6 Wave 1~2 |
| 05 | [agent-tool-engine](./05-agent-tool-engine-plan.md) | agent-tool-engine(8090/9090) | 12/12 | ✅ 已完成 | — |
| 06 | [agent-runtime](./06-agent-runtime-plan.md) | agent-runtime(8092/9092) | 10/10 | ✅ 已完成 | — |
| 07 | [agent-model-gateway](./07-agent-model-gateway-plan.md) | agent-model-gateway(8094/9094) | 14/14 | ✅ 已完成 | Wave 18~29, 40 |
| 08 | [agent-repo-knowledge](./08-agent-repo-knowledge-plan.md) | agent-repo(8096) + agent-knowledge(8098) | 12/12 | ✅ 已完成 | Wave 19~26, 40 |
| 09 | [infra-deployment](./09-infra-deployment-plan.md) | infra/k8s + docker + nacos | 13/13 | ✅ 已完成 | — |

### 各 Plan 详细进度

#### Plan 01 — agent-proto + agent-common（✅ 已完成，8/8 Task）
- 8 个 .proto 文件全部编译通过（common / task / planning / memory / model / tool / knowledge / runtime）
- 11 个 Java 类（DTO / 异常 / 工具 / 枚举）47 测试用例全绿
- 验收：Wave 1~4 闭合，JaCoCo line ≥80% / branch ≥70%

#### Plan 02 — agent-gateway + agent-session（✅ 已完成，10/10 Task）
- agent-gateway(8080)：AuthFilter / ContentSafetyFilter / RateLimitFilter / TaskController
- agent-session(8082)：SessionRepository / ShortTermMemoryService / SessionController / EndToEndTest
- 43 个 Java 类全绿 + EndToEndTest 通过
- 验收：Wave 5~11 闭合，CI streak=10 达成 A- 等级

#### Plan 03 — agent-memory（✅ 已完成，10/10 Task）
| Task | 状态 | 完成 Wave | 说明 |
|---|---|---|---|
| T1 基础设施 | ✅ | Wave 30 | MemoryProperties + MilvusClientConfig + EmbeddingClientConfig |
| T2 JPA Entity + Repository | ✅ | Wave 30 | MemoryRecord @Entity + MemoryRecordRepository |
| T3 MemoryExtractor | ✅ | Wave 31 | REFLECTIVE + 过滤 + 自动分流 |
| T4 MemoryDistiller | ✅ | Wave 33 | gRPC Chat RPC + 源归档 + 聚合 importance |
| T5 EmbeddingClient | ✅ | Wave 36 | HTTP /v1/embeddings + 3 次重试 + Caffeine 缓存 |
| T6 MemoryVectorStore + Milvus | ✅ | Wave 39 | 双轨策略：InMemory fallback + Milvus 条件装配 + @Disabled 集成测试 |
| T7 ImportanceScorer | ✅ | Wave 34 | 5 维度加权 + level 分级 + dimensions 明细 |
| T8 MemoryTtlManager | ✅ | Wave 32 | applyTtl 状态机 + cleanupExpired + Scheduler |
| T9 MemoryDeduper | ✅ | Wave 32 | dedup + DedupReport + repository-backed |
| T10 MemoryService gRPC | ✅ | Wave 37 | 4 RPC（WriteLongTerm/Recall/TriggerDistill/GetMemoryById）+ 完整写入流程 + 189 tests |

#### Plan 04 — agent-task-orchestrator + agent-planning（✅ 已完成，13/13 Task）
| Task | 状态 | 说明 |
|---|---|---|
| T1 项目骨架 | ✅ | Spring Boot 启动类 + application.yml |
| T2 TaskInstance 实体 | ✅ | 23 业务字段 JPA 实体 + Repository |
| T3 DAG 引擎 | ✅ | DagGraph + DagValidator + TopologicalSorter |
| T4 状态机 | ✅ | 10 状态流转矩阵校验 |
| T5 TaskOrchestrator gRPC | ✅ | 4 RPC 服务端（SubmitTask / GetTaskStatus / CancelTask / ReportSubtaskResult） |
| T6 复杂度识别 | ✅ | 6 维度评分 + 规则初筛 |
| T7 PlanningService gRPC | ✅ | 4 RPC 服务端（AssessComplexity / Plan / ValidatePlan / Replan） |
| T8 模板匹配 | ✅ | 场景标签匹配 + 成功率过滤 |
| T9 5 维度 DAG 校验 | ✅ | 完备性/原子性/效率/成本/容错 |
| T10 并行批次调度 | ✅ | 拓扑分层 → 同层无依赖归同批次 |
| T11 RocketMQ 集成 | ✅ | 4 topic（execute/done/state.change/cancel） |
| T12 重规划模式选择 | ✅ | 增量 / 全量 / 人工 |
| T13 端到端集成测试 | ✅ | H2 + jedis-mock + InProcess gRPC |

#### Plan 05 — agent-tool-engine（✅ 已完成，12/12 Task）
224 unit tests, 49 classes. T1-T12 全部完成。
- 4 RPC：CallTool / RegisterTool / ListTools / GetToolMeta
- 9 项核心能力：ToolRegistry / ToolGateway / RiskClassifier / ApprovalStore / SandboxBorrower / ToolCache / ToolCallAuditor / ToolSemanticRecaller / ResultCleaner
- 依赖：agent-proto / agent-common；F8 工具选择骨架已有（P7-3）

#### Plan 06 — agent-runtime（✅ 已完成，10/10 Task）
163 unit tests, 72+ classes. T1-T10 全部完成。
- 5 RPC：StartAgent / Step / GetState / Pause / Resume
- ReActLoop (Think→Act→Observe) + ReflexionEngine + SessionManager
- ModelGatewayClient + ToolEngineClient (gRPC stub + Resilience4j CB/Retry)
- TokenWatermarkMonitor + StepStateSyncer + TokenBudgetCalculator
- Resilience4j 熔断 (model-gateway: 5 failures→30s, tool-engine: 3 failures→30s) + 重试 (3 attempts, exponential backoff)
- 依赖：agent-proto / agent-common；F6 ReAct 骨架已有（P7-4）

#### Plan 07 — agent-model-gateway（✅ 已完成，14/14 Task）
| Task | 状态 | 完成 Wave | 说明 |
|---|---|---|---|
| T1 骨架 | ✅ | Wave 18 | Spring Boot 启动类 + 配置 |
| T2-T3 Entity + Repository | ✅ | Wave 21 | model_provider + model_route_rule JPA |
| T4-T7 多供应商适配器 | ✅ | Wave 18~20 | OpenAI / Anthropic / Gemini / 国内模型 |
| T8 Chat gRPC 服务 | ✅ | Wave 27 | Chat RPC 实现 |
| T9 StreamChat server streaming | ✅ | Wave 29 | reactor-core Flux + onBackpressureBuffer |
| T10 CountTokens + ListModels | ✅ | Wave 28 | 含中文 1.7 倍系数 |
| T11 PromptCache | ✅ | Wave 18 | Redis 缓存相同前缀 |
| T12 CostMeter + JPA | ✅ | Wave 23 | model_usage_log 计量 |
| T13 ModelDegradationManager | ✅ | Wave 18 | 故障自动降级 |
| T14 集成测试 | ✅ | Wave 40 | InProcess gRPC + Mockito stubs 6 E2E 场景（Chat/StreamChat/CountTokens/ListModels/PromptCache/Degradation） |

#### Plan 08 — agent-repo + agent-knowledge（✅ 已完成，12/12 Task）
| Task | 状态 | 完成 Wave | 说明 |
|---|---|---|---|
| T1-T6 agent-repo（CRUD/版本/快照/绑定） | ✅ | Wave 19+22 | 骨架 + JPA 持久化 |
| T4 AgentRepo gRPC 服务 | ✅ | Wave 29 | 4 RPC + 8 tests |
| T7 agent-knowledge 骨架 | ✅ | Wave 18 | Spring Boot 启动类 |
| T8 knowledge_base + knowledge_chunk JPA | ✅ | Wave 24 | 双表 JPA 实体 |
| T9 DocumentIngestor + TokenCounter | ✅ | Wave 25 | 文档分块 + token 估算 |
| T10 EmbeddingService + Milvus | ✅ | Wave 40 | 双轨策略：InMemory fallback + Milvus 条件装配 + @Disabled 集成测试 |
| T11 KnowledgeBase gRPC 服务 | ✅ | Wave 26 | 4 RPC（IngestDocument / SearchChunks / ListBases / DeleteBase） |
| T12 集成测试 | ✅ | Wave 40 | H2 + JPA + InProcess gRPC 6 E2E 场景（Ingest/ListBases/DeleteBase KB_IN_USE/force/not-found/SearchChunks） |

#### Plan 09 — infra 部署（✅ 已完成，13/13 Task）
- 90 个部署配置文件 (Dockerfile + docker-compose + K8s + Nacos + Vault + 可观测 + 脚本)
- 13 微服务 Dockerfile (multi-stage build, base: JRE17 + SkyWalking 9.7)
- docker-compose 本地一键起 (14 中间件 + 12 业务服务)
- K8s: 4 namespace + 12 SA + 12 Deployment + 12 Service + 1 Ingress + 6 HPA + 1 PDB
- Nacos 配置中心 (bootstrap + 5 shared + 2 服务级 prod + import 脚本)
- Vault 密钥 (12 Policy + vault-seeds.sh + K8s SA 认证)
- SkyWalking / Prometheus / Loki / Grafana 可观测组件
- PowerShell 部署脚本 (build-all + deploy + deploy-middleware + health-check)
- 验证: PS 8/8 OK, YAML 56/56 OK, JSON 1/1 OK
- 依赖：全部业务服务可启动后执行

---

## 1. 依赖关系图

```
┌──────────────────────────────────────────────────────────────────────┐
│  P0 基础层（无依赖，最先执行）                                          │
│  ┌──────────────────────────┐    ┌─────────────────────────────────┐  │
│  │ Plan 01: agent-proto     │    │ Plan 02: agent-gateway+session  │  │
│  │ + agent-common           │    │ （接入层，已先期完成）            │  │
│  │ （8 Task，✅ 已完成）     │    │ （10 Task，✅ 已完成）            │  │
│  └────────────┬─────────────┘    └────────────┬────────────────────┘  │
└───────────────┼─────────────────────────────────┼─────────────────────┘
                │                                 │
                ▼                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  P1 核心引擎层（依赖 P0）                                              │
│  ┌──────────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │ Plan 04:          │  │ Plan 03:     │  │ Plan 05:     │            │
│  │ task-orchestrator │  │ memory       │  │ tool-engine  │            │
│  │ + planning        │  │ (10 Task)    │  │ (12 Task)    │            │
│  │ (13 Task)         │  │ ✅ 10/10     │  │ ⏳ 0/12      │            │
│  │ ✅ 13/13          │  └──────┬───────┘  └──────┬───────┘            │
│  └────────┬─────────┘         │                  │                    │
│           │                   │                  │                    │
│  ┌────────▼───────────────────▼──────────────────▼─────┐               │
│  │ Plan 07: model-gateway (14 Task, ✅ 14/14)           │               │
│  │ （被 task/memory/tool/runtime 依赖）                  │               │
│  └────────┬─────────────────────────────────────────────┘               │
└───────────┼────────────────────────────────────────────────────────────┘
            │
            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  P2 运行时与能力层（依赖 P1）                                          │
│  ┌──────────────────────┐  ┌──────────────────────────────────────┐    │
│  │ Plan 06: agent-runtime│  │ Plan 08: agent-repo + knowledge     │    │
│  │ (10 Task, ⏳ 0/10)    │  │ + quality (12 Task, ✅ 12/12)        │    │
│  └──────────┬───────────┘  └────────────────┬─────────────────────┘    │
└─────────────┼─────────────────────────────────┼────────────────────────┘
              │                                 │
              ▼                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  P3 基础设施（依赖全部服务）                                          │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ Plan 09: infra/k8s + docker + nacos (⏳ 0/?)                    │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

## 2. 建议执行顺序

### 阶段 A：基础层（P0）—— ✅ 已完成
- **Plan 01（proto+common）** ✅ Wave 1~4 闭合
- **Plan 02（gateway+session）** ✅ Wave 5~11 闭合（接入层提前完成便于联调）

### 阶段 B：核心引擎层（P1）—— ✅ 已完成
- **Plan 03（memory）** ✅ 10/10，Wave 30~39 闭合
- **Plan 04（task-orchestrator+planning）** ✅ 13/13，P6 Wave 1~2 闭合
- **Plan 07（model-gateway）** ✅ 14/14，Wave 18~29 + Wave 40 闭合
- **Plan 05（tool-engine）** ✅ 12/12，224 tests

### 阶段 C：运行时与能力层（P2）—— ✅ 已完成
- **Plan 06（agent-runtime）** ✅ 10/10，163 tests
- **Plan 08（repo+knowledge）** ✅ 12/12，Wave 19~26 + Wave 40 闭合

### 阶段 D：基础设施（P3）—— ✅ 已完成
- **Plan 09（K8s/Docker/Nacos）** ✅ 13/13，90 文件，全部部署配置就绪

## 3. 关键约定（所有 plan 必须遵循）

### 3.1 技术栈版本
- Java 17 / Spring Boot 3.2 / Spring Cloud Alibaba 2023.0.1.0
- gRPC 1.62.2 / protobuf-maven-plugin 0.6.1
- MySQL 8.0.36 / Milvus 2.4 / Redis 7.2 / RocketMQ 5.3 / Neo4j 5.18 / ES 8.13
- JUnit 5 / Mockito 5 / Testcontainers 1.19

### 3.2 Monorepo 目录结构
```
e:\git\Agent-Platform-Prototype\
├── agent-proto/          # Protobuf 契约层（Plan 01，✅）
├── agent-common/         # 公共工具层（Plan 01，✅）
├── agent-gateway/        # 接入网关（Plan 02，端口 8080，✅）
├── agent-session/        # 会话管理（Plan 02，端口 8082，✅）
├── agent-task-orchestrator/  # 任务编排（Plan 04，端口 8084，🔄）
├── agent-planning/       # 智能规划（Plan 04，端口 8086，🔄）
├── agent-memory/         # 记忆管理（Plan 03，端口 8088，🔄）
├── agent-tool-engine/    # 工具引擎（Plan 05，端口 8090，⏳）
├── agent-runtime/        # Agent 运行时（Plan 06，端口 8092，⏳）
├── agent-model-gateway/   # 模型网关（Plan 07，端口 8094，🔄）
├── agent-repo/           # Agent 仓库（Plan 08，端口 8096，🔄）
├── agent-knowledge/       # 知识服务（Plan 08，端口 8098，🔄）
├── agent-quality/        # 质量评估（端口 8100，骨架已有）
├── infra/
│   ├── sql/              # DDL 脚本（已由各 Plan 内嵌完成）
│   ├── k8s/              # K8s 部署（Plan 09，⏳）
│   ├── docker/           # Docker 配置（Plan 09，⏳）
│   ├── nacos/            # Nacos 配置（Plan 09，⏳）
│   └── observability/    # 可观测组件（Plan 09，⏳）
└── docs/                 # 设计文档与编码计划
    ├── plans/            # 本目录（9 份编码计划 + 1 份总览）
    ├── tests/            # 测试策略 / 用例 / TDD 红绿记录 / 审计报告
    ├── 00-overview/ ~ 11-detail-flow/  # 设计文档
    └── adr/              # 架构决策记录
```

### 3.3 5 条 ADR（编码时不得违反）
- ADR-001：自研 DAG 引擎（不依赖 Airflow）
- ADR-002：Agent 运行时无状态，状态外置 Redis `runtime:{agentInstanceId}:state`
- ADR-003：OpenAI 协议适配器
- ADR-004：Milvus 作为统一向量库
- ADR-005：工具调用统一走 tool-engine.ToolGateway gRPC 网关

### 3.4 TDD 红绿循环（每个 Task 必须遵循）
1. 写失败测试
2. 运行验证失败（红）
3. 写最小实现
4. 运行验证通过（绿）
5. git commit

### 3.5 错误码规范（对齐 doc 02-api §0.5）
所有 plan 的异常分支必须使用 `agent-common.ErrorCode` 枚举中的错误码，包括但不限于：
`UNAUTHENTICATED` / `RATE_LIMITED` / `CONTENT_BLOCKED` / `TOOL_NOT_FOUND` / `PARAM_INVALID` / `FORBIDDEN` / `QUOTA_EXCEEDED` / `COST_BUDGET_EXCEEDED` / `DAG_CYCLE_DETECTED` / `COMPLETENESS_FAIL` / `REPLAN_EXHAUSTED` / `HALLUCINATION_SUSPECTED` / `FACT_INCONSISTENCY` / `TIMEOUT` / `MAX_STEPS_EXCEEDED` / `CONTEXT_WINDOW_EXHAUSTED`

### 3.6 TDD 提交时序

> 本节由 TDD 第 2 轮审核 SEQ-02 一票否决项触发新增，避免后续新模块出现"测试与实现同 commit、无法事后拆分"的问题。

#### 3.6.1 三阶段独立提交规则

每个测试方法必须按 **Red → Green → Refactor** 三阶段独立 commit：

| 阶段 | 动作 | commit message 格式 |
|---|---|---|
| Red | 先写失败的测试（运行验证失败） | `test({module}): add failing test for {feature}` |
| Green | 写最小实现让测试通过（运行验证通过） | `feat({module}): implement {feature} to pass test` |
| Refactor | 重构代码（提取公共方法、改善命名、消除重复） | `refactor({module}): {重构内容}` |

- 若该 Task 无需重构，可跳过 Refactor 阶段，但 Red 与 Green 两个 commit 不可合并
- 每个阶段 commit 前必须本地运行测试，确认阶段状态符合预期（Red 阶段测试失败 / Green 阶段测试通过）

#### 3.6.2 禁止事项

- 禁止测试与实现同 commit 提交（违反 Uncle Bob TDD 三定律第 1 条）
- 禁止跳过 Red 阶段直接写实现
- 禁止在 Green 阶段写超出最小实现的代码（如提前做重构、加注释、加额外功能）
- 禁止一个 commit 同时覆盖多个测试方法的多轮红绿循环（每个测试方法独立一组 commit）

#### 3.6.3 commit message 规范

遵循 Conventional Commits：`type(scope): description`

| 字段 | 取值 |
|---|---|
| type | `test` / `feat` / `refactor` / `fix` / `docs` / `chore` / `ci` |
| scope | 模块名：`proto` / `common` / `gateway` / `session` / `orchestrator` / `planning` / `memory` / `tool` / `runtime` / `model` / `repo` / `knowledge` / `quality` / `infra` |
| description | 简洁英文描述，动词原形开头，首字母小写 |

#### 3.6.4 示例（以 agent-task-orchestrator 模块为例）

```
commit 1: test(orchestrator): add failing test for TaskStateMachine.legalTransition
commit 2: feat(orchestrator): implement TaskStateMachine.canTransitTo
commit 3: refactor(orchestrator): extract transition matrix to enum
```

#### 3.6.5 审核要求

- 每个模块开发完成后，审核员会检查 `git log` 中该模块的 commit 序列
- 审核员通过 `git log --oneline -- {module-path}` 查看模块提交历史，确认每个测试方法都有独立的 Red / Green commit
- 如果发现测试与实现同 commit，视为 **SEQ-02 违规**，触发一票否决，该模块需返工重做
- 如果发现跳过 Red 阶段（无 `test(...)` commit 直接出现 `feat(...)` commit），同样视为 SEQ-02 违规

## 4. 已完成 Plan 的执行回顾

### Plan 01（agent-proto + agent-common）✅
- **执行方式**：Subagent-Driven（每个 Task 派发独立子代理）
- **关键步骤**：先 `mvn install` agent-proto，再编译 agent-common
- **验收标准**：8 个 .proto 文件全部编译通过 + 11 个 Java 类 47 个测试用例全绿
- **完成 Wave**：Wave 1~4

### Plan 02（agent-gateway + agent-session）✅
- **执行方式**：Subagent-Driven（Task 7 依赖 Task 6 实体；Task 9 依赖 Task 8 记忆服务）
- **关键依赖**：需先执行 Plan 01 完成 agent-proto + agent-common
- **验收标准**：10 个 Task 43 个 Java 类全绿 + EndToEndTest 通过
- **Testcontainers**：集成测试需 Docker 环境，Task 7/8/10 涉及 MySQL/Redis Testcontainers
- **完成 Wave**：Wave 5~11（CI streak=10 达成 A- 等级）

## 5. 进行中 Plan 的下一步建议

### 优先级排序（基于依赖关系与已闭合情况）

| 优先级 | Plan / Task | 理由 |
|---|---|---|
| 高 | Plan 09 infra-deployment | 8/9 计划已完成，可开始 K8s/Docker/Nacos 部署 |
| 最后 | 全平台文档同步 | Wave 47 最终验证 |

## 6. 变更记录

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.0 | 2026-06-27 | 初始版本，列出 10 份计划（含待生成的 8 份），定义依赖关系、执行顺序、TDD 提交时序约定 |
| v2.0 | 2026-07-04 | 重写：①Plan 编号对齐实际文件（03=agent-memory / 04=task-orchestrator / 05=tool-engine / 06=runtime / 07=model-gateway / 08=repo+knowledge / 09=infra）；②删除"待生成 8 份"表述（全部 9 份 plan 已生成）；③更新各 Plan 真实进度（Plan 03 7/10、Plan 04 9/13、Plan 07 13/14、Plan 08 7/12）；④更新依赖图与执行顺序建议；⑤新增已完成 Plan 执行回顾与进行中 Plan 下一步建议 |
| v2.1 | 2026-07-04 | 同步 Wave 36 进度：①Plan 03 进度 7/10 → 8/10（T5 EmbeddingClient HTTP 实现完成）；②CI streak 33 → 36；③依赖图 Plan 03 标注 8/10；④阶段 B 描述更新（T5 移出待做列表）；⑤优先级排序表移除已完成的 T5 |
| v2.2 | 2026-07-04 | 同步 Wave 37 进度：①Plan 03 进度 8/10 → 9/10（T10 MemoryService gRPC 4 RPC 完成）；②CI streak 36 → 39；③依赖图 Plan 03 标注 9/10；④T10 标记完成并更新说明；⑤优先级排序表移除已完成的 T10 |
| v2.3 | 2026-07-04 | 同步 Wave 40 进度：①Plan 07 进度 13/14 → 14/14（T14 集成测试 6 E2E 场景完成）；②Plan 08 进度 7/12 → 12/12（T10 Milvus 双轨 + T12 集成测试 6 E2E 场景完成）；③CI streak 39 → 42；④依赖图全量更新（Plan 03/04/07/08 标记 ✅）；⑤阶段 B → ✅ 基本完成，阶段 C → 🔄 进行中；⑥优先级排序更新（Plan 05/06 高优先级） |
| v2.4 | 2026-07-06 | 同步进度：①Plan 05 进度 0/12 → 12/12（224 tests, 49 classes, T1-T12 全部完成）；②Plan 06 进度 0/10 → 10/10（163 tests, 72+ classes, T1-T10 全部完成）；③阶段 B → ✅ 已完成，阶段 C → ✅ 已完成；④优先级排序更新（仅剩 Plan 09 infra-deployment） |
