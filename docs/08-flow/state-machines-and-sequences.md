# 关键流程状态机与时序设计

> 文档版本：v1.0  |  更新日期：2026-06-26  |  对应 PRD：第一节(三)/第三节

## 0. 文档定位与依赖

本文档用 Mermaid 图统一绘制 Agent 平台**关键流程的状态机与端到端时序**，作为编码与联调时的"行为契约"。状态名严格遵循 [03-task-engine/task-orchestration-and-planning.md](../03-task-engine/task-orchestration-and-planning.md) 第 6 节定义的 10 个状态；时序图参与者严格对齐 [00-overview/tech-stack-and-architecture.md](../00-overview/tech-stack-and-architecture.md) §3.1 的微服务清单与 §3.3 通信矩阵。

**依赖文档**：
- [PRD.md](../../PRD.md) — 第一节(三) 完整执行全链路 7 步、第三节 异常容错与质量保障
- [03-task-engine/task-orchestration-and-planning.md](../03-task-engine/task-orchestration-and-planning.md) — §6 任务状态机、§5 重规划、§7 子任务分发
- [05-tool-engine/tool-and-invocation-system.md](../05-tool-engine/tool-and-invocation-system.md) — 工具调用 9 步全链路、R3 审批
- [06-agent-runtime/agent-runtime-engine.md](../06-agent-runtime/agent-runtime-engine.md) — ReAct 循环、Reflexion、断点续跑、人工介入
- [04-memory/memory-system-design.md](../04-memory/memory-system-design.md) — 记忆写入与召回
- [09-governance-and-deployment/governance-and-middleware.md](../09-governance-and-deployment/governance-and-middleware.md) — 异常分级、熔断、成本管控

**参与者别名约定**（贯穿本文所有时序图）：

| 别名 | 含义 |
|---|---|
| `U` | User（终端用户） |
| `GW` | agent-gateway（8080） |
| `SESS` | session-service（8082） |
| `TO` | task-orchestrator（8084） |
| `PL` | planning-service（8086） |
| `MEM` | memory-service（8088） |
| `TE` | tool-engine（8090） |
| `AR` | agent-runtime（8092） |
| `MG` | model-gateway（8094） |
| `QS` | quality-service（8100） |
| `RC` | risk-control（8102） |

---

## 1. 任务状态机图

任务实例 `task_instance.status` 的合法流转矩阵定义于 [task-orchestration-and-planning.md §6.2](../03-task-engine/task-orchestration-and-planning.md#62-合法状态流转矩阵)。状态机唯一归属方为 `task-orchestrator`，所有流转写 `task_state_change` 审计表。终态共 4 个：`SUCCESS / FAILED / CANCELLED / TIMEOUT`；`FAILED / TIMEOUT` 在 R3 高风险任务中允许经用户申诉回到 `WAITING_HUMAN`。

```mermaid
stateDiagram-v2
    direction TB
    [*] --> PENDING : SubmitTask

    PENDING --> PLANNING : L2/L3 触发 AssessComplexity\n动作: 调 PL.AssessComplexity\n回写 task_instance.complexity
    PENDING --> RUNNING : L1 跳规划\n动作: dag_id=NULL\n单 Agent 直跑
    PENDING --> FAILED : 校验失败/Schema 非法\n动作: error_code=VALIDATION_FAILED
    PENDING --> CANCELLED : 用户取消\n动作: CancelTask(trigger=manual)
    PENDING --> TIMEOUT : deadline 到期\n动作: TaskTimeoutScanner 扫描

    PLANNING --> RUNNING : Plan 完成\n动作: 落 task_dag(v1)\n回写 dag_id
    PLANNING --> WAITING_HUMAN : Plan 自检 2 轮未过\n动作: error_code=PLAN_VALIDATION_FAILED
    PLANNING --> FAILED : PLAN_FORMALIZE_FAILED\n动作: 模型非法 JSON 重试 2 次仍失败
    PLANNING --> CANCELLED : 用户取消
    PLANNING --> TIMEOUT : 规划超 60s

    RUNNING --> SUBTASK_RUNNING : 首批节点投递完成\n动作: MQ task.subtask.execute
    RUNNING --> WAITING_HUMAN : R3 节点 requireHumanReview\n动作: 等待人工 ack
    RUNNING --> REPLANNING : 成本/复杂度升级触发\n动作: Replanner.triggerIncremental
    RUNNING --> FAILED : DAG 校验失败
    RUNNING --> CANCELLED : 用户取消
    RUNNING --> TIMEOUT : cost_limit 或 deadline 触发

    SUBTASK_RUNNING --> SUBTASK_RUNNING : 同批次多节点运行
    SUBTASK_RUNNING --> SUCCESS : 所有节点 success\n动作: 结果聚合 + 记忆沉淀
    SUBTASK_RUNNING --> REPLANNING : 节点 failed 且 retryCount≥maxRetries\n动作: Replanner.triggerIncremental
    SUBTASK_RUNNING --> WAITING_HUMAN : 重规划熔断 / R3 终审\n动作: notifyUser
    SUBTASK_RUNNING --> FAILED : 重规划耗尽 + 不允许人工兜底
    SUBTASK_RUNNING --> CANCELLED : 用户取消\n动作: 发 task.subtask.cancel
    SUBTASK_RUNNING --> TIMEOUT : 整体超时

    WAITING_HUMAN --> RUNNING : 人工修正结果后恢复\n动作: trigger=manual, operator=userId
    WAITING_HUMAN --> REPLANNING : 人工指定重规划路径\n动作: RequestReplan(mode=full)
    WAITING_HUMAN --> SUCCESS : 人工确认结果通过\n动作: 写审计
    WAITING_HUMAN --> FAILED : 人工终止 / 超时未响应
    WAITING_HUMAN --> CANCELLED : 人工终止

    REPLANNING --> RUNNING : 全量重规划完成\n动作: 新版本 DAG 落库
    REPLANNING --> SUBTASK_RUNNING : 增量重规划完成\n动作: 从失败批次重新推进
    REPLANNING --> WAITING_HUMAN : 熔断\n动作: 增量≥3次 / 全量≥1次 / 成本超 30%
    REPLANNING --> FAILED : 累计重规划≥4次 / 耗时>60s
    REPLANNING --> CANCELLED : 用户取消
    REPLANNING --> TIMEOUT : 整体超时

    SUCCESS --> [*]
    FAILED --> WAITING_HUMAN : 用户申诉(R3 任务)\n动作: trigger=manual
    TIMEOUT --> WAITING_HUMAN : 用户申诉(R3 任务)
    CANCELLED --> [*]

    note right of PENDING : 唯一入口态
    note right of SUCCESS : 终态: 全部成功+聚合完成
    note right of FAILED : 终态: 不可恢复失败
    note right of CANCELLED : 终态: 用户主动终止
    note right of TIMEOUT : 终态: 成本/时间熔断
    note right of WAITING_HUMAN : 唯一可被人工操作的非终态
    note right of REPLANNING : 由 Replanner 主导, 不允许跨子任务并发
```

**关键约束**：
1. 任何终态 → 非终态 仅允许 `FAILED / TIMEOUT → WAITING_HUMAN` 的申诉路径（且仅 R3 任务允许）。
2. `SUCCESS` 不可流转到任何状态。
3. 除 L1 跳规划外，禁止跳过 `PLANNING` 直接进入 `SUBTASK_RUNNING`。

---

## 2. 工具审批状态机（R3 高危工具）

R3 高危工具调用前必须先有有效的 `tool_approval` 记录，详见 [tool-and-invocation-system.md §4.4](../05-tool-engine/tool-and-invocation-system.md#44-r3-高危工具审批流程)。审批状态落地于 `tool_approval` 表，限时授权（默认 1 小时），双人复核强制。

```mermaid
stateDiagram-v2
    direction LR
    [*] --> pending : 提交审批单\n动作: toolApprovalService.submit\n写 tool_approval(status=pending, expireAt=now+24h)

    pending --> approved : 双人复核通过\n动作: 主审批人 approve\n副审批人 second_approve\nexpireAt=now+1h(限时授权)
    pending --> rejected : 任一审批人驳回\n动作: comment 必填
    pending --> expired : 24h 未操作\n动作: XXL-Job 扫描 expireAt

    approved --> expired : 限时授权过期\n动作: expire_at < now()\n触发 APPROVAL_EXPIRED
    approved --> approved : 同 inputSnapshot 复用\n动作: findValid 命中直接放行

    rejected --> [*]
    expired --> [*]
    approved --> [*] : 调用结束后归档\n(不强制状态变更)

    note right of pending : 阻塞等待, 最长 24h
    note right of approved : 限时窗口默认 1h\n网关前置校验第 5 步放行
    note right of rejected : 不可复用, 需重新提交
    note right of expired : 限时授权到期, 同入参需重新审批
```

**审批通过后的执行流程**（接网关前置校验）：

```mermaid
sequenceDiagram
    autonumber
    participant AR as AR<br/>(agent-runtime)
    participant TE as TE<br/>(tool-engine)
    participant TA as ToolApproval<br/>(tool-engine 内部)
    participant RC as RC<br/>(risk-control)
    participant SB as Sandbox<br/>(Docker 沙箱池)

    AR->>TE: ToolGateway.Invoke(toolId=R3, inputJson)
    TE->>TA: findValid(toolId, taskId, inputSnapshot)
    alt 无有效审批
        TA-->>TE: APPROVAL_REQUIRED
        TE-->>AR: reject(APPROVAL_REQUIRED)
        Note over AR: 触发 Reflect / 人工介入
    else 审批已过期
        TA-->>TE: APPROVAL_EXPIRED
        TE-->>AR: reject(APPROVAL_EXPIRED)
    else 审批有效
        TA-->>TE: approval(approver, expireAt)
        TE->>RC: preCheck(RBAC+ABAC + 高危参数白名单)
        RC-->>TE: allow / deny
        alt deny
            TE-->>AR: reject(FORBIDDEN)
        else allow
            TE->>SB: borrow 容器(inputJson, constraints)
            SB-->>TE: container
            TE->>SB: docker.exec(tool-runner, input)
            SB-->>TE: rawOutput + exitCode
            TE->>SB: docker.rm(container) (一次性使用)
            TE->>TE: ResultCleaner 清洗
            TE-->>AR: ToolInvokeResponse(success, outputJson)
        end
    end
    TE->>TE: ToolCallAuditor 落 tool_call_log + 发 audit 事件
```

**关键约束**：
- R3 强制 `executor_type=sandbox`，禁止 `general/proxy`（见 [tool-and-invocation-system.md §4.1](../05-tool-engine/tool-and-invocation-system.md#41-工具风险三级分级)）。
- 双人复核：主审批人 approve 后仍需副审批人 second_approve，任一驳回即整体 rejected。
- 同 `inputSnapshot` 在 `expire_at` 内可复用，避免重复审批。

---

## 3. 完整执行全链路时序图（核心）

对应 [PRD 第一节(三)](../../PRD.md#三完整执行全链路从用户输入到任务完成) 7 步全链路。本图覆盖一次 L3 复杂任务从接入到记忆沉淀的完整路径；L1/L2 任务可视为本图的简化子集（跳过 PL.Plan、单 Agent 直跑）。

```mermaid
sequenceDiagram
    autonumber
    participant U as U<br/>(User)
    participant GW as GW<br/>(gateway:8080)
    participant SESS as SESS<br/>(session:8082)
    participant MEM as MEM<br/>(memory:8088)
    participant TO as TO<br/>(orchestrator:8084)
    participant PL as PL<br/>(planning:8086)
    participant AR as AR<br/>(runtime:8092)
    participant MG as MG<br/>(model-gw:8094)
    participant TE as TE<br/>(tool-engine:8090)
    participant QS as QS<br/>(quality:8100)
    participant RC as RC<br/>(risk-control:8102)

    %% ===== Step 1: 请求接入与上下文初始化 =====
    rect rgb(235, 245, 255)
    Note over U, MEM: ① 请求接入与上下文初始化 (PRD 一·三·1)
    U->>GW: POST /api/v1/chat (goal, sessionId?)
    GW->>GW: 前置风控 + 鉴权 + 限流
    alt 无 sessionId
        GW->>SESS: CreateSession(userId, tenantId)
        SESS-->>GW: sessionId
    end
    GW->>MEM: LoadShortTerm(sessionId, agentId, maxRecentTurns)
    MEM-->>GW: ShortTermMemory(history, tokenWaterPercent)
    end

    %% ===== Step 2: 意图理解与任务形式化 =====
    rect rgb(255, 245, 235)
    Note over GW, PL: ② 意图理解与任务形式化 (PRD 一·三·2)
    GW->>TO: SubmitTask(goal, sessionId, taskSchema, tenantId, traceId)
    TO->>TO: 落 task_instance(status=PENDING)
    TO->>PL: gRPC AssessComplexity(goal, schema, sceneTags)
    PL->>PL: 规则初筛(<5ms) → 置信度<0.9 调 MG 精判
    alt 模型精判
        PL->>MG: Chat(scene=intent, tier=light)
        MG-->>PL: 六维度打分 + L1/L2/L3
    end
    PL-->>TO: AssessResult(level=L3, dimensions, reasoning)
    TO->>TO: 回写 complexity, 状态转 PLANNING
    TO-->>GW: taskId (异步, 通过 SSE 推送进度)
    end

    %% ===== Step 3: 任务规划与多 Agent 编排 =====
    rect rgb(235, 255, 235)
    Note over TO, PL: ③ 任务规划与多 Agent 编排 (PRD 一·三·3)
    TO->>PL: gRPC Plan(taskId, goal, schema, complexity=L3)
    PL->>PL: 1. 语义形式化 (调 MG strong)
    PL->>PL: 2. 拆解策略决策 (模板匹配 / 智能拆解)
    alt 模板命中
        PL->>PL: 3a. 模板参数填充
    else 智能规划
        PL->>MG: Chat(scene=planning, tier=strong, cot=true)
        MG-->>PL: 原始 DAG 草稿
    end
    PL->>PL: 4. DagBuilder 构建 + 环检测 + 并行批次
    PL->>PL: 5. PlanValidator 五维自检(完备/原子/效率/成本/容错)
    PL->>PL: 6. 输出标准化执行计划
    PL->>PL: 落 task_dag(version=1, source=template|ai)
    PL-->>TO: PlanResponse(dagJson, version, source)
    TO->>TO: 回写 dag_id, 状态转 RUNNING
    end

    %% ===== Step 4: Agent 运行与工具调用执行 =====
    rect rgb(255, 235, 255)
    Note over TO, TE: ④ Agent 运行与工具调用执行 (PRD 一·三·4)
    TO->>TO: DagExecutor 取首批 parallel_batch
    loop 每个并行节点
        TO->>TE: (异步) MQ task.subtask.execute(node, inputs, config)
    end
    TO->>TO: 状态转 SUBTASK_RUNNING, 广播 task.state.change

    loop ReAct 循环 (Think→Act→Observe→Reflect)
        AR->>MG: Chat(scene=tool_call, tier=middle) - Think
        MG-->>AR: tool_calls / final_answer
        AR->>TO: ReportStep(phase=think)
        alt 含 tool_calls
            AR->>TE: ToolGateway.Invoke(toolId, inputJson) - Act
            TE->>RC: preCheck(RBAC+ABAC + 配额 + 审批)
            RC-->>TE: allow
            TE->>TE: Executor 执行 + ResultCleaner
            TE-->>AR: ToolInvokeResponse
            AR->>TO: ReportStep(phase=act)
        end
        AR->>AR: Observe (本地归一化 + 幻觉初筛)
        AR->>TO: ReportStep(phase=observe)
        AR->>MEM: MQ memory.write (增量情景记忆)
        AR->>MG: Chat(scene=audit, tier=strong) - Reflect (可选)
        MG-->>AR: verdict=PASS/RETRY/ESCALATE
        AR->>TO: ReportStep(phase=reflect)
    end

    AR->>TO: MQ task.subtask.done(status, outputs, cost, token)
    end

    %% ===== Step 5: 结果聚合与质量校验 =====
    rect rgb(255, 255, 220)
    Note over TO, QS: ⑤ 结果聚合与质量校验 (PRD 一·三·5)
    TO->>TO: 所有节点 success → 聚合 outputs
    TO->>QS: ValidateResult(taskId, deliverables, nodeOutputs)
    QS->>QS: 一级规则校验(格式/合规/边界)
    QS->>QS: 二级事实一致性(对比工具返回)
    QS->>MG: Chat(scene=audit, tier=strong) 三级目标完成度
    MG-->>QS: score + verdict
    QS-->>TO: ValidateResult(passed=true/false, score, issues)
    alt 校验不通过
        TO->>TO: 触发重规划或人工介入
    end
    end

    %% ===== Step 6: 响应输出与会话收尾 =====
    rect rgb(220, 245, 245)
    Note over U, TO: ⑥ 响应输出与会话收尾 (PRD 一·三·6)
    TO->>TO: 状态转 SUCCESS, 写 task_state_change
    TO->>SESS: TaskDoneNotify(taskId, result_summary)
    SESS->>SESS: 归档会话状态, 写 session_history
    SESS->>GW: SSE 推送最终结果
    GW-->>U: HTTP Response / SSE 流式结果
    end

    %% ===== Step 7: 记忆沉淀与能力迭代 =====
    rect rgb(245, 245, 220)
    Note over AR, QS: ⑦ 记忆沉淀与能力迭代 (PRD 一·三·7)
    AR->>MEM: gRPC WriteLongTerm(taskId, subtaskTrace, deliverables)
    MEM->>MEM: 提取→标签→评分→向量化→去重→Milvus+MySQL
    TO->>QS: 异步触发效果评估
    QS->>QS: 评测任务完成率/幻觉率/工具成功率
    QS->>QS: 写 ClickHouse agent_metrics_daily
    alt 高价值流程
        QS->>TO: 建议沉淀 task_template (success_rate≥0.85)
        TO->>TO: 更新模板 usage_count + success_rate
    end
    end
```

**典型耗时**：L1 < 3s，L2 3~15s，L3 > 15s；具体 SLA 基线见 [tech-stack-and-architecture.md §1.2](../00-overview/tech-stack-and-architecture.md#12-非功能性指标基线)。

---

## 4. ReAct 循环时序图

对应 [agent-runtime-engine.md §2](../06-agent-runtime/agent-runtime-engine.md#2-react-推理循环核心) 的 Think→Act→Observe→Reflect 四阶段循环。退出条件含 7 类（任务完成/最大步数/Token 上限/成本上限/工具连续失败/不可恢复异常/人工介入）。

```mermaid
sequenceDiagram
    autonumber
    participant AR as AR<br/>(agent-runtime)
    participant MG as MG<br/>(model-gateway)
    participant TE as TE<br/>(tool-engine)
    participant RC as RC<br/>(risk-control)
    participant MEM as MEM<br/>(memory-service)
    participant TO as TO<br/>(task-orchestrator)

    Note over AR: 进入 ReAct 主循环 (loopCount=0)

    loop loopCount < max_steps
        AR->>AR: checkCircuitBreaker (步数/Token/成本/工具失败/反思次数)
        alt 命中熔断
            AR->>TO: ReportSubtaskDone(FAILED, reason=BREAK_*)
            Note over AR: 退出循环 (退出条件 2~6)
        end

        AR->>AR: loopCount++, currentStep++, currentPhase=think

        %% ===== Think =====
        rect rgb(230, 240, 255)
        Note over AR, MG: Think 阶段
        AR->>MEM: LoadShortTerm (拉取水位 + 历史)
        MEM-->>AR: ShortTermMemory(tokenWaterPercent, history)
        AR->>AR: ContextBuilder 组装 messages (system/constraints/tools/memories/history/task)
        AR->>MG: Chat(scene=tool_call, tier=agent.model_tier)
        MG-->>AR: ChatResponse(content, tool_calls, is_final_answer)
        AR->>TO: ReportStep(phase=think, action_type=model_call, token, cost)
        end

        alt is_final_answer == true
            AR->>AR: 跳转 OBSERVE_FINAL
        else 含 tool_calls
            %% ===== Act =====
            rect rgb(230, 255, 230)
            Note over AR, TE: Act 阶段
            loop 每个 toolCall (写操作串行)
                AR->>TE: ToolGateway.Invoke(call_id, tool_id, inputJson)
                TE->>RC: preCheck (工具状态/Schema/绑定/RBAC+ABAC/R3审批/配额/缓存)
                RC-->>TE: allow / deny
                alt allow
                    TE->>TE: Executor 路由(general/proxy/sandbox) + 隔离执行
                    TE->>TE: ResultCleaner 清洗 (Schema 校验+Token 截断+错误码归一化)
                    TE-->>AR: ToolInvokeResponse(success, outputJson)
                    AR->>AR: consecutiveToolFail=0
                else deny / failed / timeout
                    TE-->>AR: ToolInvokeResponse(failed/timeout)
                    AR->>AR: consecutiveToolFail++, lastToolId=tool_id
                    alt consecutiveToolFail ≥ 3
                        AR->>TO: ReportSubtaskDone(FAILED, reason=CONSECUTIVE_TOOL_FAIL)
                        Note over AR: 退出循环 (退出条件 5)
                    end
                end
                AR->>TO: ReportStep(phase=act, action_type=tool_call, input, output, status, duration)
            end
            end

            %% ===== Observe =====
            rect rgb(255, 245, 230)
            Note over AR, MEM: Observe 阶段 (本地无外部调用)
            AR->>AR: ObservePhase.summarize (归一化+Token 计数+幻觉初筛)
            AR->>AR: observationTrace.add(observation)
            AR->>TO: ReportStep(phase=observe, output=observation, status=success)
            AR->>MEM: MQ memory.write (增量情景记忆: 思考片段+工具结论)
            end

            %% ===== Reflect (可选) =====
            rect rgb(255, 230, 245)
            Note over AR, MG: Reflect 阶段 (reflection_mode ≠ none)
            AR->>MG: Chat(scene=audit, tier=strong, prompt=反思模板)
            MG-->>AR: ReflectionResult(verdict, suggestion, confidence)
            AR->>TO: ReportStep(phase=reflect, action_type=model_call, verdict)
            alt verdict == RETRY
                AR->>AR: reflectionCount++
                alt reflectionCount < maxReflections
                    Note over AR: 携带 suggestion 回到 Think (重试当前步)
                else
                    AR->>TO: RequestHumanIntervention(reason=REFLECTION_EXCEED)
                    Note over AR: 退出循环 (退出条件 7)
                end
            else verdict == ESCALATE
                AR->>TO: RequestHumanIntervention(reason=REFLECTION_ESCALATE)
                Note over AR: 退出循环 (退出条件 7)
            else verdict == PASS
                Note over AR: 落到下一轮循环
            end
            end

            %% ===== Token 水位管控 =====
            AR->>AR: TokenWaterGuard.checkAndCompress
            alt 水位 ≥95% 且压缩后仍 ≥95%
                AR->>TO: ReportSubtaskDone(FAILED, reason=TOKEN_WATER_HEAVY_UNRECOVERABLE)
                Note over AR: 退出循环 (退出条件 3)
            end

            AR->>AR: StateRecovery.checkpoint (写 Redis state, 续期 TTL)
        end
    end

    %% ===== 最终输出 =====
    rect rgb(240, 255, 240)
    Note over AR, TO: 循环正常退出 (退出条件 1: 任务完成)
    AR->>AR: OBSERVE_FINAL: ObservePhase.finalizeResult
    alt reflection_mode ≠ none
        AR->>MG: Chat(scene=audit, tier=strong) - 最终反思
        MG-->>AR: verdict
        alt verdict != PASS
            AR->>TO: ReportSubtaskDone(FAILED, reason=FINAL_REFLECTION_FAIL)
        end
    end
    AR->>TO: ReportSubtaskDone(SUCCESS, result=observation)
    AR->>MEM: gRPC WriteLongTerm (写长期记忆)
    AR->>AR: StateRecovery.clear (删 Redis state)
    end
```

**循环退出条件标注**（对应 [agent-runtime-engine.md §2.2](../06-agent-runtime/agent-runtime-engine.md#22-循环退出条件)）：

| # | 退出条件 | 触发判断 | 上报动作 |
|---|---|---|---|
| 1 | 任务完成 | Think 产出 `final_answer` 且 Reflect PASS | `ReportSubtaskDone(SUCCESS)` |
| 2 | 最大步数熔断 | `loopCount ≥ max_steps`（L1=10 / L2=20 / L3=40） | `ReportSubtaskDone(FAILED, MAX_STEPS_EXCEED)` |
| 3 | Token 上限熔断 | `tokenUsed ≥ max_token`（默认 60K）或水位 ≥95% 不可恢复 | `ReportSubtaskDone(FAILED, TOKEN_EXCEED)` |
| 4 | 成本上限熔断 | `costUsedCent ≥ cost_limit_cent` | `ReportSubtaskDone(FAILED, COST_EXCEED)` |
| 5 | 工具连续失败熔断 | `consecutiveToolFail ≥ 3` | `ReportSubtaskDone(FAILED, CONSECUTIVE_TOOL_FAIL)` |
| 6 | 不可恢复异常 | 业务/致命异常 | `ReportSubtaskDone(FAILED, reason=*)` |
| 7 | 人工介入请求 | Reflect verdict=ESCALATE / 反思超限 / 依赖熔断 | `RequestHumanIntervention` |

---

## 5. 动态重规划时序图

对应 [task-orchestration-and-planning.md §5](../03-task-engine/task-orchestration-and-planning.md#5-动态重规划机制) 与 §9.2 重规划子流程。触发源包括：子任务连续失败、校验不通过、核心依赖失效、用户需求变更、复杂度动态升级、成本超限、Agent 跨域能力不足。

```mermaid
sequenceDiagram
    autonumber
    participant AR as AR<br/>(agent-runtime)
    participant TO as TO<br/>(orchestrator)
    participant PL as PL<br/>(planning)
    participant MEM as MEM<br/>(memory)
    participant U as U<br/>(User)

    Note over AR: 子任务执行中遇到不可恢复失败

    %% ===== 触发上报 =====
    rect rgb(255, 240, 240)
    Note over AR, TO: 触发阶段
    AR->>AR: 节点 retryCount ≥ maxRetries 仍 failed
    AR->>AR: 检查 fallback Agent / fallback tool
    alt 有降级路径
        AR->>AR: switchAgentAndRetry
        Note over AR: 不触发重规划, 直接换路重试
    else 无降级路径
        AR->>TO: MQ task.subtask.done(status=failed, reason, errorLogs)
    end
    end

    %% ===== 熔断检查 + 状态切换 =====
    rect rgb(255, 245, 220)
    Note over TO: 熔断检查阶段
    TO->>TO: SubtaskDoneHandler 消费 failed 事件
    TO->>TO: Replanner.checkQuota(taskId)
    alt 累计重规划 ≥4 次 / 全量 ≥1 次 / 成本超 30%
        TO->>TO: 状态转 WAITING_HUMAN
        TO->>U: SSE 通知用户"重规划熔断, 需人工介入"
        Note over TO: 流程结束, 进入人工介入
    else 配额充足
        TO->>TO: 状态转 REPLANNING, 广播 task.state.change
    end
    end

    %% ===== 增量重规划 =====
    rect rgb(230, 245, 255)
    Note over TO, PL: 增量重规划阶段 (mode=incremental, 默认优先)
    TO->>PL: gRPC Plan(mode=incremental, frozenNodes, failedNodeIds, failureContext)
    PL->>PL: 标记已成功节点为"冻结" (status=SUCCESS)
    PL->>PL: 计算受影响节点 (failed + 所有下游)
    alt 模板场景
        PL->>PL: 模板参数填充失败 → 回退智能拆解
    end
    PL->>MEM: Recall (流程记忆辅助拆解)
    MEM-->>PL: similarTaskHistory / bestPractice
    PL->>PL: AiPlanner 生成新子 DAG (受影响部分)
    PL->>PL: merge(oldDag.frozen + newSubDag) + 环检测 + 并行批次
    PL->>PL: PlanValidator 五维自检
    alt 自检 2 轮未过
        PL-->>TO: PlanResponse(success=false, error=PLAN_VALIDATION_FAILED)
        TO->>TO: 升级为全量重规划 (限 1 次)
    else 自检通过
        PL->>PL: 落 task_dag(version+1, source=ai)
        PL->>PL: 写 task_replan_log(replanNo, oldVersion, newVersion, cost)
        PL-->>TO: PlanResponse(success=true, newDagVersion, mergedDag)
    end
    end

    %% ===== 全量重规划(降级路径) =====
    rect rgb(255, 230, 230)
    Note over TO, PL: 全量重规划 (mode=full, 仅当增量 2 次失败或用户需求变更)
    TO->>TO: snapshot 当前 DAG (用于回滚)
    TO->>PL: gRPC Plan(mode=full, prefer_template=false)
    PL->>PL: 强模型重新生成完整 DAG
    PL->>PL: 重置未成功节点为 pending (除已成功且不可逆的写操作)
    PL->>PL: 落 task_dag(version+1) + task_replan_log
    PL-->>TO: PlanResponse(success=true, newDagVersion)
    end

    %% ===== 状态恢复 + 重新分发 =====
    rect rgb(230, 255, 230)
    Note over TO, AR: 状态恢复与重新分发
    alt 增量重规划完成
        TO->>TO: 状态转 SUBTASK_RUNNING
        TO->>TO: DagExecutor 从失败批次重新推进
    else 全量重规划完成
        TO->>TO: 状态转 RUNNING
        TO->>TO: DagExecutor 从首个批次重新推进
    end
    TO->>AR: MQ task.subtask.execute (新 DAG 版本的节点, 含 failureContext 提示)
    Note over AR: 新 AgentInstance 接管, 携带"避免重复犯错"提示
    end
```

**关键约束**：
- 增量重规划保留 `frozenNodes`（已 success 节点不重跑），降低成本。
- 全量重规划上限 1 次，累计重规划上限 4 次（见 [task-orchestration-and-planning.md §5.3](../03-task-engine/task-orchestration-and-planning.md#53-重规划熔断)）。
- 单次重规划耗时 > 60s 直接转 `FAILED`。

---

## 6. 人工介入时序图

对应 [agent-runtime-engine.md §7](../06-agent-runtime/agent-runtime-engine.md#7-人工介入请求) 与 [task-orchestration-and-planning.md §9.3](../03-task-engine/task-orchestration-and-planning.md#93-人工介入子流程waiting_human)。触发场景：自动机制全部失效、R3 高风险终审、用户申诉、系统故障、Reflect verdict=ESCALATE。

```mermaid
sequenceDiagram
    autonumber
    participant AR as AR<br/>(agent-runtime)
    participant TO as TO<br/>(orchestrator)
    participant SESS as SESS<br/>(session)
    participant U as U<br/>(User)
    participant MEM as MEM<br/>(memory)

    Note over AR: 运行中触发人工介入

    %% ===== 触发与挂起 =====
    rect rgb(255, 240, 240)
    Note over AR, TO: 挂起阶段
    AR->>AR: state.status=suspended, currentPhase=suspended
    AR->>AR: StateRecovery.suspend(checkpointPayload=完整执行轨迹)
    AR->>TO: gRPC RequestHumanIntervention(taskId, subtaskId, reason, executionTrace, suggestedActions)
    TO->>TO: 状态转 WAITING_HUMAN, 写 task_state_change(trigger=auto)
    TO->>TO: 创建 intervention 记录, 设 suspendedUntil=expireAt
    TO-->>AR: HumanInterventionResponse(interventionId, expireAt)
    AR->>AR: 写 Redis state (suspended), 当前 Pod 可销毁
    end

    %% ===== 通知用户 =====
    rect rgb(255, 245, 220)
    Note over TO, U: 通知阶段
    TO->>SESS: NotifyHumanIntervention(taskId, interventionId, reason, suggestedActions)
    SESS->>U: SSE 推送 + IM/Web 控制台通知
    Note over U: 用户看到: 执行轨迹 + 失败根因 + 建议动作
    end

    %% ===== 用户决策 =====
    rect rgb(230, 245, 255)
    Note over U, TO: 用户操作阶段 (4 选 1)
    alt 修正结果 (approve)
        U->>TO: SubmitInterventionResult(interventionId, action=approve, correctedResult)
        TO->>TO: 状态转 SUCCESS, 写 task_state_change(trigger=manual, operator=userId)
        TO->>AR: MQ task.subtask.resume(pendingResumeAction=approve)
        AR->>TO: ReportSubtaskDone(SUCCESS, corrected_result)
    else 调整路径 (retry_from_step)
        U->>TO: SubmitInterventionResult(action=retry_from_step, modifiedContext)
        TO->>TO: 状态转 RUNNING, 写 task_state_change(trigger=manual)
        TO->>AR: MQ task.subtask.resume(pendingResumeAction=retry_from_step, modifiedContext)
        AR->>AR: 加载 checkpointPayload, 从挂起前 think 阶段重新执行
    else 终止任务 (terminate)
        U->>TO: SubmitInterventionResult(action=terminate)
        TO->>TO: 状态转 CANCELLED, 写 task_state_change(trigger=manual)
        TO->>AR: MQ task.subtask.resume(pendingResumeAction=terminate)
        AR->>TO: ReportSubtaskDone(FAILED, reason=MANUAL_TERMINATE)
    else 重规划 (modify_plan)
        U->>TO: SubmitInterventionResult(action=modify_plan, newRequirement)
        TO->>TO: 状态转 REPLANNING, 写 task_state_change(trigger=manual)
        Note over TO: 走 §5 动态重规划流程 (mode=full)
        TO->>AR: 通过 task.subtask.execute 重新分发新 DAG 版本节点
    end
    end

    %% ===== 闭环优化 =====
    rect rgb(245, 245, 220)
    Note over TO, MEM: 闭环优化阶段
    TO->>MEM: 记录 Badcase (触发原因 + 用户操作 + 最终结果)
    MEM->>MEM: 异步触发 Badcase 回流
    Note over MEM: 用于优化自动容错能力, 减少 future 人工介入率
    end
```

**关键约束**：
- 挂起状态完整保存到 Redis（不进 MySQL，避免污染 `task_step_log`），TTL 30min 续期。
- 所有人工操作写 `task_state_change`，`trigger=manual, operator=userId` 留痕。
- 4 类操作均通过 `task.subtask.resume` 消息唤醒新 Pod 接管，原 Pod 已销毁。

---

## 7. 工具调用全链路时序图

对应 [tool-and-invocation-system.md §3](../05-tool-engine/tool-and-invocation-system.md#3-工具调用全链路标准流程) 9 步标准流程：召回 → 指令 → 网关 → 前置校验 → 路由 → 隔离执行 → 清洗 → 返回 → 审计。

```mermaid
sequenceDiagram
    autonumber
    participant AR as AR<br/>(agent-runtime)
    participant MG as MG<br/>(model-gateway)
    participant TE as TE<br/>(tool-engine)
    participant Milvus as Milvus<br/>(tool_index)
    participant RC as RC<br/>(risk-control)
    participant Exec as Executor<br/>(general/proxy/sandbox)
    participant DB as MySQL<br/>(tool_call_log)
    participant MQ as RocketMQ<br/>(tool.call.audit)

    %% ===== Step 1: 工具语义召回 =====
    rect rgb(230, 240, 255)
    Note over AR, Milvus: ① 工具语义召回 (ToolRecaller)
    AR->>TE: ToolGateway.Recall(query, sceneTags, topK=30)
    TE->>Milvus: 向量检索 + 场景标签过滤 (status==2)
    Milvus-->>TE: Top-30 候选
    TE->>TE: 重排: 0.6*相关性 + 0.3*成本 + 0.1*风险偏好
    TE-->>AR: Top-5 ToolCandidate (含 inputSchema 最小化)
    end

    %% ===== Step 2: 模型生成调用指令 =====
    rect rgb(255, 245, 230)
    Note over AR, MG: ② 模型生成调用指令 (Think 阶段)
    AR->>MG: Chat(scene=tool_call, tier=middle, tools=Top-5 schemas)
    MG-->>AR: tool_calls = [{toolId, inputJson}, ...]
    end

    %% ===== Step 3: 统一网关接入 =====
    rect rgb(230, 255, 230)
    Note over AR, TE: ③ 统一网关接入 (ToolGateway.Invoke)
    AR->>TE: ToolGateway.Invoke(call_id, task_id, step_no, agent_id, tool_id, input_json, trace)
    TE->>TE: 生成 ToolCallContext (贯穿后续所有步骤)
    TE->>TE: 注入 SkyWalking TraceID 透传
    end

    %% ===== Step 4: 前置校验 =====
    rect rgb(255, 240, 240)
    Note over TE, RC: ④ 前置校验 (8 项顺序校验, 任一失败即 reject)
    TE->>TE: 1. 工具存在性 + 状态校验 (status=2)
    TE->>TE: 2. inputSchema 参数校验 (JSON Schema Validator)
    TE->>TE: 3. 工具-Agent 绑定校验
    TE->>TE: 4. 召回-指令对齐校验 (防幻觉: toolId 必须在召回结果中)
    TE->>RC: 5. RBAC + ABAC 权限校验 (subject=agent, resource=tool, action=execute)
    RC-->>TE: allow / deny (越权实时拦截 + 告警)
    alt R3 高危工具
        TE->>TE: 5b. R3 审批校验 (tool_approval.findValid)
        alt 无审批/过期
            TE-->>AR: reject(APPROVAL_REQUIRED / APPROVAL_EXPIRED)
            Note over TE: 落审计 (status=blocked)
        end
    end
    TE->>TE: 6. 高危参数白名单校验 (R2/R3, x-param-whitelist)
    TE->>TE: 7. 配额校验 (tryAcquire: 租户级/业务线级/任务级三级)
    TE->>TE: 8. 缓存命中校验 (仅 R1, inputHash)
    alt 缓存命中
        TE-->>AR: ToolInvokeResponse(from_cache=true)
        Note over TE: 跳过 ⑤⑥⑦, 直接到 ⑧⑨
    end
    end

    %% ===== Step 5: 路由分发 =====
    rect rgb(245, 230, 255)
    Note over TE, Exec: ⑤ 路由分发 (ToolExecutorRouter)
    TE->>TE: 按 tool_registry.executor_type 路由
    alt general (R1 只读)
        TE->>Exec: GeneralExecutor (JVM 内虚拟线程)
    else proxy (R1/R2 业务服务)
        TE->>Exec: ProxyExecutor (gRPC Stub + 熔断器)
    else sandbox (R3 强制)
        TE->>Exec: SandboxExecutor (Docker 容器)
    end
    end

    %% ===== Step 6: 隔离执行 =====
    rect rgb(255, 245, 230)
    Note over Exec: ⑥ 隔离执行 (3 种模式)
    alt general
        Exec->>Exec: rateLimiter.acquire + CompletableFuture.orTimeout
        Exec-->>TE: ToolRawResult
    else proxy
        Exec->>Exec: 熔断器 + 指数退避重试 (retryable=true, 最多 3 次)
        Exec-->>TE: ToolRawResult
    else sandbox
        Exec->>Exec: sandboxPool.borrow → docker.exec → docker.waitContainer
        alt 超时
            Exec->>Exec: docker.kill(SIGKILL)
            Exec-->>TE: ToolRawResult(timeout=true)
        else OOM (exitCode=137)
            Exec-->>TE: ToolRawResult(failed, SANDBOX_OOM)
        else 正常
            Exec->>Exec: docker.rm (一次性使用)
            Exec-->>TE: ToolRawResult(success, stdout)
        end
    end
    end

    %% ===== Step 7: 结果标准化清洗 =====
    rect rgb(230, 255, 245)
    Note over TE: ⑦ 结果标准化清洗 (ResultCleaner)
    TE->>TE: Schema 校验 (output_schema, 失败尝试容错修复)
    TE->>TE: 冗余字段裁剪 (按 outputSchema 过滤 + 去 _meta/_debug)
    TE->>TE: Token 截断 (≤max_output_token, 超限触发摘要/分页)
    TE->>TE: 错误码归一化 (gRPC/HTTP/Container → tool.errorCodes)
    TE-->>TE: CleanResult(outputJson, tokenUsed)
    end

    %% ===== Step 8: 返回 Agent 上下文 =====
    rect rgb(245, 245, 220)
    Note over TE, AR: ⑧ 返回 Agent 上下文
    TE->>TE: buildResponse (callId, status, outputJson, errorCode, costCent, tokenUsed, fromCache)
    TE->>TE: ToolQuotaManager.confirmAcquire (按实际成本对账)
    TE-->>AR: ToolInvokeResponse
    end

    %% ===== Step 9: 全量落盘审计 =====
    rect rgb(255, 240, 245)
    Note over TE, MQ: ⑨ 全量落盘审计 (双写: 强一致 + 最终一致)
    TE->>DB: 写 tool_call_log (强一致, 调用返回前完成)<br/>含: callId/taskId/agentId/toolId/input(脱敏)/output(截断)/status/errorCode/cost/token/riskLevel/approvedBy/traceId
    TE->>MQ: 发 tool.call.audit (异步, risk-control 消费)
    MQ->>RC: 异步消费 (合规审计 + Badcase 归集)
    end
```

**关键约束**（[tool-and-invocation-system.md §11.3](../05-tool-engine/tool-and-invocation-system.md#113-关键约束清单)）：
- C-001: 所有工具调用必经 `ToolGateway`，禁止 Agent 直连（ADR-005）。
- C-002: R3 强制 `executor_type=sandbox`。
- C-007: 沙箱容器一次性使用，不复用。
- C-008: 调用全量落 `tool_call_log` + `audit_log`。

---

## 8. Token 水位压缩流程图

对应 [agent-runtime-engine.md §4.3](../06-agent-runtime/agent-runtime-engine.md#43-分级压缩触发) 与 [PRD 第一节(一)3](../../PRD.md) 四级水位线。每个阶段（think/act/observe/reflect）执行后由 `TokenWaterGuard` 检查并触发分级压缩。

```mermaid
flowchart TD
    A[阶段执行结束<br/>think/act/observe/reflect] --> B[StateSyncer 更新<br/>tokenUsed += delta<br/>tokenWaterPercent = tokenUsed*100/maxToken]
    B --> C[计算当前水位<br/>water = tokenWaterPercent]
    C --> D{water ≤ 70%?}

    D -- 是 --> E[等级 NONE<br/>无压缩, 正常进入下一轮]
    E --> Z[返回继续 ReAct 循环]

    D -- 否 --> F{water ≤ 85%?}
    F -- 是 --> G[等级 LIGHT 轻度压缩]
    F -- 否 --> H{water ≤ 95%?}
    H -- 是 --> I[等级 MEDIUM 中度压缩]
    H -- 否 --> J[等级 HEAVY 重度压缩]

    G --> G1[memory-service.TriggerCompress<br/>level=LIGHT]
    G1 --> G2[裁剪工具返回冗余字段<br/>精简思考过程<br/>折叠闲聊类内容]
    G2 --> Y[重新加载水位<br/>reloadWater]

    I --> I1[memory-service.TriggerCompress<br/>level=MEDIUM]
    I1 --> I2[早期对话按主题摘要化<br/>裁剪召回数量<br/>归档已完成子任务详情]
    I2 --> Y

    J --> J1[memory-service.TriggerCompress<br/>level=HEAVY]
    J1 --> J2[滑动窗口保留最近 K 轮<br/>工具历史仅留结论<br/>清空非核心信息]
    J2 --> J3[StepReporter.reportTokenCritical<br/>同步预警]
    J3 --> Y

    Y --> Y1{压缩后 water ≥ 95%?}
    Y1 -- 是 --> X[抛 CircuitBreakException<br/>TOKEN_WATER_HEAVY_UNRECOVERABLE]
    X --> X1[ReportSubtaskDone FAILED<br/>reason=TOKEN_EXCEED]
    X1 --> END[退出 ReAct 循环]
    Y1 -- 否 --> Z

    Z --> Z1[StateRecovery.checkpoint<br/>写 Redis state + 续期 TTL]
    Z1 --> A2[进入下一轮 ReAct 循环]

    style E fill:#d4edda
    style G fill:#fff3cd
    style I fill:#ffe0b2
    style J fill:#f8d7da
    style X fill:#f5c6cb
    style END fill:#d9534f,color:#fff
```

**压缩等级触发动作矩阵**（[agent-runtime-engine.md §4.3](../06-agent-runtime/agent-runtime-engine.md#43-分级压缩触发)）：

| 水位区间 | 等级 | 触发动作 | 调用方 |
|---|---|---|---|
| < 70% | NONE | 无压缩 | - |
| 70%~85% | LIGHT | 裁剪冗余字段、精简思考、折叠闲聊 | memory-service（轻压缩通道） |
| 85%~95% | MEDIUM | 主题摘要、裁剪召回、归档子任务 | memory-service（中压缩通道） |
| ≥95% | HEAVY | 滑动窗口、仅留结论、清空非核心 + 预警 | memory-service（重压缩）+ 熔断预警 |

---

## 9. 异常处理流程图

对应 [PRD 第三节(二)](../../PRD.md#二分级容错机制) 异常分级与三级重试层次。

```mermaid
flowchart TD
    START[异常发生] --> CLASSIFY{异常分级判定}

    CLASSIFY -->|瞬时异常<br/>网络抖动/限流/超时<br/>retryable=true| L1_INST
    CLASSIFY -->|业务异常<br/>参数非法/权限拒绝<br/>工具不存在| L2_BIZ
    CLASSIFY -->|质量异常<br/>校验不通过/幻觉检测<br/>事实不一致| L3_QUAL
    CLASSIFY -->|致命异常<br/>路径不可行/重规划熔断<br/>成本超限| L4_FATAL

    %% ===== 瞬时异常: 指数退避重试 =====
    L1_INST --> L1_A{当前层级?}
    L1_A -->|接口级<br/>model-gateway/tool-engine| L1_B{已重试 < 3 次?}
    L1_B -- 是 --> L1_C[指数退避<br/>1s/2s/4s]
    L1_C --> L1_D[重试调用]
    L1_D --> L1_E{成功?}
    L1_E -- 是 --> OK[恢复正常流程]
    L1_E -- 否 --> L1_B
    L1_B -- 否 --> L1_F[升级到单步执行级]

    L1_A -->|单步执行级<br/>agent-runtime| L1_G{已重试 < 2 次?}
    L1_G -- 是 --> L1_H[附带错误说明重新生成<br/>ctx.retryHint = errorMsg]
    L1_H --> L1_I[回到 Think 重跑]
    L1_I --> L1_E
    L1_G -- 否 --> L1_J[升级到子任务级]

    L1_A -->|子任务级<br/>task-orchestrator| L1_K{已重试 < 2 次?}
    L1_K -- 是 --> L1_L[重置上下文重跑<br/>additionalContext=previousError+hint]
    L1_L --> L1_M[重新投递 task.subtask.execute]
    L1_M --> L1_E
    L1_K -- 否 --> L1_N[升级到重规划级]

    L1_N --> REPLAN[触发动态重规划<br/>mode=incremental]

    %% ===== 业务异常: 降级换路 =====
    L2_BIZ --> L2_A[不允许重试<br/>retryable=false]
    L2_A --> L2_B{有 fallback 路径?}
    L2_B -- 是 --> L2_C[资源级降级<br/>切换 fallback Agent / fallback tool]
    L2_C --> L2_D[功能级降级<br/>裁剪非核心步骤 + 标注降级说明]
    L2_D --> OK
    L2_B -- 否 --> L2_E[标记子任务 failed]
    L2_E --> REPLAN

    %% ===== 质量异常: 重跑修正 =====
    L3_QUAL --> L3_A{校验层级?}
    L3_A -->|一级规则硬校验失败| L3_B[格式修正 / Schema 修复]
    L3_B --> L3_C{修复成功?}
    L3_C -- 是 --> OK
    L3_C -- 否 --> L3_D[单步重跑<br/>Reflect 产出修正建议]
    L3_D --> L3_E{重跑 < 2 次?}
    L3_E -- 是 --> L3_F[回到 Think 重试<br/>携带修正建议]
    L3_F --> L3_E2{成功?}
    L3_E2 -- 是 --> OK
    L3_E2 -- 否 --> L3_E
    L3_E -- 否 --> L3_G[子任务级重跑]
    L3_G --> L3_H{重跑 < 2 次?}
    L3_H -- 是 --> L1_L
    L3_H -- 否 --> REPLAN

    L3_A -->|二级事实一致性失败| L3_I[Reflect 多轮审查<br/>最多 maxReflections 次]
    L3_I --> L3_J{verdict?}
    L3_J -->|PASS| OK
    L3_J -->|RETRY| L3_F
    L3_J -->|ESCALATE| HUMAN[请求人工介入]

    L3_A -->|三级目标完成度失败| L3_K[调用强模型综合评分]
    L3_K --> L3_L{score ≥ 阈值?}
    L3_L -- 是 --> OK
    L3_L -- 否 --> REPLAN

    %% ===== 致命异常: 回滚 + 重规划/人工 =====
    L4_FATAL --> L4_A[立即停止当前路径]
    L4_A --> L4_B{是否含写操作?}
    L4_B -- 是 --> L4_C[操作级回滚<br/>执行 tool.undo_action 补偿]
    L4_C --> L4_D[子任务级回滚<br/>逆序执行补偿动作链<br/>清理中间数据]
    L4_D --> L4_E{高风险全局写任务?}
    L4_E -- 是 --> L4_F[全局任务级回滚<br/>Seata 分布式事务协调<br/>全链路逆序回滚]
    L4_F --> L4_G
    L4_E -- 否 --> L4_G
    L4_B -- 否 --> L4_G
    L4_G{允许重规划?}
    L4_G -- 是 --> REPLAN
    L4_G -- 否 --> HUMAN

    %% ===== 重规划公共路径 =====
    REPLAN --> RP_A{熔断检查<br/>累计<4次 且 成本<30%?}
    RP_A -- 是 --> RP_B[状态转 REPLANNING]
    RP_B --> RP_C[增量重规划<br/>保留 frozenNodes]
    RP_C --> RP_D{成功?}
    RP_D -- 是 --> RP_E[状态转 SUBTASK_RUNNING]
    RP_E --> OK
    RP_D -- 否 --> RP_F{全量重规划 < 1 次?}
    RP_F -- 是 --> RP_G[全量重规划<br/>snapshot + 重新生成 DAG]
    RP_G --> RP_D
    RP_F -- 否 --> HUMAN

    %% ===== 人工介入 =====
    HUMAN --> H_A[状态转 WAITING_HUMAN]
    H_A --> H_B[SSE 通知用户<br/>含执行轨迹+失败根因+建议动作]
    H_B --> H_C{用户操作}
    H_C -->|修正结果| OK
    H_C -->|调整路径重试| OK
    H_C -->|指定重规划| RP_B
    H_C -->|终止任务| FAIL[状态转 FAILED/CANCELLED]
    H_C -->|超时未响应| FAIL

    OK --> FINAL[继续执行 / 任务完成]
    FAIL --> FINAL2[终态: FAILED/CANCELLED/TIMEOUT]

    style L1_INST fill:#d4edda
    style L2_BIZ fill:#fff3cd
    style L3_QUAL fill:#ffe0b2
    style L4_FATAL fill:#f8d7da
    style HUMAN fill:#e2d6f3
    style OK fill:#28a745,color:#fff
    style FAIL fill:#dc3545,color:#fff
```

**异常分级与处理策略汇总表**（[PRD 第三节(二)1](../../PRD.md#1-失败重试机制) + [task-orchestration-and-planning.md §10.2](../03-task-engine/task-orchestration-and-planning.md#102-三级重试层次)）：

| 分级 | 典型场景 | 准入规则 | 重试层次 | 兜底动作 |
|---|---|---|---|---|
| 瞬时异常 | 模型超时、工具网关抖动、DB 死锁 | 仅 retryable=true 允许 | 接口级 3 次 + 单步级 2 次 + 子任务级 2 次 | 重规划 |
| 业务异常 | 参数非法、权限拒绝、工具不存在 | 禁止重试 | - | 降级换路 / 重规划 |
| 质量异常 | 校验不通过、幻觉命中、事实不一致 | 单步重跑带修正 | 单步级 2 次 + 子任务级 2 次 | 重规划 / 人工介入 |
| 致命异常 | 路径不可行、重规划熔断、成本超限 | 禁止重试 | - | 回滚 + 重规划 / 人工介入 |

**配套保障**：所有写操作幂等性（`task_id + step_no` 唯一索引 + 乐观锁）、失败率过高自动熔断（Sentinel）、重试时附带错误说明避免重复犯错。

---

## 10. 记忆写入与召回时序图

对应 [PRD 第一节(一)2/3](../../PRD.md#1-三级记忆架构) 与 [memory-system-design.md](../04-memory/memory-system-design.md)。写入流程包含信息提取→标签分类→重要性评分→向量化→去重校验→入库；召回流程四路并行（向量/关键词/时间/标签）+ 综合重排序 + Top-N 返回。

### 10.1 记忆写入时序图

```mermaid
sequenceDiagram
    autonumber
    participant AR as AR<br/>(agent-runtime)
    participant MEM as MEM<br/>(memory-service)
    participant MG as MG<br/>(model-gateway)
    participant Milvus as Milvus<br/>(memory_collection)
    participant MySQL as MySQL<br/>(memory_long_term)
    participant Redis as Redis<br/>(短期记忆)

    Note over AR: 子任务执行完成 (ReportSubtaskDone 前)

    %% ===== 触发写入 =====
    rect rgb(230, 240, 255)
    Note over AR, MEM: 触发阶段
    AR->>MEM: gRPC WriteLongTerm(taskId, subtaskTrace, deliverables, sceneTags)
    Note over MEM: 也可由 TO 在任务完成后批量触发
    end

    %% ===== 信息提取 =====
    rect rgb(255, 245, 230)
    Note over MEM, MG: 1. 信息提取 (Extractor)
    MEM->>MG: Chat(scene=memory_extract, tier=light)<br/>输入: 执行轨迹 + 工具结论 + 最终结果
    MG-->>MEM: 结构化记忆片段<br/>{事实/偏好/流程/错误教训}
    end

    %% ===== 标签分类 + 重要性评分 =====
    rect rgb(230, 255, 230)
    Note over MEM: 2. 标签分类 + 3. 重要性评分
    MEM->>MEM: 自动打标签<br/>domain/scene/agent/task_type/entity_tags
    MEM->>MG: Chat(scene=memory_score, tier=light)<br/>维度: 时效性/相关性/独特性/可复用性
    MG-->>MEM: importance_score (0.0~1.0)
    MEM->>MEM: 分级: score≥0.8 热 / 0.5~0.8 温 / <0.5 冷
    end

    %% ===== 向量化 =====
    rect rgb(255, 230, 245)
    Note over MEM, MG: 4. 向量化
    MEM->>MG: Embedding(text=记忆片段, dim=1024)
    MG-->>MEM: embedding[1024]
    end

    %% ===== 去重校验 =====
    rect rgb(255, 245, 220)
    Note over MEM, Milvus: 5. 语义去重校验
    MEM->>Milvus: 向量检索 (top_k=5, score_threshold=0.92)<br/>同 tenant + 同 domain 过滤
    Milvus-->>MEM: 候选相似记忆列表
    alt 存在高相似记忆 (score≥0.92)
        MEM->>MEM: 执行 UPDATE 而非 INSERT<br/>合并/更新已有记忆
        MEM->>MySQL: UPDATE memory_long_term SET ... WHERE id=existing.id
        MEM->>Milvus: UPDATE vector (同 vector_id)
    else 无相似记忆
        MEM->>MEM: 执行 INSERT 新增
    end
    end

    %% ===== 入库 =====
    rect rgb(230, 255, 245)
    Note over MEM, MySQL: 6. 双存储入库 (Milvus + MySQL)
    MEM->>MySQL: INSERT memory_long_term<br/>(memory_id, type, content, tags, score,<br/>embedding_id, source_task_id, tenant_id, expire_at)
    MEM->>Milvus: INSERT memory_collection<br/>(vector_id=memory_id, embedding,<br/>tags, score, source_task_id, partition=domain)
    MEM->>Redis: 更新 sm:{sessionId}:ctx (短期记忆增量)
    end

    %% ===== 蒸馏与归档 (异步) =====
    rect rgb(245, 245, 220)
    Note over MEM: 异步: 记忆蒸馏与归档 (XXL-Job 定时)
    MEM->>MEM: 主题聚类 → 全局摘要 + 主题摘要 + 细节记忆 三级结构
    MEM->>MEM: 冷记忆 (score<0.5) 归档至低成本存储
    MEM->>MEM: 高频流程记忆转化为 task_template
    end

    MEM-->>AR: WriteLongTermResponse(success, memoryIds)
```

### 10.2 记忆召回时序图

```mermaid
sequenceDiagram
    autonumber
    participant AR as AR<br/>(agent-runtime)
    participant MEM as MEM<br/>(memory-service)
    participant Milvus as Milvus<br/>(memory_collection)
    participant ES as ES<br/>(关键词索引)
    participant MySQL as MySQL<br/>(memory_long_term)
    participant MG as MG<br/>(model-gateway)

    Note over AR: ReAct 循环 Think 前需召回长期记忆

    %% ===== 触发召回 =====
    rect rgb(230, 240, 255)
    Note over AR, MEM: 触发阶段
    AR->>MEM: gRPC Recall(sessionId, agentId, query=当前任务目标,<br/>sceneTags, topN=动态调整, tokenBudget)
    Note over MEM: ContextBuilder 在组装 messages 时调用
    end

    %% ===== 四路并行召回 =====
    rect rgb(255, 245, 230)
    Note over MEM, MySQL: 4 路并行召回 (CompletableFuture.allOf)
    par 向量检索 (语义相似)
        MEM->>MG: Embedding(query, dim=1024)
        MG-->>MEM: queryEmbedding
        MEM->>Milvus: 向量检索 (HNSW, top_k=30)<br/>过滤: tenant + domain + score≥阈值
        Milvus-->>MEM: 候选集 A (向量相似度排序)
    and 关键词检索 (精确匹配)
        MEM->>ES: 全文检索 (代码标识符分词优化)<br/>query + sceneTags 过滤
        ES-->>MEM: 候选集 B (BM25 相关性排序)
    and 时间权重检索 (近期优先)
        MEM->>MySQL: SELECT * FROM memory_long_term<br/>WHERE tenant_id=? AND created_at > now()-7d<br/>ORDER BY importance_score * time_weight DESC
        MySQL-->>MEM: 候选集 C (时间衰减加权)
    and 标签匹配检索 (业务域精准)
        MEM->>Milvus: 标签精确过滤 (partition_keys=sceneTags)
        Milvus-->>MEM: 候选集 D (标签命中)
    end
    end

    %% ===== 去重归一化 =====
    rect rgb(230, 255, 230)
    Note over MEM: 去重归一化
    MEM->>MEM: 合并 4 路结果 → 按 memory_id 去重
    MEM->>MEM: 归一化各路得分到 [0, 1]
    end

    %% ===== 综合重排序 =====
    rect rgb(255, 230, 245)
    Note over MEM: 综合重排序 (融合得分)
    MEM->>MEM: finalScore = 0.4*向量相似 + 0.3*关键词 + 0.2*时间权重 + 0.1*标签匹配
    MEM->>MEM: 按finalScore降序排序
    MEM->>MEM: Token 预算感知: 动态调整 Top-N<br/>(根据 tokenBudget 裁剪数量)
    MEM->>MEM: 取 Top-N (默认 5, 受 maxRecentTurns 约束)
    end

    %% ===== 返回注入上下文 =====
    rect rgb(230, 255, 245)
    Note over MEM, AR: 返回注入 Agent 上下文
    MEM-->>AR: RecallResponse(memories=[{content, tags, score, source_task_id}])
    AR->>AR: ContextBuilder 注入 messages[4]=recalled_memories
    Note over AR: 顺序: system → constraints → tools → memories → history → task
    end

    %% ===== 召回侧精准限流 =====
    rect rgb(255, 245, 220)
    Note over MEM: 召回侧限流 (PRD 一·一·2)
    MEM->>MEM: 严格控制单次召回数量 (Top-N ≤ tokenBudget/4K)
    MEM->>MEM: 相关性阈值过滤 (score < 0.5 丢弃)
    MEM->>MEM: 按上下文剩余 Token 动态调整召回配额
    end
```

**关键约束**（[PRD 第一节(一)2/3](../../PRD.md)）：
- 写入必须经过"提取→标签→评分→向量化→去重→入库"完整链路，不允许裸 INSERT。
- 召回必须四路并行 + 综合重排，禁止单路召回。
- 召回数量受 Token 预算动态调整，避免上下文膨胀。
- 高相似记忆（score≥0.92）执行 UPDATE 而非 INSERT，避免记忆膨胀。

---

## 11. 交叉引用与关联

| 本文档章节 | 关联文档 | 关联内容 |
|---|---|---|
| §1 任务状态机 | [03-task-engine §6](../03-task-engine/task-orchestration-and-planning.md#6-任务状态机) | 10 个状态定义 + 流转矩阵 |
| §1 任务状态机 | [01-database §2.6](../01-database/database-schema-design.md#26-task_state_change-状态流转审计表) | task_state_change 审计表 |
| §2 R3 审批状态机 | [05-tool-engine §4.4](../05-tool-engine/tool-and-invocation-system.md#44-r3-高危工具审批流程) | tool_approval 表与审批流程 |
| §3 完整执行全链路 | [PRD 第一节(三)](../../PRD.md#三完整执行全链路从用户输入到任务完成) | 7 步全链路定义 |
| §3 完整执行全链路 | [02-api §6](../02-api/api-specification.md) | TaskOrchestrator gRPC 契约 |
| §4 ReAct 循环 | [06-agent-runtime §2](../06-agent-runtime/agent-runtime-engine.md#2-react-推理循环核心) | 四阶段循环 + 退出条件 |
| §4 ReAct 循环 | [06-agent-runtime §6](../06-agent-runtime/agent-runtime-engine.md#6-循环熔断机制) | 7 类熔断维度 |
| §5 动态重规划 | [03-task-engine §5](../03-task-engine/task-orchestration-and-planning.md#5-动态重规划机制) | 增量 vs 全量 + 熔断 |
| §6 人工介入 | [06-agent-runtime §7](../06-agent-runtime/agent-runtime-engine.md#7-人工介入请求) | RequestHumanIntervention 流程 |
| §7 工具调用全链路 | [05-tool-engine §3](../05-tool-engine/tool-and-invocation-system.md#3-工具调用全链路标准流程) | 9 步标准流程 |
| §8 Token 水位压缩 | [PRD 第一节(一)3](../../PRD.md#3-上下文-token-阈值与压缩规则) | 四级水位线 |
| §8 Token 水位压缩 | [06-agent-runtime §4.3](../06-agent-runtime/agent-runtime-engine.md#43-分级压缩触发) | TokenWaterGuard 实现 |
| §9 异常处理 | [PRD 第三节(二)](../../PRD.md#二分级容错机制) | 异常分级 + 三级重试 |
| §9 异常处理 | [03-task-engine §10](../03-task-engine/task-orchestration-and-planning.md#10-异常处理) | 错误码清单 |
| §10 记忆写入与召回 | [PRD 第一节(一)2/3](../../PRD.md#1-三级记忆架构) | 三级记忆 + 写入/召回机制 |
| §10 记忆写入与召回 | [04-memory/memory-system-design.md](../04-memory/memory-system-design.md) | 记忆系统详设 |

---

## 12. 设计决策记录（ADR 摘要）

### ADR-F1: 时序图采用"参与者别名 + 阶段着色"而非全称

**背景**：时序图参与者众多（11 个微服务），全称导致图宽过宽难以阅读。
**决策**：采用 `U/GW/SESS/TO/PL/MEM/TE/AR/MG/QS/RC` 简短别名，配合 `rect` 阶段着色块区分 7 步全链路。
**代价**：阅读者需对照别名表，但通过统一约定降低认知负担。

### ADR-F2: 任务状态机用 `stateDiagram-v2` 而非 `flowchart`

**背景**：状态机有 10 个状态、20+ 流转，需要明确表达"状态"语义而非"流程"语义。
**决策**：使用 `stateDiagram-v2`，配合 `note right of` 标注终态与约束。
**代价**：`stateDiagram-v2` 对复合状态支持有限，但本场景 10 个扁平状态足够。

### ADR-F3: 异常处理流程图采用"分级分支 + 公共兜底"结构

**背景**：PRD 第三节异常分 4 级，但每级处理路径有交叉（如最终都可能触发重规划/人工介入）。
**决策**：按 4 级异常分支展开，公共路径（重规划 `REPLAN` / 人工介入 `HUMAN`）作为汇聚节点。
**代价**：图较复杂，但完整覆盖了所有路径。

### ADR-F4: 工具调用全链路时序图覆盖 9 步完整流程

**背景**：[tool-and-invocation-system.md §3](../05-tool-engine/tool-and-invocation-system.md#3-工具调用全链路标准流程) 定义了 9 步标准流程，需一张图完整呈现。
**决策**：使用 `rect` 着色块区分 9 个步骤，每步含简短说明。
**代价**：图较长，但便于按步骤定位问题。

---

**文档结束**。本文档定义的状态机与时序图为编码与联调的"行为契约"，所有模块实现须严格遵循本文档定义的状态流转、参与者调用关系与异常处理路径。后续若状态机或时序有变更，须同步更新本文档版本号与交叉引用。
