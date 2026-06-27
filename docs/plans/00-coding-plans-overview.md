# Agent 平台编码计划总览

> 文档版本：v1.0 | 更新日期：2026-06-27 | 基于 15 份设计文档（11 主设计 + 1 补遗 + 3 详细流程图）
> 方法论：[writing-plans](../../project_memory.md) | TDD 红绿循环 | 无占位符 | bite-sized tasks

## 0. 总览

本平台共 11 个核心微服务 + 2 个横向服务，按依赖关系拆分为 **10 个独立编码计划**。每个计划可独立交付可测试的软件单元，遵循 TDD 红绿循环。

### 已完成编码计划（2 份，共 7124 行，18 Task，90 测试用例）

| # | 文档 | Task 数 | 行数 | 覆盖模块 | 状态 |
|---|---|---|---|---|---|
| 01 | [01-agent-proto-and-common-plan.md](./01-agent-proto-and-common-plan.md) | 8 | 2785 | agent-proto（8 个 .proto）+ agent-common（11 个 Java 类） | ✅ 已完成 |
| 02 | [02-agent-gateway-session-plan.md](./02-agent-gateway-session-plan.md) | 10 | 4339 | agent-gateway(8080) + agent-session(8082) | ✅ 已完成 |

### 待生成编码计划（8 份，任务大纲）

| # | 计划名称 | 模块 | 依赖 | 优先级 | Task 大纲 |
|---|---|---|---|---|---|
| 03 | DDL 脚本编写 plan | `infra/sql/` | 无（独立） | P0（最先执行） | T1 创建 agent_session 库 / T2 agent_task 库 / T3 agent_memory 库 / T4 agent_tool 库 / T5 agent_model 库 / T6 agent_repo 库 / T7 agent_knowledge 库 / T8 agent_quality 库 / T9 agent_risk 库 / T10 ClickHouse agent_metrics_daily / T11 Milvus Collections 初始化 / T12 Neo4j 约束与索引 / T13 Redis 初始化脚本 / T14 种子数据（model_provider / model_route_rule / task_template） |
| 04 | task-orchestrator + planning 编码 plan | `agent-task-orchestrator`(8084) + `agent-planning`(8086) | agent-proto / agent-common | P1 | T1 项目骨架 / T2 TaskInstance 实体与 Repository / T3 DAG 引擎核心（DagNode/DagEdge/拓扑排序/环检测）/ T4 状态机实现（10 状态转换）/ T5 TaskOrchestrator gRPC 服务实现（SubmitTask/GetTaskStatus/CancelTask/ReportSubtaskResult）/ T6 复杂度识别（6 维度评分）/ T7 PlanningService gRPC 服务（Plan/ValidatePlan/Replan）/ T8 模板匹配 / T9 5 维度 DAG 校验 / T10 并行批次调度 / T11 RocketMQ 集成（task.subtask.execute/done/state.change）/ T12 动态重规划（增量/全量）/ T13 集成测试 |
| 05 | memory 编码 plan | `agent-memory`(8088) | agent-proto / agent-common / Milvus / MySQL / Redis | P1 | T1 项目骨架 / T2 MemoryRecord 实体 / T3 MemoryRepository MySQL + Milvus 双写 / T4 WriteLongTerm gRPC 服务 / T5 Recall 多路召回（向量/关键词/时间/标签）/ T6 融合重排（4 权重 0.4/0.3/0.2/0.1）/ T7 重要性评分（4 维度）/ T8 语义去重（0.85/0.95 阈值）/ T9 蒸馏调度（XXL-Job）/ T10 分域隔离 / T11 Top-N 动态调整 / T12 集成测试 |
| 06 | tool-engine 编码 plan | `agent-tool-engine`(8090) | agent-proto / agent-common | P1 | T1 项目骨架 / T2 ToolRegistry 实体 / T3 工具注册（RegisterTool）/ T4 Top-K 召回（向量+标签）/ T5 ToolGateway.Invoke 前置校验链（5 步）/ T6 RBAC+ABAC 权限校验 / T7 配额校验（tool_quota）/ T8 R1/R2/R3 分级执行 / T9 Docker sandbox 沙箱执行 / T10 超时重试 + 替代工具 / T11 结果标准化清洗 / T12 成本熔断 / T13 集成测试 |
| 07 | agent-runtime 编码 plan | `agent-runtime`(8092) | agent-proto / agent-common / Redis | P2 | T1 项目骨架 / T2 AgentState Redis 状态管理（ADR-002）/ T3 ReAct 循环引擎（Think→Act→Observe→Reflect）/ T4 Think 自检三问 / T5 Act 三分支决策（生成/工具/转交）/ T6 Reflexion 反思（none/single/multi）/ T7 Token 水位预计算 / T8 四级水位压缩 / T9 6 类内容优先级压缩 / T10 断点续跑 / T11 max_steps 熔断 / T12 人工介入 / T13 AgentRuntime gRPC 服务（StartAgent/Step/GetState/Pause/Resume）/ T14 集成测试 |
| 08 | model-gateway 编码 plan | `agent-model-gateway`(8094) | agent-proto / agent-common | P1 | T1 项目骨架 / T2 model_provider 表与路由 / T3 model_route_rule 路由匹配 / T4 OpenAI 协议适配器（ADR-003）/ T5 Anthropic 适配器 / T6 Gemini 适配器 / T7 国内模型适配器（通义/文心/DeepSeek）/ T8 Chat gRPC 服务 / T9 StreamChat server streaming / T10 CountTokens（含中文 1.7 倍系数）/ T11 Prompt 缓存 / T12 model_usage_log 计量 / T13 故障自动降级 / T14 集成测试 |
| 09 | agent-repo + knowledge + quality 编码 plan | `agent-repo`(8096) + `agent-knowledge`(8098) + `agent-quality`(8100) | agent-proto / agent-common / Milvus / ES | P2 | T1 agent-repo 骨架 / T2 AgentDefinition 实体 / T3 Agent 生命周期管理 / T4 能力标签体系 / T5 效果动态评分 / T6 agent_version 版本管理 / T7 knowledge-service 骨架 / T8 文档解析切片 / T9 KnowledgeService gRPC（Ingest/Retrieve）/ T10 知识版本与权限 / T11 quality-service 骨架 / T12 L4-1 规则化硬校验 / T13 L4-2 事实一致性校验 / T14 L4-3 综合质量终审 / T15 Badcase 归集 / T16 集成测试 |
| 10 | 基础设施配置 plan | `infra/k8s/` + `infra/docker/` + `infra/nacos/` | 全部服务 | P3 | T1 Dockerfile 基础镜像 / T2 各服务 Dockerfile / T3 docker-compose 本地开发环境 / T4 K8s Namespace / T5 K8s ConfigMap + Secret / T6 K8s Deployment（11 服务）/ T7 K8s Service + Ingress / T8 K8s HPA 自动扩缩容 / T9 Nacos 配置中心 / T10 Nacos 服务注册 / T11 SkyWalking Agent 配置 / T12 Prometheus + Grafana 监控 / T13 Loki 日志聚合 / T14 部署脚本（PowerShell） |

## 1. 依赖关系图

```
┌──────────────────────────────────────────────────────────────────────┐
│  P0 基础层（无依赖，最先执行）                                          │
│  ┌──────────────────────────┐    ┌─────────────────────────────────┐  │
│  │ Plan 03: infra/sql DDL   │    │ Plan 01: agent-proto+common     │  │
│  │ （13 个 Task，独立可执行）│    │ （8 Task，8 .proto + 11 Java 类）│  │
│  └────────────┬─────────────┘    └────────────┬────────────────────┘  │
└───────────────┼─────────────────────────────────┼─────────────────────┘
                │                                 │
                ▼                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  P1 核心引擎层（依赖 P0）                                              │
│  ┌──────────────────┐  ┌──────────────┐  ┌──────────────┐            │
│  │ Plan 04:          │  │ Plan 05:     │  │ Plan 06:     │            │
│  │ task-orchestrator │  │ memory       │  │ tool-engine  │            │
│  │ + planning        │  │ (13 Task)    │  │ (13 Task)    │            │
│  │ (13 Task)         │  └──────┬───────┘  └──────┬───────┘            │
│  └────────┬─────────┘         │                  │                    │
│           │                   │                  │                    │
│  ┌────────▼───────────────────▼──────────────────▼─────┐               │
│  │ Plan 08: model-gateway (14 Task)                     │               │
│  │ （被 task/memory/tool/runtime 依赖）                  │               │
│  └────────┬─────────────────────────────────────────────┘               │
└───────────┼────────────────────────────────────────────────────────────┘
            │
            ▼
┌──────────────────────────────────────────────────────────────────────┐
│  P2 运行时与能力层（依赖 P1）                                          │
│  ┌──────────────────────┐  ┌──────────────────────────────────────┐    │
│  │ Plan 07: agent-runtime│  │ Plan 09: agent-repo + knowledge     │    │
│  │ (14 Task, ReAct+Token)│  │ + quality (16 Task)                 │    │
│  └──────────┬───────────┘  └────────────────┬─────────────────────┘    │
└─────────────┼─────────────────────────────────┼────────────────────────┘
              │                                 │
              ▼                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  P2 接入层（依赖 P0-P2，可独立测试）                                   │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ Plan 02: agent-gateway + agent-session (10 Task, 已完成)        │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
              │
              ▼
┌──────────────────────────────────────────────────────────────────────┐
│  P3 基础设施（依赖全部服务）                                          │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ Plan 10: infra/k8s + docker + nacos (14 Task)                  │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

## 2. 建议执行顺序

### 阶段 A：基础层（P0）—— 并行执行
- **Plan 03（DDL）** + **Plan 01（proto+common）** 并行，无相互依赖
- 两者完成后所有后续服务可依赖

### 阶段 B：核心引擎层（P1）—— 串行或并行
- **Plan 04（task-orchestrator+planning）** / **Plan 05（memory）** / **Plan 06（tool-engine）** 三者可并行（都只依赖 P0）
- **Plan 08（model-gateway）** 依赖 P0，可与上面三者并行

### 阶段 C：运行时与能力层（P2）—— 依赖阶段 B
- **Plan 07（agent-runtime）** 依赖 task/memory/tool/model 全部完成
- **Plan 09（agent-repo+knowledge+quality）** 依赖 P0-P1

### 阶段 D：接入层（已完成）
- **Plan 02（gateway+session）** 已完成，待 P1-P2 完成后联调

### 阶段 E：基础设施（P3）—— 最后执行
- **Plan 10（K8s/Docker/Nacos）** 依赖全部服务可启动

## 3. 关键约定（所有 plan 必须遵循）

### 3.1 技术栈版本
- Java 17 / Spring Boot 3.2 / Spring Cloud Alibaba 2023.0.1.0
- gRPC 1.62.2 / protobuf-maven-plugin 0.6.1
- MySQL 8.0.36 / Milvus 2.4 / Redis 7.2 / RocketMQ 5.3 / Neo4j 5.18 / ES 8.13
- JUnit 5 / Mockito 5 / Testcontainers 1.19

### 3.2 Monorepo 目录结构
```
e:\git\Agent-Platform-Prototype\
├── agent-proto/          # Protobuf 契约层（Plan 01）
├── agent-common/         # 公共工具层（Plan 01）
├── agent-gateway/        # 接入网关（Plan 02，端口 8080）
├── agent-session/        # 会话管理（Plan 02，端口 8082）
├── agent-task-orchestrator/  # 任务编排（Plan 04，端口 8084）
├── agent-planning/       # 智能规划（Plan 04，端口 8086）
├── agent-memory/         # 记忆管理（Plan 05，端口 8088）
├── agent-tool-engine/    # 工具引擎（Plan 06，端口 8090）
├── agent-runtime/        # Agent 运行时（Plan 07，端口 8092）
├── agent-model-gateway/   # 模型网关（Plan 08，端口 8094）
├── agent-repo/           # Agent 仓库（Plan 09，端口 8096）
├── agent-knowledge/       # 知识服务（Plan 09，端口 8098）
├── agent-quality/        # 质量评估（Plan 09，端口 8100）
├── infra/
│   ├── sql/              # DDL 脚本（Plan 03）
│   ├── k8s/              # K8s 部署（Plan 10）
│   ├── docker/           # Docker 配置（Plan 10）
│   └── nacos/            # Nacos 配置（Plan 10）
└── docs/                 # 设计文档与编码计划
    ├── plans/            # 本目录
    ├── 00-overview/ ~ 11-detail-flow/  # 设计文档
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

## 4. 已完成 Plan 的执行建议

### Plan 01（agent-proto + agent-common）
- **执行方式**：推荐 Subagent-Driven（每个 Task 派发独立子代理）
- **关键步骤**：先 `mvn install` agent-proto，再编译 agent-common
- **验收标准**：8 个 .proto 文件全部编译通过 + 11 个 Java 类 47 个测试用例全绿

### Plan 02（agent-gateway + agent-session）
- **执行方式**：推荐 Subagent-Driven（Task 7 依赖 Task 6 实体；Task 9 依赖 Task 8 记忆服务）
- **关键依赖**：需先执行 Plan 01 完成 agent-proto + agent-common
- **验收标准**：10 个 Task 43 个 Java 类全绿 + EndToEndTest 通过
- **Testcontainers**：集成测试需 Docker 环境，Task 7/8/10 涉及 MySQL/Redis Testcontainers

## 5. 后续 plan 生成优先级

| 优先级 | Plan | 理由 |
|---|---|---|
| 高 | Plan 03（DDL） | 阶段 3 将实际执行，先生成 plan |
| 高 | Plan 04（task-orchestrator+planning） | 核心引擎，被多个服务依赖 |
| 中 | Plan 08（model-gateway） | 被所有 LLM 调用依赖 |
| 中 | Plan 05（memory） | 被 agent-runtime 依赖 |
| 中 | Plan 06（tool-engine） | 被 agent-runtime 依赖 |
| 低 | Plan 07（agent-runtime） | 依赖 task/memory/tool/model 全部完成 |
| 低 | Plan 09（repo+knowledge+quality） | 能力层，可后置 |
| 低 | Plan 10（基础设施） | 最后部署阶段 |
