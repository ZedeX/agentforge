# agent-runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `agent-runtime`（端口 8092 / gRPC 9092）模块补齐运行时全栈能力：AgentRuntime gRPC 服务（5 RPC：StartAgent / Step / GetState / Pause / Resume），覆盖 ReActLoop 循环（Think→Act→Observe）、ModelGatewayClient 模型客户端、ToolEngineClient 工具客户端、ReflexionEngine 反思、StepStateSyncer 步骤同步、TokenWatermarkMonitor 水印监控六项核心能力。已有 16 个骨架文件（api 接口 + enums + exception + model POJO），本计划 T1~T10 落地全部业务逻辑、JPA Entity、ReAct 循环编排、熔断重试与端到端集成测试，对齐 v5 审核 §6 P6-6 整改项与 doc 06-runtime 设计。

> **proto 契约对齐说明（2026-07-05 修订）：** 实际 `agent_runtime.proto` 定义 5 RPC（StartAgent / Step / GetState / Pause / Resume），而非早期 Plan 草案描述的 4 RPC（StartAgent / GetStepState / CancelAgent / ReportStepResult）。本计划统一以 proto 为准实现，文档已同步更新。

**Architecture:** 单 Spring Boot 应用 `agent-runtime`，对外暴露 gRPC 服务 AgentRuntime（端口 9092）+ Actuator（8092）。内部以 `step_state` 表（MySQL 逻辑库 `agent_runtime`）记录每步 ReAct 状态，ReActLoop 是核心循环（Think 调 ModelGateway / Act 调 ToolEngine / Observe 反思），TokenWatermarkMonitor 监控 token 消耗防止超限。上游接收 agent-task-orchestrator 的 StartAgent 指令，下游对接 agent-model-gateway（LLM 推理）+ agent-tool-engine（工具调用）+ agent-memory（记忆召回）。依赖 agent-proto（Protobuf 契约 `agent_runtime.proto` / `common.proto`）与 agent-common（ReActPhaseType / TokenLevel / ErrorCode / BusinessException）。

**Tech Stack:** Java 17 / Spring Boot 3.2.5 / grpc-spring-boot-starter 3.1.0.RELEASE（net.devh）/ spring-boot-starter-data-jpa / MySQL Connector 8 / Resilience4j 2.2.0（熔断 + 重试）/ Caffeine 3.1.8（本地缓存）/ Jackson / Lombok / JUnit 5 / Mockito 5 / AssertJ 3.25.3 / Awaitility 4.2.0 / H2（MySQL 模式，测试备选）/ Testcontainers 1.19.7（可选集成路径）/ WireMock 3.x（model-gateway / tool-engine mock）

---

## 设计文档对齐

| 项 | 来源 | 锁定值 |
|---|---|---|
| agent-runtime HTTP / gRPC 端口 | doc 00-overview §3.1 | 8092（HTTP） / 9092（gRPC） |
| 逻辑库 | doc 01-database §0.4 / §2.3 | `agent_runtime`（step_state / agent_session / token_usage_log） |
| AgentRuntime gRPC 5 RPC | `agent-proto/src/main/proto/agent_runtime.proto` | StartAgent / Step / GetState / Pause / Resume |
| proto 生成包名 | agent_runtime.proto / common.proto | `agentplatform.agent_runtime.v1` / `agentplatform.common.v1` |
| ReAct 三阶段 | doc 06-runtime §3.1 + ReActPhaseType 枚举 | THINK（思考）/ ACT（行动）/ OBSERVE（观察） |
| 反思结果 | doc 06-runtime §3.2 + ReflexionResult 枚举 | CONTINUE（继续）/ RETRY（重试当前步）/ REPLAN（请求重规划）/ ABORT（终止） |
| Token 水位等级 | doc 06-runtime §3.3 + TokenLevel 枚举 | GREEN（<60%）/ YELLOW（60-80%）/ RED（>80%）/ EXCEEDED（超限） |
| ReAct 最大步数 | doc 06-runtime §4.1 | maxSteps=20（可配），超限触发 ABORT |
| Token 预算 | doc 06-runtime §4.2 | 默认 32K tokens / session，含 prompt + completion |
| 反思触发条件 | doc 06-runtime §5.1 | 每 3 步触发一次 Reflexion / ACT 失败后必触发 / token YELLOW 必触发 |
| 熔断阈值 | doc 06-runtime §6.1 | ModelGateway 连续 5 次失败 → 熔断 30s；ToolEngine 连续 3 次失败 → 熔断 30s |
| 重试策略 | doc 06-runtime §6.2 | 瞬时错误：3 次指数退避（200/600/1800 ms）；业务错误：不重试直接 REPLAN；致命错误：ABORT |
| 步骤状态 | doc 06-runtime §7.1 | step_state：step_id / session_id / tenant_id / agent_id / task_id / phase / status / think_content / act_tool_id / act_params / observe_content / tokens_used / reflexion_result / started_at / ended_at / duration_ms / error_message |
| 步骤状态 6 态 | doc 06-runtime §7.2 | PENDING / RUNNING / SUCCESS / FAILED / RETRYING / CANCELLED |
| ModelGateway 调用 | doc 06-runtime §8.1 | gRPC ChatCompletion（agent-model-gateway:8080），支持 stream + non-stream |
| ToolEngine 调用 | doc 06-runtime §8.2 | gRPC CallTool（agent-tool-engine:9090），含审批回调 |
| Memory 调用 | doc 06-runtime §8.3 | gRPC RecallMemory（agent-memory:9088），Think 前注入历史经验 |
| StartAgent 流程 | doc 06-runtime §9.1 | 创建 session（agent_instance_id）→ 初始化 StepState → 返回 STARTED |
| Step 流程 | proto §14 | 单步执行：Think → Act → Observe → Reflexion → 返回 StepResponse（含 phase / status / token_used / finished） |
| GetState 流程 | proto §67 | 返回 AgentState 快照（current_step / token_used / cost_used / status） |
| Pause 流程 | proto §85 | 暂停当前会话：标记 PAUSED → 中断当前 step → 返回 paused_at |
| Resume 流程 | proto §97 | 恢复会话：标记 RUNNING → 重新启动 ReActLoop → 返回 resumed_at |
| 错误码域 | doc 06-runtime §12.4 | SESSION_NOT_FOUND(404) / SESSION_ALREADY_FINISHED(409) / MAX_STEPS_EXCEEDED(429) / TOKEN_BUDGET_EXCEEDED(429) / CIRCUIT_OPEN(503) / MAX_RETRY_EXCEEDED(503) / REACT_LOOP_INTERRUPTED(500) / MODEL_GATEWAY_UNAVAILABLE(503) / TOOL_ENGINE_UNAVAILABLE(503) |
| gRPC 服务类签名 | doc 06-runtime §11.1 | `@GrpcService extends AgentRuntimeGrpc.AgentRuntimeImplBase` |
| 配置参数 | doc 06-runtime §13 | `runtime.react.maxSteps=20` / `runtime.react.tokenBudget=32000` / `runtime.react.tokenYellowThreshold=0.6` / `runtime.react.tokenRedThreshold=0.8` / `runtime.reflexion.interval=3` / `runtime.circuit.model.failureThreshold=5` / `runtime.circuit.tool.failureThreshold=3` / `runtime.circuit.openDurationMs=30000` / `runtime.retry.maxAttempts=3` / `runtime.retry.initialBackoffMs=200` / `runtime.retry.multiplier=3.0` |
| 异常分级与重试 | doc 06-runtime §12.3 | 瞬时（3 次指数退避）/ 业务（REPLAN）/ 质量（单步重跑 Reflexion）/ 致命（ABORT） |
| ADR-004 | docs/adr/ADR-004-react-vs-planexec.md | 选 ReAct 不选 Plan-and-Execute：步骤动态可调，更适合长链路 |
| 测试用例 | `docs/tests/unit-test-cases.md` §9 | UT-RT-001~015 |
| v5 审核整改项 | `docs/tests/tdd-audit-report-v5.md` §6 P6-6 | 实现 agent-runtime T1-T10 D2 +1.0 |

---

## 文件结构总览

### 已完成骨架文件（16 个，仅接口/枚举/异常/POJO，无业务实现）

| 文件 | 当前状态 | 职责 |
|---|---|---|
| `agent-runtime/pom.xml` | 占位 | Maven 配置（需补全依赖） |
| `agent-runtime/src/main/resources/application.yml` | 占位 | 端口 + 数据源（需补全） |
| `agent-runtime/src/main/java/com/agent/runtime/RuntimeApplication.java` | 占位 | Spring Boot 启动类（需补全） |
| `.../api/ReActLoop.java` | 接口 | Think→Act→Observe 循环 |
| `.../api/ModelGatewayClient.java` | 接口 | agent-model-gateway gRPC 客户端 |
| `.../api/ToolEngineClient.java` | 接口 | agent-tool-engine gRPC 客户端 |
| `.../api/ReflexionEngine.java` | 接口 | 反思引擎（决定 CONTINUE/RETRY/REPLAN/ABORT） |
| `.../api/StepStateSyncer.java` | 接口 | 步骤状态同步 + 落库 |
| `.../api/TokenWatermarkMonitor.java` | 接口 | token 水位监控（GREEN/YELLOW/RED/EXCEEDED） |
| `.../enums/ReActPhaseType.java` | 枚举 | THINK / ACT / OBSERVE |
| `.../enums/ReflexionResult.java` | 枚举 | CONTINUE / RETRY / REPLAN / ABORT |
| `.../enums/TokenLevel.java` | 枚举 | GREEN / YELLOW / RED / EXCEEDED |
| `.../exception/CircuitOpenException.java` | 子类 | 熔断器开启 |
| `.../exception/MaxRetryExceededException.java` | 子类 | 重试上限耗尽 |
| `.../model/ReActContext.java` | POJO | ReAct 循环上下文（session/agent/task/step/state） |
| `.../model/ReflectionFeedback.java` | POJO | 反思反馈（result / reason / suggestions） |
| `.../model/RetryContext.java` | POJO | 重试上下文（attempt / maxAttempts / lastError / backoffMs） |
| `.../model/StepState.java` | POJO | 步骤状态（需升级为 JPA @Entity） |
| `.../model/TokenWatermark.java` | POJO | token 水位（used / budget / level / remaining） |

### 待新增文件（T1~T10）

| 文件 | Task | 职责 |
|---|---|---|
| `.../config/RuntimeProperties.java` | T1 | `@ConfigurationProperties("runtime")` |
| `.../config/ModelGatewayClientConfig.java` | T1 | gRPC stub（agent-model-gateway） |
| `.../config/ToolEngineClientConfig.java` | T1 | gRPC stub（agent-tool-engine） |
| `.../config/MemoryClientConfig.java` | T1 | gRPC stub（agent-memory） |
| `.../config/Resilience4jConfig.java` | T1 | 熔断器 + 重试器 bean |
| `.../repository/StepStateRepository.java` | T2 | JPA Repository |
| `.../repository/AgentSessionRepository.java` | T2 | JPA Repository |
| `.../repository/TokenUsageLogRepository.java` | T2 | JPA Repository |
| `.../model/StepState.java`（升级） | T2 | @Entity |
| `.../model/AgentSession.java` | T2 | 会话 @Entity |
| `.../model/TokenUsageLog.java` | T2 | token 用量日志 @Entity |
| `.../loop/ReActLoopImpl.java` | T3 | 实现 ReActLoop |
| `.../loop/ThinkPhase.java` | T3 | Think 阶段处理器 |
| `.../loop/ActPhase.java` | T3 | Act 阶段处理器 |
| `.../loop/ObservePhase.java` | T3 | Observe 阶段处理器 |
| `.../loop/ReActPromptBuilder.java` | T3 | 构造 ReAct prompt（含历史步骤 + 召回记忆） |
| `.../modelgateway/ModelGatewayClientImpl.java` | T4 | 实现 ModelGatewayClient |
| `.../modelgateway/ChatCompletionMapper.java` | T4 | proto ↔ DTO 映射 |
| `.../toolengine/ToolEngineClientImpl.java` | T5 | 实现 ToolEngineClient |
| `.../toolengine/ToolCallMapper.java` | T5 | proto ↔ DTO 映射 |
| `.../reflexion/ReflexionEngineImpl.java` | T6 | 实现 ReflexionEngine |
| `.../reflexion/ReflexionPromptBuilder.java` | T6 | 构造反思 prompt |
| `.../sync/StepStateSyncerImpl.java` | T7 | 实现 StepStateSyncer |
| `.../sync/SessionManager.java` | T7 | 会话生命周期管理 |
| `.../watermark/TokenWatermarkMonitorImpl.java` | T8 | 实现 TokenWatermarkMonitor |
| `.../watermark/TokenBudgetCalculator.java` | T8 | token 预算计算（tiktoken 估算） |
| `.../circuit/CircuitBreakerRegistry.java` | T9 | Resilience4j 熔断器封装 |
| `.../retry/RetryExecutor.java` | T9 | Resilience4j 重试封装 |
| `.../grpc/AgentRuntimeGrpcImpl.java` | T10 | AgentRuntime gRPC 5 RPC 实现（StartAgent / Step / GetState / Pause / Resume） |
| `.../grpc/StepStateMapper.java` | T10 | proto ↔ JPA 映射 |
| `.../grpc/GrpcExceptionAdvice.java` | T10 | gRPC Status 异常翻译 |

---

## Tasks

### Task T1: 骨架补全（pom + application.yml + 启动类）

**目标：** 把 16 个骨架文件外的 Maven / 配置 / 启动类补齐到可启动状态。

**Red：**
- [ ] 在 `RuntimeApplicationTest.java` 写 `contextLoads()` 测试，断言 Spring Context 加载成功。
- [ ] 在 `application-test.yml` 配置 H2 MySQL 模式 + 关闭 gRPC stub 实际连接（指向 in-process）。
- [ ] 运行测试，预期失败（pom 缺依赖 / yml 空）。

**Green：**
- [ ] 补全 `pom.xml`：parent / web / data-jpa / validation / mysql-connector / grpc-starter / resilience4j / caffeine / jackson / lombok / test 全套。
- [ ] 补全 `application.yml`：`server.port=8092` / `grpc.server.port=9092` / `grpc.client.GLOBAL.negotiationType=PLAINTEXT` / MySQL agent_runtime / `grpc.client.model-gateway.host=agent-model-gateway:8080` / `grpc.client.tool-engine.host=agent-tool-engine:9090` / `grpc.client.memory.host=agent-memory:9088` / `runtime.*` 全部配置项（对齐 §13）。
- [ ] 补全 `RuntimeApplication.java`：`@SpringBootApplication(scanBasePackages="com.agent.runtime")` + `@EnableJpaRepositories` + `@EnableScheduling` + `@EnableAsync`。
- [ ] 新建 `RuntimeProperties.java`：`@ConfigurationProperties(prefix="runtime")`，字段对齐 §13，内部静态类 `React` / `Reflexion` / `Circuit` / `Retry`。
- [ ] 新建 `ModelGatewayClientConfig.java`：gRPC stub bean（`ModelGatewayBlockingStub` / `ModelGatewayFutureStub`），指向 agent-model-gateway:8080。
- [ ] 新建 `ToolEngineClientConfig.java`：gRPC stub bean（`ToolEngineBlockingStub` / `ToolEngineFutureStub`），指向 agent-tool-engine:9090。
- [ ] 新建 `MemoryClientConfig.java`：gRPC stub bean（`MemoryServiceBlockingStub`），指向 agent-memory:9088。
- [ ] 新建 `Resilience4jConfig.java`：CircuitBreakerRegistry + RetryRegistry bean（配置来自 RuntimeProperties.Circuit / RuntimeProperties.Retry）。
- [ ] 运行 `contextLoads()`，预期通过。

**Refactor：**
- [ ] 拆分 `application.yml` 为 `application.yml` + `application-dev.yml` + `application-test.yml`。
- [ ] `RuntimeProperties.React` 字段命名清晰（`maxSteps` / `tokenBudget` / `tokenYellowThreshold` / `tokenRedThreshold`）。

**Commit：** `feat(agent-runtime): T1 scaffold pom/yml/startup, context loads`

---

### Task T2: StepState / AgentSession / TokenUsageLog JPA Entity + Repository

**目标：** 把 StepState POJO 升级为 JPA @Entity，新建 AgentSession + TokenUsageLog 实体与 Repository。

**Red：**
- [ ] 在 `StepStateRepositoryTest.java` / `AgentSessionRepositoryTest.java` / `TokenUsageLogRepositoryTest.java`（@DataJpaTest + H2 MySQL 模式）写：
  - `StepStateRepositoryTest.saveAndFindByStepId` 保存 StepState 后按 stepId 查询，断言字段完整。
  - `findBySessionIdOrderByStepNumber` 按会话查询并按步骤号排序。
  - `findBySessionIdAndStatus` 按会话 + 状态查询。
  - `findLatestBySessionId` 取会话最新一步。
  - `AgentSessionRepositoryTest.findBySessionId` 按会话 ID 查询。
  - `findByTaskIdAndStatus` 按任务 + 状态查询。
  - `updateStatusById` 单条更新状态。
  - `TokenUsageLogRepositoryTest.findBySessionId` 按会话查询用量。
  - `sumTokensBySessionId` 聚合查询。
  - 测试预期失败（实体未加 @Entity）。

**Green：**
- [ ] 升级 `StepState.java` 为 @Entity，字段对齐 doc 01-database §2.3：
  - `id`（Long，@Id @GeneratedValue）
  - `stepId`（String，UUID，唯一索引）
  - `sessionId`（String，索引）
  - `tenantId`（String）
  - `agentId`（String）
  - `taskId`（String）
  - `stepNumber`（Integer）
  - `phase`（@Enumerated STRING，ReActPhaseType）
  - `status`（@Enumerated STRING，StepStatus：PENDING/RUNNING/SUCCESS/FAILED/RETRYING/CANCELLED）
  - `thinkContent`（@Lob，TEXT）
  - `actToolId`（String）
  - `actParams`（@Lob，JSON）
  - `observeContent`（@Lob，TEXT）
  - `tokensUsed`（Integer）
  - `reflexionResult`（@Enumerated STRING，ReflexionResult）
  - `startedAt` / `endedAt`（Instant）
  - `durationMs`（Long）
  - `errorMessage`（String）
  - `createdAt` / `updatedAt`（Instant，@Version）
- [ ] 新建 `AgentSession.java` @Entity：
  - `id` / `sessionId`（唯一索引）/ `tenantId` / `agentId` / `taskId` / `status`（CREATING / RUNNING / CANCELLING / SUCCESS / FAILED / CANCELLED）/ `currentStepNumber` / `totalTokensUsed` / `tokenBudget` / `maxSteps` / `startedAt` / `endedAt` / `errorMessage`。
- [ ] 新建 `TokenUsageLog.java` @Entity：
  - `id` / `sessionId` / `stepId` / `phase` / `promptTokens` / `completionTokens` / `totalTokens` / `model` / `loggedAt`。
- [ ] 新建 3 个 Repository 接口 + 各 4~6 个查询方法。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 抽 `BaseEntity`（id / createdAt / updatedAt / @Version）。
- [ ] 显式 `@Column(name="snake_case")`。

**Commit：** `feat(agent-runtime): T2 StepState + AgentSession + TokenUsageLog JPA entities`

---

### Task T3: ReActLoopImpl Think→Act→Observe 循环

**目标：** 实现 `ReActLoop` 接口：核心 ReAct 循环编排（Think→Act→Observe→Reflexion→next step）。

**Red：**
- [ ] 在 `ReActLoopImplTest.java` 写（@MockBean 各依赖）：
  - `run_completesInSingleStep_whenThinkResolvesDirectly` Think 阶段模型直接给出最终答案（无 tool call）→ 1 步成功结束。
  - `run_executesFullCycle_thinkActObserve` Think 决定调 tool → Act 调用 → Observe 总结 → 下一步 Think。
  - `run_respectsMaxSteps_abortsWhenExceeded` maxSteps=3，模拟 4 步 → 第 4 步触发 ABORT。
  - `run_triggersReflexionEvery3Steps` 每 3 步触发 Reflexion（mock 返回 CONTINUE）。
  - `run_triggersReflexionOnActFailure` ACT 失败后必触发 Reflexion（mock 返回 RETRY）。
  - `run_retriesOnRetryReflexion` Reflexion 返回 RETRY → 重跑当前 step（重试次数 +1）。
  - `run_replansOnReplanReflexion` Reflexion 返回 REPLAN → 通知 orchestrator（mock）+ ABORT 当前循环。
  - `run_abortsOnAbortReflexion` Reflexion 返回 ABORT → 立即终止。
  - `run_cancelsOnCancelSignal` 收到 cancel 信号 → 中断当前 step + 状态 CANCELLED。
  - `run_injectsRecalledMemoryBeforeThink` Think 前调用 Memory.RecallMemory 注入历史经验。
  - `run_recordsStepStateAfterEachPhase` 每个阶段后调 StepStateSyncer 落库。
  - `run_updatesTokenWatermarkAfterModelCall` 每次模型调用后更新 token 水位。

**Green：**
- [ ] 新建 `ThinkPhase.java`：构造 prompt（system + history steps + recalled memory + user query）→ 调 ModelGatewayClient.chat() → 解析返回（含 tool_call_decision 或 final_answer）。
- [ ] 新建 `ActPhase.java`：从 Think 输出提取 tool_id + params → 调 ToolEngineClient.callTool() → 返回 ToolCallResult。
- [ ] 新建 `ObservePhase.java`：把 ToolCallResult 总结为 observation 文本 → 注入下轮 prompt。
- [ ] 新建 `ReActPromptBuilder.java`：构造 ReAct prompt（system message 描述 Think/Act/Observe 协议 + history + memory + query）。
- [ ] 实现 `ReActLoopImpl.java`：
  - `run(ReActContext ctx)`：
    1. 初始化 StepState（stepNumber=0 / status=RUNNING）。
    2. 循环 while not finished and stepNumber < maxSteps and not cancelled：
       a. **Think 阶段**：
          - 调 MemoryClient.recallMemory(query) → 注入历史经验。
          - 调 ReActPromptBuilder.build(ctx, history, memory)。
          - 调 ModelGatewayClient.chat(prompt) → thinkContent + toolCallDecision。
          - 更新 token 水位。
          - 落库 StepState（phase=THINK / status=SUCCESS）。
          - 若 thinkContent 是 final_answer → 标记 finished。
       b. **Act 阶段**（若 Think 决定调 tool）：
          - 调 ToolEngineClient.callTool(toolId, params) → ToolCallResult。
          - 落库 StepState（phase=ACT）。
          - 若 Act 失败 → 触发 Reflexion。
       c. **Observe 阶段**：
          - 总结 ToolCallResult → observation。
          - 落库 StepState（phase=OBSERVE）。
       d. **Reflexion 阶段**（每 interval 步 或 Act 失败）：
          - 调 ReflexionEngine.reflect(ctx, history) → ReflexionResult。
          - CONTINUE → 下一步。
          - RETRY → 重跑当前步（retryCount +1，若超限 → ABORT）。
          - REPLAN → 通知 orchestrator + ABORT。
          - ABORT → 立即终止。
       e. stepNumber++。
    3. 超出 maxSteps → 标记 ABORT。
    4. 落库最终 StepState。
    5. 返回 ReActResult（finalAnswer / stepCount / totalTokens / status）。
- [ ] 注入 ModelGatewayClient / ToolEngineClient / MemoryClient / ReflexionEngine / StepStateSyncer / TokenWatermarkMonitor / RuntimeProperties。
- [ ] 异步执行：`@Async` + CompletableFuture，主线程返回 sessionId。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 把 `run()` 拆 `executeThink` / `executeAct` / `executeObserve` / `executeReflexion` / `handleReflexionResult` 5 个私有方法。
- [ ] 加 Micrometer timer：`runtime.react.step.duration{phase, status}`。

**Commit：** `feat(agent-runtime): T3 ReActLoopImpl think-act-observe cycle with reflexion`

---

### Task T4: ModelGatewayClientImpl 模型客户端

**目标：** 实现 `ModelGatewayClient` 接口：调用 agent-model-gateway 的 ChatCompletion RPC。

**Red：**
- [ ] 在 `ModelGatewayClientImplTest.java` 写（gRPC in-process mock）：
  - `chat_returnsCompletion` 输入 prompt → 返回模型回复文本。
  - `chat_stream_returnsChunks` stream 模式 → 聚合多个 chunk 返回完整文本。
  - `chat_includesSystemAndUserMessages` 验证请求体包含 system + user 消息。
  - `chat_passesTemperatureAndMaxTokens` 验证 temperature / maxTokens 参数传递。
  - `chat_throwsOnUnavailable` gRPC UNAVAILABLE → 抛 `ModelGatewayUnavailableException`。
  - `chat_throwsOnDeadlineExceeded` gRPC DEADLINE_EXCEEDED → 抛 `ModelGatewayTimeoutException`。
  - `chat_returnsToolCallDecision` 模型返回 tool_call → 解析为 ToolCallDecision（toolId / params / reasoning）。
  - `chat_returnsFinalAnswer` 模型返回 final answer → 解析为 FinalAnswer（content / finished=true）。
  - `chat_recordsTokenUsage` 返回 token usage（prompt / completion / total）。

**Green：**
- [ ] 新建 `ChatCompletionMapper.java`：proto ChatCompletionRequest ↔ DTO 双向映射 + 响应解析。
- [ ] 实现 `ModelGatewayClientImpl.java`：
  - `chat(ChatRequest request)`：
    1. 构造 gRPC ChatCompletionRequest（model / messages / temperature / maxTokens / stream）。
    2. 非流式：`stub.chatCompletion(request)` → 解析 response。
    3. 流式：`stub.chatCompletionStream(request)` → 聚合 chunks。
    4. 解析 response：content + tool_calls（若有）+ token usage。
    5. 返回 `ChatResponse(content, toolCallDecision, finalAnswer, tokenUsage)`。
  - `chatStream(ChatRequest)`：纯流式接口。
- [ ] 注入 `ModelGatewayBlockingStub` / `ModelGatewayFutureStub` + `RuntimeProperties`。
- [ ] 超时：默认 30s（可配），通过 `withDeadlineAfter` 设置。
- [ ] 异常映射：UNAVAILABLE → `ModelGatewayUnavailableException`；DEADLINE_EXCEEDED → `ModelGatewayTimeoutException`。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 把响应解析抽 `ChatResponseParser`。
- [ ] 加 Caffeine 缓存（可选，相同 prompt 短期缓存，仅对 temperature=0 启用）。

**Commit：** `feat(agent-runtime): T4 ModelGatewayClientImpl chat + stream + tool_call parsing`

---

### Task T5: ToolEngineClientImpl 工具调用

**目标：** 实现 `ToolEngineClient` 接口：调用 agent-tool-engine 的 CallTool RPC。

**Red：**
- [ ] 在 `ToolEngineClientImplTest.java` 写（gRPC in-process mock）：
  - `callTool_returnsResult` 输入 toolId + params → 返回 ToolCallResult（stdout / status=SUCCESS）。
  - `callTool_passesTenantAndAgentContext` 验证请求包含 tenantId / agentId / sessionId。
  - `callTool_throwsOnNotFound` gRPC NOT_FOUND → 抛 `ToolNotFoundException`。
  - `callTool_throwsOnApprovalRequired` gRPC PERMISSION_DENIED（含 approval_call_id）→ 抛 `ToolApprovalRequiredException`。
  - `callTool_throwsOnQuotaExhausted` gRPC RESOURCE_EXHAUSTED → 抛 `ToolQuotaExhaustedException`。
  - `callTool_throwsOnTimeout` gRPC DEADLINE_EXCEEDED → 抛 `ToolExecutionTimeoutException`。
  - `callTool_returnsCleanedStdout` 验证 stdout 已被 tool-engine 清洗（无 PII）。
  - `callTool_recordsCallIdAndAuditId` 返回结果包含 callId / auditId。

**Green：**
- [ ] 新建 `ToolCallMapper.java`：proto CallToolRequest ↔ DTO 映射 + 响应解析。
- [ ] 实现 `ToolEngineClientImpl.java`：
  - `callTool(ToolCallContext ctx)`：
    1. 构造 gRPC CallToolRequest（toolId / params / tenantId / agentId / sessionId / taskId / noCache）。
    2. `stub.callTool(request)` → 解析 response。
    3. 返回 `ToolCallResult(callId, status, stdout, stderr, exitCode, durationMs, cacheHit, auditId)`。
  - `callToolAsync(ToolCallContext)`：异步版本返回 CompletableFuture。
- [ ] 注入 `ToolEngineBlockingStub` / `ToolEngineFutureStub` + `RuntimeProperties`。
- [ ] 超时：默认 60s（可配，含沙箱执行时间）。
- [ ] 异常映射：按 doc 06 §12.4 错误码域。
- [ ] 运行测试，全绿。

**Refactor：**
- [ ] 把响应解析抽 `ToolCallResponseParser`。
- [ ] 加 Micrometer counter：`runtime.tool.call{toolId, status}`。

**Commit：** `feat(agent-runtime): T5 ToolEngineClientImpl call_tool with error mapping`

---
