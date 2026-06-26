# Agent 运行时引擎 详设

> 文档版本：v1.0  |  更新日期：2026-06-26  |  对应 PRD：第二节(五)Agent运行时引擎

## 0. 文档定位与依赖

本文档为 `agent-runtime` 微服务（端口 8092）的工程级详细设计，落地 PRD 第二节第(五)小节"Agent 运行时引擎"的全部要求，并衔接上下游模块契约：

- 架构与端口：[00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md)（§3.1 微服务清单、ADR-002）
- 数据库与 Redis Key：[01-database/database-schema-design.md](../01-database/database-schema-design.md)（§2.5 task_step_log、§3.4 Redis Key、§6.1 agent_definition）
- gRPC 接口契约：[02-api/api-specification.md](../02-api/api-specification.md)（§4 memory、§5 model、§6 task、§7 Agent Runtime）
- 上游需求：[PRD.md](../../PRD.md) 第二节(五) + 第一节(三)第 4 步 + 第三节(二)分级容错

---

## 1. 运行时定位与无状态设计

### 1.1 设计目标

`agent-runtime` 是 Agent 推理循环的执行体，仅负责**单个子任务（subtask）的 ReAct/Reflexion 执行**，不持有任何跨请求的进程内状态。平台层（task-orchestrator/planning/memory/tool-engine）负责调度、管控、校验，本服务只做"接任务—跑循环—上报—销毁"。

| 维度 | 约束 |
|---|---|
| 服务端口 | 8092（仅内部 gRPC + RocketMQ Consumer，不对网关暴露） |
| 状态来源 | 进程内无业务状态；所有执行态外置 Redis+MySQL |
| 并发模型 | Java 17 虚拟线程（Project Loom），单实例并发承载 ≥ 200 个 AgentInstance |
| 生命周期 | RocketMQ 消息触发拉起 → 加载状态 → 执行循环 → 上报/落盘 → 销毁（典型存活 30s~10min） |
| K8s 弹性 | HPA 按消息堆积量扩缩容，Pod 可任意销毁 |

### 1.2 为什么无状态（ADR-002 落地）

| 痛点 | 无状态方案收益 |
|---|---|
| Agent 实例需按需拉起销毁（Serverless） | 实例任意销毁不丢失执行进度，新 Pod 立即接管 |
| 崩溃恢复 | 从 Redis 加载 `runtime:{agentInstanceId}:state` 续跑，无需重放任务 |
| 水平扩容 | 无本地缓存一致性问题，HPA 直接按队列长度伸缩 |
| 灰度发布 | 新旧 Pod 可并存，按版本路由流量 |

**代价**：每步需读写 Redis（~5ms 增量延迟），通过 Pipeline 批量操作 + 本地只读缓存 Agent 静态定义缓解。

### 1.3 状态外置契约

#### 1.3.1 Redis Key 设计（与 [database-schema-design.md §3.4](../01-database/database-schema-design.md#34-redis-短期记忆-key-设计) 对齐）

| Key 模式 | 类型 | TTL | 字段/含义 |
|---|---|---|---|
| `runtime:{agentInstanceId}:state` | Hash | 30min（执行中续期） | 见下表 |
| `sm:{sessionId}:ctx` | Hash | 2h | 当前会话上下文（system/recent_msgs/tools） |
| `sm:{sessionId}:steps` | List | 2h | 本轮推理步骤栈 |
| `sm:{sessionId}:token_water` | String | 2h | Token 水位百分比 |
| `runtime:{agentInstanceId}:lock` | String（NX） | 30s | 执行互斥锁（防同实例并发跑） |

**`runtime:{agentInstanceId}:state` Hash 字段**：

| field | 类型 | 含义 |
|---|---|---|
| `agentInstanceId` | string | 本次执行实例 ID（雪花） |
| `taskId` | string | 任务 ID |
| `subtaskId` | string | 子任务 ID |
| `nodeId` | string | DAG 节点 ID |
| `agentId` | string | Agent 业务 ID |
| `agentVersion` | int | Agent 版本号（防漂移） |
| `sessionId` | string | 关联会话 |
| `tenantId` | int64 | 租户 |
| `traceId` | string | 链路 ID |
| `currentStep` | int | 当前步序号（从 1 起） |
| `currentPhase` | string | 当前阶段：`think`/`act`/`observe`/`reflect`/`idle`/`done`/`suspended` |
| `contextHash` | string | 当前上下文 SHA-256（用于断点比对） |
| `tokenWaterPercent` | int | Token 水位百分比（0~100） |
| `tokenUsed` | int | 累计 Token |
| `costUsedCent` | int64 | 累计成本（分） |
| `loopCount` | int | 已执行循环次数 |
| `reflectionCount` | int | 本步已反思次数 |
| `consecutiveToolFail` | int | 同工具连续失败次数 |
| `lastToolId` | string | 上一次调用的工具 ID |
| `suspendedReason` | string | 挂起原因（人工介入时填） |
| `checkpointPayload` | string | 断点恢复载荷（JSON：上一步 think/act 输入输出快照） |
| `updatedAt` | int64 | 最近心跳时间戳 |

#### 1.3.2 MySQL 落盘契约

| 表 | 用途 | 写入时机 |
|---|---|---|
| `task_step_log` | 步骤明细日志 | 每个阶段（think/act/observe/reflect）结束由 `StepReporter` 通过 gRPC 上报 task-orchestrator 落盘 |
| `tool_call_log` | 工具调用日志 | 由 tool-engine 落盘，runtime 仅持有 callId 关联 |
| `task_instance.cost_used_cent/token_used` | 任务成本累计 | task-orchestrator 汇总各步 ReportStep 增量更新 |
| `memory_long_term` | 长期记忆 | 子任务完成后通过 `memory.write` 事件异步写入 |

### 1.4 实例生命周期

```pseudo
┌──────────────────────────────────────────────────────────────────────┐
│ 1. RocketMQ Consumer 收到 task.subtask.execute 消息                    │
│    - 解析 subtaskId/agentId/nodeId/inputs/config/traceId              │
│    - 幂等校验：查询 runtime:{agentInstanceId}:state 是否已存在          │
│      ├ 不存在 → 新实例                                               │
│      └ 存在且 status=running → 断点续跑（崩溃恢复场景）                │
│                                                                      │
│ 2. 加载执行上下文                                                     │
│    - 拉 agent_definition（含 system_prompt/core_constraints/max_steps │
│      /max_token/reflection_mode/bound_tools）                         │
│    - memory.LoadShortTerm 获取会话上下文与水位                        │
│    - 写入 runtime:{agentInstanceId}:state，加 TTL 续期定时任务          │
│                                                                      │
│ 3. 进入 ReAct 主循环（见 §2）                                         │
│    每步：阶段执行 → StepReporter 上报 → StateSyncer 写 Redis           │
│                                                                      │
│ 4. 循环退出                                                          │
│    ├ 任务完成 → ReportSubtaskDone(SUCCESS) → 删 Redis state          │
│    ├ 熔断 → ReportSubtaskDone(FAILED) → 保留 state 30min 供复盘       │
│    └ 请求人工介入 → RequestHumanIntervention → state.status=suspended │
│                                                                      │
│ 5. Pod 销毁（无清理负担，所有状态已在 Redis/MySQL）                     │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. ReAct 推理循环（核心）

### 2.1 四阶段定义

| 阶段 | 输入 | 调用服务 | 输出 | 退出条件 |
|---|---|---|---|---|
| **Think（思考）** | 当前上下文 + 上一步 observation | `model-gateway.Chat(scene=tool_call, tier=agent_definition.model_tier)` | 思考文本 + tool_calls 决策（或 `final_answer` 决策） | 模型返回正常 |
| **Act（行动）** | tool_calls | `tool-engine.ToolGateway.Invoke`（gRPC） | 工具结果快照 | 工具调用完成或失败 |
| **Observe（观察）** | 工具结果 + 思考历史 | 运行时本地（无外部调用，仅做结果归一化、Token 计数、幻觉初筛） | observation 文本 + 是否需反思 | 观察完成 |
| **Reflect（反思）** | observation + 任务目标 | `model-gateway.Chat(scene=audit, tier=strong)`（reflection_mode ≠ none 时） | 通过/打回/修正建议 | 通过或达反思次数上限 |

### 2.2 循环退出条件

1. **任务完成**：Think 阶段产出 `final_answer` 且 Reflect 通过 → 走 §5.1 完成上报
2. **最大步数熔断**：`loopCount >= agent_definition.max_steps` → §6.1 熔断
3. **Token 上限熔断**：`tokenUsed >= agent_definition.max_token` → §6.2
4. **成本上限熔断**：`costUsedCent >= task_instance.cost_limit_cent` → §6.3
5. **工具连续失败熔断**：`consecutiveToolFail >= 3` → §6.4
6. **不可恢复异常**：业务异常/致命异常（参见 [PRD §三(二)1](../../PRD.md)）→ 直接 FAILED 上报
7. **人工介入请求**：触发场景命中 §7 → 挂起

### 2.3 主循环伪代码

```pseudo
function runReActLoop(ctx: RuntimeContext):
    state = ctx.state
    agentDef = ctx.agentDefinition

    while true:
        # ---- 熔断检查（每轮起点） ----
        if checkCircuitBreaker(state, agentDef) == BREAK:
            return handleBreak(state)

        state.currentStep += 1
        state.loopCount += 1
        state.consecutiveToolFail = 0  # 重置（仅连续失败计数）

        # ---- 1. THINK ----
        state.currentPhase = "think"
        thinkResp = modelGateway.chat(
            scene = "tool_call",
            tier  = agentDef.model_tier,
            messages = contextBuilder.buildThinkMessages(ctx)
        )
        stepReporter.reportStep(phase="think", action_type="model_call",
            action_target=thinkResp.model, output=thinkResp.content,
            token=thinkResp.input_tokens + thinkResp.output_tokens,
            cost=thinkResp.cost_cent)
        state.tokenUsed += thinkResp.total_tokens
        state.costUsedCent += thinkResp.cost_cent

        # 解析决策
        if thinkResp.tool_calls is empty or thinkResp.is_final_answer:
            # 进入最终观察 + 反思
            ctx.lastThink = thinkResp
            goto OBSERVE_FINAL
        else:
            ctx.pendingToolCalls = thinkResp.tool_calls
            ctx.thinkTrace.add(thinkResp)

        # ---- 2. ACT ----
        state.currentPhase = "act"
        for toolCall in ctx.pendingToolCalls:
            toolResp = toolEngine.invoke(
                call_id=toolCall.call_id,
                task_id=state.taskId,
                step_no=state.currentStep,
                agent_id=state.agentId,
                tool_id=toolCall.tool_id,
                input_json=toolCall.input_json
            )
            stepReporter.reportStep(phase="act", action_type="tool_call",
                action_target=toolCall.tool_id,
                input=toolCall.input_json, output=toolResp.output_json,
                status=toolResp.status, error=toolResp.error_msg,
                token=toolResp.token_used, cost=toolResp.cost_cent,
                duration=toolResp.duration_ms)

            if toolResp.status == "failed" or toolResp.status == "timeout":
                state.consecutiveToolFail += (toolCall.tool_id == state.lastToolId ? 1 : 1)
                state.lastToolId = toolCall.tool_id
                if state.consecutiveToolFail >= 3:
                    return handleBreak(state, reason="CONSECUTIVE_TOOL_FAIL")
                # 触发单步重试（§3）
                ctx.retryHint = toolResp.error_msg
                continue
            else:
                state.consecutiveToolFail = 0
                ctx.toolResults.add(toolResp)

        # ---- 3. OBSERVE ----
        state.currentPhase = "observe"
        observation = observeEngine.summarize(ctx.toolResults, ctx.thinkTrace)
        ctx.observationTrace.add(observation)
        stepReporter.reportStep(phase="observe", action_type="none",
            output=observation, status="success")

        # ---- 4. REFLECT（可选） ----
        if agentDef.reflection_mode != "none":
            state.currentPhase = "reflect"
            reflectionResult = reflexionEngine.reflect(ctx, observation)
            stepReporter.reportStep(phase="reflect",
                action_type="model_call",
                action_target=reflectionResult.model,
                output=reflectionResult.verdict,
                token=reflectionResult.total_tokens,
                cost=reflectionResult.cost_cent)

            if reflectionResult.verdict == "RETRY":
                if state.reflectionCount < agentDef.maxReflections:
                    state.reflectionCount += 1
                    ctx.retryHint = reflectionResult.suggestion
                    continue  # 回到 THINK，带修正提示
                else:
                    return handleBreak(state, reason="REFLECTION_EXCEED")
            elif reflectionResult.verdict == "ESCALATE":
                return requestHumanIntervention(ctx, reason="REFLECTION_ESCALATE")
            # verdict == PASS 落到下一轮

        # Token 水位管控（§4）
        tokenWaterGuard.checkAndCompress(ctx)

        # 状态持久化
        stateRecovery.checkpoint(state, ctx)
        state.currentPhase = "idle"

    # ---- 最终输出 ----
    OBSERVE_FINAL:
        observation = observeEngine.finalize(ctx)
        if agentDef.reflection_mode != "none":
            reflectionResult = reflexionEngine.reflectFinal(ctx, observation)
            if reflectionResult.verdict != "PASS":
                return handleBreak(state, reason="FINAL_REFLECTION_FAIL")
        stepReporter.reportSubtaskDone(status="SUCCESS", result=observation)
        state.currentPhase = "done"
        stateRecovery.clear(state)
```

---

## 3. Reflexion 反思机制

### 3.1 单轮 vs 多轮反思

| 模式 | agent_definition.reflection_mode | 触发节点 | 最大次数 | 适用场景 |
|---|---|---|---|---|
| 禁用 | `none` | 不触发 | 0 | L1 简单任务、低成本场景 |
| 单轮 | `single` | 每步 Observe 后自检一次 | 1 | L2 中等任务、常规工具调用 |
| 多轮 | `multi` | 每步可循环反思 | `agent_definition.maxReflections`（默认 3） | L3 复杂任务、高风险写操作、终审 |

### 3.2 触发条件

| 触发源 | 说明 | 处理动作 |
|---|---|---|
| Observe 自检未通过 | 结果质量不达标（格式/来源/完整性） | Reflect 阶段产出修正建议，回到 Think 重试 |
| 工具调用失败 | 工具返回 failed/timeout | Reflect 决定换工具 or 重试 or 升级人工 |
| 校验打回 | 一级规则校验、二级事实一致性未过 | Reflect 产出根因分析，重新生成 tool_calls |
| 最终结果校验未过 | 三级目标完成度校验打回 | Reflect 多轮审查，达到上限仍不过则 ESCALATE |

### 3.3 反思 Prompt 模板

**单轮反思模板**（reflection_mode=single）：

```text
[SYSTEM]
你是 {agent_name} 的反思审查器。请基于以下信息判定当前执行是否合格。

[CORE_CONSTRAINTS]
{agent_definition.core_constraints}

[TASK_GOAL]
{subtask.goal}

[HISTORY]
思考: {last_think}
行动: {last_act_summary}
观察: {last_observation}

[CHECK_DIMENSIONS]
1. 格式合规：输出是否符合 Task Schema
2. 来源可溯：关键事实是否来自工具返回/知识库
3. 目标达成：是否解决 subtask 目标
4. 约束遵守：是否违反 core_constraints

[OUTPUT_FORMAT]
{
  "verdict": "PASS" | "RETRY" | "ESCALATE",
  "reason": "...",
  "suggestion": "...",  // RETRY 时必填，给出具体修正方向
  "confidence": 0.0~1.0
}
```

**多轮反思模板**（reflection_mode=multi）：在第 N 轮反思时追加：

```text
[PREVIOUS_REFLECTIONS]
第 1 轮反思: {reflection_1.verdict} - {reflection_1.suggestion}
第 2 轮反思: {reflection_2.verdict} - {reflection_2.suggestion}
...
当前是第 {N+1} 轮反思。若仍无法判定通过，请输出 ESCALATE。
```

### 3.4 反思次数限制

| 限制项 | 默认值 | 来源 |
|---|---|---|
| 单步最大反思次数 | 3 | `agent_definition.maxReflections`（业务可配，上限 5） |
| 单任务总反思次数 | 10 | 硬编码兜底，避免无限反思 |
| 反思消耗 Token 上限 | `max_token * 20%` | 防止反思本身耗尽预算 |

超过任一限制 → `verdict=ESCALATE`，进入 §7 人工介入流程。

---

## 4. 上下文构建与 Token 水位管控

### 4.1 上下文组装顺序

由 `ContextBuilder` 按以下顺序组装 messages（顺序不可调换，便于 Prompt 缓存命中前缀）：

| 序号 | 段 | 来源 | 压缩优先级（高→低，1=最不易压缩） |
|---|---|---|---|
| 1 | `system_prompt` | agent_definition.system_prompt | 1（永不压缩） |
| 2 | `core_constraints` | agent_definition.core_constraints | 1（永不压缩，[PRD §五(一)1](../../PRD.md) 要求） |
| 3 | `tool_schemas` | tool-engine.Recall 召回的绑定工具 Schema | 4（按需召回，最小化） |
| 4 | `recalled_memories` | memory-service.Recall 多路召回 | 3（可裁剪数量） |
| 5 | `history_messages` | sm:{sessionId}:ctx 中的历史消息 | 2（按主题摘要化） |
| 6 | `current_task` | subtask.goal + inputs + 上游 node 输出 | 1（永不压缩） |
| 7 | `last_observation` | 上一步观察结果 | 2（可裁剪冗余字段） |

### 4.2 Token 水位获取

每步 Think 前调用 [memory-service.LoadShortTerm](../02-api/api-specification.md#4-记忆管理-apigprc内部) 获取水位：

```java
ShortTermMemory shortTerm = memoryServiceBlockingStub.loadShortTerm(
    LoadShortTermRequest.newBuilder()
        .setSessionId(state.sessionId)
        .setAgentId(state.agentId)
        .setMaxRecentTurns(agentDef.maxRecentTurns)
        .setTokenBudget(agentDef.maxToken - state.tokenUsed)
        .setTrace(traceCtx)
        .build()
);
int waterPercent = shortTerm.getTokenWaterPercent();
String compressLevel = shortTerm.getCompressLevel(); // none|light|medium|heavy
```

### 4.3 分级压缩触发

与 [PRD §一(一)3](../../PRD.md) 四级水位线对齐：

| 水位区间 | 等级 | 触发动作 | 调用方 |
|---|---|---|---|
| < 70% | none | 无压缩 | - |
| 70% ~ 85% | light | 裁剪工具返回冗余字段、精简思考过程、折叠闲聊 | memory-service（轻压缩通道） |
| 85% ~ 95% | medium | 早期对话按主题摘要、裁剪召回数量、归档已完成子任务 | memory-service（中压缩通道） |
| ≥ 95% | heavy | 滑动窗口保留最近 K 轮、工具历史仅留结论、清空非核心信息 | memory-service（重压缩通道） + 熔断预警 |

```java
// TokenWaterGuard 实现
public void checkAndCompress(RuntimeContext ctx) {
    int water = ctx.state.tokenWaterPercent;
    CompressLevel level = water < 70 ? NONE
                       : water < 85 ? LIGHT
                       : water < 95 ? MEDIUM
                       : HEAVY;
    if (level == NONE) return;

    if (level == HEAVY) {
        // 同步触发重度压缩，并预警
        stepReporter.reportTokenCritical(ctx.state);
    }
    memoryServiceBlockingStub.triggerCompress(TriggerCompressRequest.newBuilder()
        .setSessionId(ctx.state.sessionId)
        .setLevel(level.name())
        .setTrace(traceCtx)
        .build());
    // 压缩后重新加载水位
    int newWater = reloadWater(ctx);
    ctx.state.tokenWaterPercent = newWater;
    if (newWater >= 95) {
        throw new CircuitBreakException("TOKEN_WATER_HEAVY_UNRECOVERABLE");
    }
}
```

### 4.4 每步水位更新

每个阶段（think/act/observe/reflect）执行后，由 `StateSyncer` 将最新 Token 用量、水位百分比写入 Redis：

```pseudo
function updateWater(state, deltaToken, deltaCost):
    state.tokenUsed += deltaToken
    state.costUsedCent += deltaCost
    state.tokenWaterPercent = (state.tokenUsed * 100) / agentDef.maxToken
    redis.hset("runtime:{agentInstanceId}:state", {
        "tokenUsed": state.tokenUsed,
        "costUsedCent": state.costUsedCent,
        "tokenWaterPercent": state.tokenWaterPercent,
        "updatedAt": now()
    })
    redis.expire("runtime:{agentInstanceId}:state", 1800)  # 续期 30min
    redis.set("sm:{sessionId}:token_water", state.tokenWaterPercent, ex=7200)
```

---

## 5. 执行状态同步

### 5.1 步骤上报（gRPC ReportStep）

每个阶段结束由 `StepReporter` 同步上报 task-orchestrator，由其落 `task_step_log` 表（[database-schema-design.md §2.5](../01-database/database-schema-design.md#25-task_step_log-步骤执行日志表)）：

```java
StepReport report = StepReport.newBuilder()
    .setTaskId(state.taskId)
    .setStepNo(state.currentStep)
    .setNodeId(state.nodeId)
    .setPhase(state.currentPhase)            // think|act|observe|reflect
    .setActionType(actionType)               // model_call|tool_call|none
    .setActionTarget(actionTarget)
    .setInputJson(inputJson)
    .setOutputJson(outputJson)
    .setTokenUsed(tokenDelta)
    .setCostCent(costDelta)
    .setDurationMs(durationMs)
    .setStatus(status)                       // success|failed|retry|skipped
    .setError(error)
    .setTrace(traceCtx)
    .build();
taskOrchestratorBlockingStub.reportStep(report);
```

**幂等**：`(task_id, step_no, phase)` 联合唯一，task-orchestrator 端基于此去重，避免网络重试导致重复落库。

### 5.2 状态变更同步 memory-service

| 时机 | 调用 | 用途 |
|---|---|---|
| 每步 Observe 后 | `memory.write` 事件（RocketMQ） | 增量写入情景记忆（思考片段/工具结论） |
| 子任务完成 | `memory.WriteLongTerm`（gRPC） | 写入长期情景记忆，绑定 source_task_id |
| Token 水位变化 | `memory.LoadShortTerm` 重拉 | 同步水位到 Redis `sm:{sessionId}:token_water` |

### 5.3 断点续跑

实例崩溃后，新 Pod 接管流程：

```pseudo
function recover(agentInstanceId):
    state = redis.hgetAll("runtime:{agentInstanceId}:state")
    if state == null:
        return null  # 已超时清理，按新任务处理
    if state.status == "done":
        return null  # 已完成，忽略重复消息
    if state.status == "suspended":
        return waitHumanResume(state)  # 等待人工恢复

    # 加载 checkpointPayload 重建 RuntimeContext
    ctx = RuntimeContext.fromCheckpoint(state.checkpointPayload)
    ctx.state = state
    # 从断点继续执行主循环
    return runReActLoop(ctx)
```

**断点粒度**：以"阶段"为最小断点单位（think/act/observe/reflect 完成后才写 checkpoint，阶段内崩溃则回退到该阶段起点重跑，避免半成品状态）。

---

## 6. 循环熔断机制

### 6.1 熔断维度汇总

| 熔断类型 | 阈值来源 | 默认值 | 触发后动作 |
|---|---|---|---|
| **最大循环步数** | `agent_definition.max_steps` | 10（L1）/ 20（L2）/ 40（L3） | ReportSubtaskDone(FAILED, reason=MAX_STEPS_EXCEED) |
| **Token 上限** | `agent_definition.max_token` | 60K | ReportSubtaskDone(FAILED, reason=TOKEN_EXCEED) |
| **成本上限** | `task_instance.cost_limit_cent` | 任务级配置 | ReportSubtaskDone(FAILED, reason=COST_EXCEED) |
| **同工具连续失败** | 运行时硬编码 | 3 次 | 触发 Reflect；若仍失败 → ESCLATE 人工介入 |
| **反思次数上限** | `agent_definition.maxReflections * 1.5` | 5 | ESCALATE 人工介入 |
| **单步超时** | `subtask.config.timeoutMs` | 30s/60s/120s | 单步重试 2 次后子任务 FAILED |
| **依赖服务熔断** | Sentinel 熔断器 | 1min 错误率>30% | 等待半开恢复或请求人工介入 |

### 6.2 熔断后动作矩阵

| 场景 | 上报 | 是否保留 Redis state | 是否请求人工介入 |
|---|---|---|---|
| MAX_STEPS_EXCEED | ReportSubtaskDone(FAILED) | 保留 30min 供复盘 | 否（自动失败） |
| TOKEN_EXCEED | ReportSubtaskDone(FAILED) | 保留 30min | 否 |
| COST_EXCEED | ReportSubtaskDone(FAILED) | 保留 30min | 否 |
| CONSECUTIVE_TOOL_FAIL | ReportSubtaskDone(FAILED) | 保留 30min | 视情况（高风险任务 → 是） |
| REFLECTION_EXCEED | RequestHumanIntervention | 保留至挂起恢复 | 是 |
| DEPENDENCY_CIRCUIT_OPEN | RequestHumanIntervention | 保留至挂起恢复 | 是 |

### 6.3 熔断检查伪代码

```pseudo
function checkCircuitBreaker(state, agentDef):
    if state.loopCount >= agentDef.max_steps:
        return Break(MAX_STEPS_EXCEED)
    if state.tokenUsed >= agentDef.max_token:
        return Break(TOKEN_EXCEED)
    if state.costUsedCent >= state.costLimitCent:
        return Break(COST_EXCEED)
    if state.consecutiveToolFail >= 3:
        return Break(CONSECUTIVE_TOOL_FAIL)
    if state.reflectionCount >= agentDef.maxReflections:
        return Break(REFLECTION_EXCEED)
    return CONTINUE
```

---

## 7. 人工介入请求

### 7.1 触发场景

| 场景 | 触发条件 | 数据需求 |
|---|---|---|
| 自动机制全部失效 | 重试 + 反思 + 熔断后仍无法继续 | 完整执行轨迹 + 失败根因 |
| 高风险终审 | R3 工具执行完成需人工确认 | 工具调用快照 + 预期结果 |
| 用户申诉 | 用户对结果打 negative 反馈 | 任务结果 + 反馈标签 |
| 系统故障 | 依赖服务熔断、数据不一致 | 故障范围 + 影响面 |
| 反思升级 | `reflectionResult.verdict == ESCALATE` | 反思记录 |

### 7.2 RequestHumanIntervention 调用流程

```pseudo
function requestHumanIntervention(ctx, reason):
    state = ctx.state
    state.status = "suspended"
    state.suspendedReason = reason
    state.currentPhase = "suspended"
    stateRecovery.checkpoint(state, ctx)  # 持久化挂起状态

    request = HumanInterventionRequest.newBuilder()
        .setTaskId(state.taskId)
        .setSubtaskId(state.subtaskId)
        .setAgentId(state.agentId)
        .setReason(reason)
        .setExecutionTrace(JSON.stringify(ctx.thinkTrace + ctx.toolResults))
        .setSuggestedActions(suggestActions(reason))
        .setTrace(traceCtx)
        .build()
    response = taskOrchestratorBlockingStub.requestHumanIntervention(request)

    # response 返回 interventionId 与挂起策略
    state.interventionId = response.interventionId
    state.suspendedUntil = response.expireAt
    redis.hset("runtime:{agentInstanceId}:state", state.toMap())
    # 当前循环退出，Pod 可销毁；恢复由新消息触发
```

### 7.3 挂起状态保存

挂起时将完整执行上下文序列化到 Redis（不进 MySQL，避免污染 task_step_log）：

```json
// runtime:{agentInstanceId}:state 在挂起时的 checkpointPayload 字段
{
  "thinkTrace": [...],
  "toolResults": [...],
  "observationTrace": [...],
  "reflectionHistory": [...],
  "pendingResumeAction": "retry_from_step|modify_plan|terminate|approve"
}
```

### 7.4 人工操作后恢复

人工操作通过 task-orchestrator 下发 `task.subtask.resume` 消息触发：

| 人工操作 | pendingResumeAction | runtime 行为 |
|---|---|---|
| 修正结果 | `approve` | 直接 ReportSubtaskDone(SUCCESS, corrected_result) |
| 调整路径 | `retry_from_step` | 加载 checkpointPayload，从挂起前 think 阶段重新执行 |
| 终止任务 | `terminate` | ReportSubtaskDone(FAILED, reason=MANUAL_TERMINATE) |
| 重规划 | `modify_plan` | 上报 RequestReplan，由 task-orchestrator 重新生成 DAG |

---

## 8. 核心类设计

包名：`com.agentplatform.runtime.*`，对应 Maven 模块 `agent-runtime`（[tech-stack §4](../00-overview/tech-stack-and-architecture.md#4-项目目录结构) 已规划目录）。

### 8.1 总览

```
com.agentplatform.runtime/
├── AgentRuntimeService          // RocketMQ 入口 + 实例编排
├── react/
│   ├── ReActLoop                // 主循环
│   ├── ThinkPhase               // 思考阶段
│   ├── ActPhase                 // 行动阶段
│   └── ObservePhase             // 观察阶段
├── reflexion/
│   ├── ReflexionEngine          // 反思引擎
│   ├── ReflectionPromptBuilder  // 反思 Prompt 构建
│   └── ReflectionVerdict        // 反思结果枚举
├── context/
│   ├── ContextBuilder           // 上下文组装
│   ├── TokenWaterGuard          // 水位管控
│   └── CompressLevel            // 压缩等级枚举
├── state/
│   ├── StateSyncer              // Redis 状态同步
│   ├── StateRecovery            // 断点恢复
│   └── RuntimeState             // 状态 POJO
├── report/
│   ├── StepReporter             // 步骤上报
│   └── CircuitBreaker           // 熔断检查
└── human/
    └── HumanInterventionHandler // 人工介入处理
```

### 8.2 核心类签名

```java
package com.agentplatform.runtime;

/**
 * Agent 运行时服务入口：消费 RocketMQ task.subtask.execute，
 * 编排实例加载 → 执行循环 → 销毁。
 */
public class AgentRuntimeService {

    /**
     * RocketMQ 消息入口（幂等消费）。
     * 消息格式见 api-specification §7.1
     */
    @RocketMQMessageListener(topic = "task.subtask.execute",
            consumerGroup = "agent-runtime-consumer",
            consumeMode = ConsumeMode.CONCURRENTLY)
    public void onSubtaskExecute(SubtaskExecuteMessage msg);

    /**
     * 恢复入口：消费 task.subtask.resume 消息（人工介入后恢复）。
     */
    @RocketMQMessageListener(topic = "task.subtask.resume",
            consumerGroup = "agent-runtime-resume-consumer")
    public void onSubtaskResume(SubtaskResumeMessage msg);

    /**
     * 加载或恢复 RuntimeContext：若 Redis 已存在 state 则断点续跑，否则新建。
     */
    RuntimeContext loadOrRecover(AgentInstanceKey instanceKey);

    /**
     * 销毁实例：清理本地线程资源，Redis state 由 TTL 自然过期。
     */
    void destroy(AgentInstanceKey instanceKey);
}
```

```java
package com.agentplatform.runtime.react;

/**
 * ReAct 主循环驱动器。线程安全：每个 AgentInstance 独立上下文。
 */
public class ReActLoop {

    private final ThinkPhase thinkPhase;
    private final ActPhase actPhase;
    private final ObservePhase observePhase;
    private final ReflexionEngine reflexionEngine;
    private final TokenWaterGuard tokenWaterGuard;
    private final StepReporter stepReporter;
    private final StateSyncer stateSyncer;
    private final CircuitBreaker circuitBreaker;
    private final HumanInterventionHandler humanInterventionHandler;

    /**
     * 执行主循环，直到退出条件命中。
     * @return SubtaskResult 最终子任务结果
     */
    public SubtaskResult run(RuntimeContext ctx);

    /**
     * 单步执行：think → act → observe → (reflect)。
     * @return 是否继续下一轮
     */
    boolean runSingleStep(RuntimeContext ctx);
}
```

```java
package com.agentplatform.runtime.react;

/**
 * 思考阶段：调用 model-gateway 产出决策。
 */
public class ThinkPhase {

    private final ModelGatewayBlockingStub modelGateway;

    /**
     * @param ctx 运行时上下文
     * @return ThinkResult 包含 content / toolCalls / isFinalAnswer
     */
    public ThinkResult think(RuntimeContext ctx);

    /**
     * 构造模型调用 messages（顺序见 §4.1）。
     */
    List<Message> buildMessages(RuntimeContext ctx);
}

public class ActPhase {
    /**
     * 执行 tool_calls 列表（顺序执行，写操作工具串行；可配置并行）。
     */
    public ActResult act(RuntimeContext ctx, List<ToolCall> toolCalls);
}

public class ObservePhase {
    /**
     * 本地观察：归一化工具结果、Token 计数、幻觉初筛（来源标注检查）。
     */
    public Observation observe(RuntimeContext ctx, ActResult actResult);

    /**
     * 最终观察：子任务完成时输出标准化结果。
     */
    public Observation finalizeResult(RuntimeContext ctx);
}
```

```java
package com.agentplatform.runtime.reflexion;

/**
 * Reflexion 反思引擎。
 * 支持单轮（single）与多轮（multi）模式。
 */
public class ReflexionEngine {

    private final ModelGatewayBlockingStub modelGateway;
    private final ReflectionPromptBuilder promptBuilder;
    private final int maxReflectionsPerStep;   // 默认 3，上限 5
    private final int maxReflectionsPerTask;   // 默认 10

    /**
     * 单步反思：在 Observe 后调用。
     * @return ReflectionResult 包含 verdict / suggestion / confidence
     */
    public ReflectionResult reflect(RuntimeContext ctx, Observation observation);

    /**
     * 最终反思：子任务完成前必执行（若 reflection_mode != none）。
     */
    public ReflectionResult reflectFinal(RuntimeContext ctx, Observation finalObservation);

    /**
     * 多轮反思：循环调用 reflect，直到 PASS 或达上限。
     */
    public ReflectionResult multiReflect(RuntimeContext ctx, Observation observation);
}

public enum ReflectionVerdict {
    PASS,       // 通过，继续下一步
    RETRY,      // 打回，回到 Think 重试（携带 suggestion）
    ESCALATE    // 升级人工介入
}

public class ReflectionResult {
    String model;
    ReflectionVerdict verdict;
    String reason;
    String suggestion;
    double confidence;
    int totalTokens;
    long costCent;
}
```

```java
package com.agentplatform.runtime.context;

/**
 * 上下文构建器：按固定顺序组装 messages。
 */
public class ContextBuilder {

    private final MemoryServiceBlockingStub memoryService;
    private final ToolGatewayBlockingStub toolGateway;

    /**
     * 构造 Think 阶段 messages（含 system/constraints/tools/memories/history/task）。
     */
    public List<Message> buildThinkMessages(RuntimeContext ctx);

    /**
     * 召回工具 Schema（按 scene_tags + query）。
     */
    List<ToolSchema> recallTools(RuntimeContext ctx);

    /**
     * 召回长期记忆（多路融合，top_k 受 Token 预算动态调整）。
     */
    List<RecalledMemory> recallMemories(RuntimeContext ctx);
}

/**
 * Token 水位管控器。
 */
public class TokenWaterGuard {

    private final MemoryServiceBlockingStub memoryService;
    private final StepReporter stepReporter;

    /**
     * 检查水位并触发分级压缩（§4.3）。
     * @throws CircuitBreakException 水位 ≥95% 且压缩后仍无法恢复
     */
    public void checkAndCompress(RuntimeContext ctx);

    /**
     * 重新加载水位（压缩后调用）。
     */
    int reloadWater(RuntimeContext ctx);

    /**
     * 更新 Token 用量到 Redis state。
     */
    void updateWater(RuntimeState state, int deltaToken, long deltaCost);
}

public enum CompressLevel {
    NONE, LIGHT, MEDIUM, HEAVY
}
```

```java
package com.agentplatform.runtime.state;

/**
 * Redis 状态同步器。
 */
public class StateSyncer {

    private final RedisTemplate<String, String> redis;

    /**
     * 写入完整 state（首次创建时）。
     */
    public void init(RuntimeState state);

    /**
     * 增量更新（每阶段结束后）。
     */
    public void sync(RuntimeState state);

    /**
     * 续期 TTL（心跳）。
     */
    public void heartbeat(String agentInstanceId);
}

/**
 * 断点恢复处理器。
 */
public class StateRecovery {

    private final RedisTemplate<String, String> redis;

    /**
     * 加载 Redis state，若不存在返回 null。
     */
    public RuntimeState load(String agentInstanceId);

    /**
     * 写入断点（阶段完成时）。
     */
    public void checkpoint(RuntimeState state, RuntimeContext ctx);

    /**
     * 清理 state（任务完成时）。
     */
    public void clear(String agentInstanceId);

    /**
     * 挂起（人工介入时）。
     */
    public void suspend(RuntimeState state, String reason, String interventionId);
}

/**
 * 运行时状态 POJO（对应 Redis Hash runtime:{agentInstanceId}:state）。
 */
public class RuntimeState {
    String agentInstanceId;
    String taskId;
    String subtaskId;
    String nodeId;
    String agentId;
    int agentVersion;
    String sessionId;
    long tenantId;
    String traceId;
    int currentStep;
    String currentPhase;       // think|act|observe|reflect|idle|done|suspended
    String contextHash;
    int tokenWaterPercent;
    int tokenUsed;
    long costUsedCent;
    long costLimitCent;
    int loopCount;
    int reflectionCount;
    int consecutiveToolFail;
    String lastToolId;
    String status;             // running|done|suspended|failed
    String suspendedReason;
    String interventionId;
    long suspendedUntil;
    String checkpointPayload;  // JSON
    long updatedAt;
}
```

```java
package com.agentplatform.runtime.report;

/**
 * 步骤上报器：gRPC 调用 task-orchestrator.ReportStep。
 */
public class StepReporter {

    private final TaskOrchestratorBlockingStub taskOrchestrator;

    /**
     * 上报单步阶段结果（§5.1）。
     * 幂等：基于 (taskId, stepNo, phase) 去重。
     */
    public ReportAck reportStep(RuntimeState state, String phase,
                                String actionType, String actionTarget,
                                String inputJson, String outputJson,
                                int tokenUsed, long costCent,
                                int durationMs, String status, String error);

    /**
     * 上报子任务完成。
     */
    public ReportAck reportSubtaskDone(RuntimeState state,
                                       String status, String resultJson);

    /**
     * 上报 Token 水位告警（≥95% 时）。
     */
    void reportTokenCritical(RuntimeState state);
}

/**
 * 熔断检查器。
 */
public class CircuitBreaker {

    /**
     * 检查是否触发熔断（§6.3）。
     */
    public BreakResult check(RuntimeState state, AgentDefinition agentDef);
}

public class BreakResult {
    boolean shouldBreak;
    String reason;     // MAX_STEPS_EXCEED|TOKEN_EXCEED|COST_EXCEED|CONSECUTIVE_TOOL_FAIL|REFLECTION_EXCEED
    boolean needHuman;  // 是否需请求人工介入
}
```

```java
package com.agentplatform.runtime.human;

/**
 * 人工介入处理器。
 */
public class HumanInterventionHandler {

    private final TaskOrchestratorBlockingStub taskOrchestrator;
    private final StateRecovery stateRecovery;

    /**
     * 请求人工介入（§7.2）。
     * @param reason 触发原因
     * @return interventionId
     */
    public String requestIntervention(RuntimeContext ctx, String reason);

    /**
     * 等待人工恢复（轮询 Redis state.status，或由 task.subtask.resume 消息唤醒）。
     */
    public RuntimeContext waitForResume(RuntimeState state);

    /**
     * 根据触发原因给出建议动作。
     */
    List<String> suggestActions(String reason);
}
```

### 8.3 gRPC 客户端配置

```java
@Configuration
public class GrpcClientConfig {

    @Bean
    @Qualifier("modelGatewayStub")
    public ModelGatewayBlockingStub modelGatewayStub(@Value("${grpc.model-gateway.host}") String host,
                                                     @Value("${grpc.model-gateway.port}") int port) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .build();
        return ModelGatewayGrpc.newBlockingStub(channel)
            .withDeadlineAfter(60, TimeUnit.SECONDS);
    }

    // toolGatewayStub / memoryServiceStub / taskOrchestratorStub 同样配置
    // 均启用 Sentinel gRPC 拦截器做熔断
}
```

---

## 9. 与上下游交互时序

### 9.1 完整执行时序（正常路径）

```pseudo
用户消息          gateway          orchestrator      agent-runtime       memory-service    tool-engine    model-gateway
   │                 │                  │                   │                   │               │              │
   │─chat──────────►│                  │                   │                   │               │              │
   │                 │─SubmitTask─────►│                   │                   │               │              │
   │                 │                  │ 规划+派发          │                   │               │              │
   │                 │                  │─MQ:task.subtask.execute──────────────►│                   │              │
   │                 │                  │                   │                   │               │              │
   │                 │                  │                   │─LoadShortTerm──────────────────────►│              │
   │                 │                  │                   │◄─ShortTermMemory──────────────────│              │
   │                 │                  │                   │                   │               │              │
   │                 │                  │                   │─Recall────────────────────────────────────────►│ (tools)
   │                 │                  │                   │◄─ToolCandidate─────────────────────────────────│
   │                 │                  │                   │                   │               │              │
   │                 │                  │   [LOOP START]    │                   │               │              │
   │                 │                  │                   │─Chat(scene=tool_call)──────────────────────────►│
   │                 │                  │                   │◄─ChatResponse──────────────────────────────────│
   │                 │                  │◄─ReportStep(think)│                   │               │              │
   │                 │                  │                   │                   │               │              │
   │                 │                  │                   │─Invoke(toolCall)──────────────────►│              │
   │                 │                  │                   │◄─ToolInvokeResponse─────────────────│              │
   │                 │                  │◄─ReportStep(act)  │                   │               │              │
   │                 │                  │                   │                   │               │              │
   │                 │                  │                   │   (observe 本地)   │               │              │
   │                 │                  │◄─ReportStep(observe)                 │               │              │
   │                 │                  │                   │                   │               │              │
   │                 │                  │                   │─Chat(scene=audit)──────────────────────────────►│
   │                 │                  │                   │◄─ChatResponse───────────────────────────────────│
   │                 │                  │◄─ReportStep(reflect)                 │               │              │
   │                 │                  │                   │                   │               │              │
   │                 │                  │                   │─MQ:memory.write (增量)──────────────►│              │
   │                 │                  │                   │   [LOOP END]      │               │              │
   │                 │                  │                   │                   │               │              │
   │                 │                  │◄─ReportSubtaskDone(SUCCESS)──────────│               │              │
   │                 │                  │                   │─WriteLongTerm──────────────────────►│              │
   │                 │                  │                   │                   │               │              │
   │                 │◄─TaskDoneNotify──│                   │                   │               │              │
   │◄─response──────│                  │                   │                   │               │              │
```

### 9.2 上游交互：task-orchestrator

| 方向 | 协议 | 接口 | 时机 |
|---|---|---|---|
| 入站 | RocketMQ `task.subtask.execute` | JSON 消息 | 子任务派发 |
| 入站 | RocketMQ `task.subtask.resume` | JSON 消息 | 人工恢复 |
| 出站 | gRPC | `ReportStep` | 每阶段结束 |
| 出站 | gRPC | `ReportSubtaskDone` | 子任务完成/失败 |
| 出站 | gRPC | `RequestHumanIntervention` | 触发人工介入 |
| 出站 | gRPC | `RequestReplan` | 反思建议重规划 |

### 9.3 下游交互：memory-service

| 方向 | 协议 | 接口 | 时机 |
|---|---|---|---|
| 出站 | gRPC | `LoadShortTerm` | 每轮 Think 前加载上下文与水位 |
| 出站 | gRPC | `Recall` | 召回长期记忆 |
| 出站 | gRPC | `WriteLongTerm` | 子任务完成时写入情景记忆 |
| 出站 | RocketMQ `memory.write` | 增量记忆事件 | 每步 Observe 后异步写入 |
| 出站 | gRPC | `TriggerCompress` | Token 水位达阈值时触发压缩 |

### 9.4 下游交互：tool-engine

| 方向 | 协议 | 接口 | 时机 |
|---|---|---|---|
| 出站 | gRPC | `ToolGateway.Invoke` | Act 阶段调用工具 |
| 出站 | gRPC | `ToolGateway.Recall` | ContextBuilder 召回工具 Schema |
| 入站 | gRPC | `ToolGateway.ReportResult` | 工具调用结果回写（缓存与审计） |

### 9.5 下游交互：model-gateway

| 方向 | 协议 | 接口 | 时机 |
|---|---|---|---|
| 出站 | gRPC | `ModelGateway.Chat` | Think 阶段（scene=tool_call）、Reflect 阶段（scene=audit） |
| 出站 | gRPC | `ModelGateway.ChatStream` | 流式输出场景（可选，session 同步对话） |
| 出站 | gRPC | `ModelGateway.CountTokens` | 上下文组装后预估 Token |

---

## 10. 配置项与默认值

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `agent.runtime.max-concurrent-instances` | 200 | 单 Pod 并发 AgentInstance 上限 |
| `agent.runtime.state-ttl-seconds` | 1800 | Redis state TTL |
| `agent.runtime.heartbeat-interval-seconds` | 10 | 心跳续期间隔 |
| `agent.runtime.tool-fail-threshold` | 3 | 同工具连续失败熔断阈值 |
| `agent.runtime.reflection-default-max` | 3 | 单步反思次数默认上限 |
| `agent.runtime.reflection-hard-cap` | 5 | 单步反思次数硬上限 |
| `agent.runtime.reflection-task-cap` | 10 | 单任务反思次数上限 |
| `agent.runtime.token-water-light` | 70 | 轻度压缩水位 |
| `agent.runtime.token-water-medium` | 85 | 中度压缩水位 |
| `agent.runtime.token-water-heavy` | 95 | 重度压缩水位 |
| `agent.runtime.single-step-timeout-ms` | 30000 | 单步超时 |
| `agent.runtime.grpc-deadline-seconds` | 60 | gRPC 调用默认超时 |

---

## 11. 与其他详设文档的衔接

| 主题 | 关联文档 |
|---|---|
| 任务状态机与编排时序 | [08-flow/state-machines-and-sequences.md](../08-flow/state-machines-and-sequences.md) |
| 记忆系统压缩与召回 | [04-memory/memory-system-design.md](../04-memory/memory-system-design.md) |
| 工具调用全链路 | [05-tool-engine/tool-and-invocation-system.md](../05-tool-engine/tool-and-invocation-system.md) |
| 模型网关路由与计量 | [09-governance-and-deployment/governance-and-middleware.md](../09-governance-and-deployment/governance-and-middleware.md) |
| 任务编排与规划 | [03-task-engine/task-orchestration-and-planning.md](../03-task-engine/task-orchestration-and-planning.md) |
| 数据库表结构 | [01-database/database-schema-design.md](../01-database/database-schema-design.md) |
| gRPC 契约 | [02-api/api-specification.md](../02-api/api-specification.md) §4-7 |

---

## 12. 后续编码计划要点

本文档定义类签名与接口契约，编码实现阶段需补充：

1. `agent-proto` 模块补充 `runtime.proto`（HumanInterventionRequest/Response 等未在 api-specification 展开的 message）
2. `infra/k8s/agent-runtime-deployment.yaml`：HPA 按 RocketMQ 消费堆积量自定义指标
3. Sentinel 规则：对 model-gateway/tool-engine gRPC Stub 配置熔断器
4. 单元测试：ReActLoop / ReflexionEngine / TokenWaterGuard 的状态机覆盖
5. 集成测试：使用 embedded Redis + gRPC InProcessServer 跑断点续跑场景

> 文档结束。
