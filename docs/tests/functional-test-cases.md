# AgentForge 智能体平台 功能测试用例清单

> 文档版本：v1.0 | 更新日期：2026-06-27 | 文档定位：**业务功能级测试用例明细**
>
> 适用范围：AgentForge 平台 13 个微服务的业务功能与 gRPC/REST 契约测试。
>
> 依赖文档：
> - [test-strategy.md](test-strategy.md) — 测试策略与命名规范
> - [02-api/api-specification.md](../02-api/api-specification.md) — 接口契约与错误码
> - [08-flow/state-machines-and-sequences.md](../08-flow/state-machines-and-sequences.md) — 状态机与时序
> - [11-detail-flow F1~F12](../11-detail-flow/01-access-and-planning-flow.md) — 决策节点来源
>
> 用例格式：`| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |`
>
> 用例总数：65 条（覆盖 13 个业务功能域）

---

## 1. 多端接入（REST/SSE/IM Webhook）

测试目标：验证 agent-gateway 对 REST、SSE、IM Webhook、企业 API-Key 四种接入协议的标准化处理。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-ACCESS-001 | Should_AcceptRestAndReturnTask_When_HttpsPost | REST/HTTPS 请求正确解析为 Task 并转发 | gateway 已启动 | `POST /api/v1/chat` + JSON body + JWT | 响应 200，data 含 taskId，traceId 已生成 | P0 |
| FT-ACCESS-002 | Should_StreamSseResponse_When_StreamTrue | SSE 流式输出完整事件序列 | session 已建立 | `POST /api/v1/sessions/{id}/chat/stream` | 依次收到 token→tool_call→tool_result→done 事件 | P0 |
| FT-ACCESS-003 | Should_AcceptImWebhookAndAdapt_When_EnterpriseWeChat | 企业微信 IM Webhook 正确适配 | IM 路由已配置 | 企微 webhook POST（含签名） | 适配为 Task，channel=im，响应 echostr | P1 |
| FT-ACCESS-004 | Should_AuthenticateApiKey_When_EnterpriseSystem | 企业系统 API-Key 鉴权放行 | API-Key 已注册 | `X-API-Key: ak_xxx` | 映射为系统用户，放行并注入 tenantId | P0 |
| FT-ACCESS-005 | Should_RejectMissingAuth_When_NoAuthorizationHeader | 缺少 Authorization 头返回 401 | F1.D2 | 无 Authorization 头 | 返回 401 `UNAUTHENTICATED`，含 traceId | P0 |

来源：doc 11-detail-flow F1.D1~D2、doc 02-api §0.6、[plans/02-agent-gateway-session-plan.md](../plans/02-agent-gateway-session-plan.md)

---

## 2. 会话管理与多轮对话

测试目标：验证 session-service 的会话生命周期、消息历史、多轮上下文维护。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-SESS-001 | Should_CreateAndReturnSession_When_PostSessions | 创建会话返回 sessionId 与 active 状态 | agentId 已注册 | `POST /api/v1/sessions` + agentId | 响应 200，sessionId 格式 `ss_xxx`，status=active | P0 |
| FT-SESS-002 | Should_LoadHistoryByPage_When_GetMessages | 分页查询消息历史 | 已有 25 条消息 | `GET /sessions/{id}/messages?page=2&size=10` | 返回 10 条，total=25，按时间升序 | P1 |
| FT-SESS-003 | Should_MaintainContextAcrossRounds_When_MultiTurnChat | 多轮对话上下文保持 | 会话已建立 | 连续 3 轮 chat，第 2 轮引用第 1 轮内容 | 第 3 轮响应能正确引用第 1 轮信息 | P0 |
| FT-SESS-004 | Should_CloseSession_When_DeleteInvoked | 关闭会话后状态变更 | 会话存在 | `DELETE /sessions/{id}` | status=closed，closedAt 非空，后续 chat 返回 409 | P1 |
| FT-SESS-005 | Should_Return404_When_SessionNotFound | 查询不存在的会话返回 404 | 无 | `GET /sessions/ss_notexist` | 返回 404 `SESSION_NOT_FOUND` | P1 |

来源：doc 02-api §1.1、[plans/02-agent-gateway-session-plan.md](../plans/02-agent-gateway-session-plan.md) Task 9

---

## 3. 任务创建与 DAG 规划（模板/智能两种模式）

测试目标：验证 task-orchestrator + planning-service 的任务提交、复杂度评估、模板/智能两种规划模式。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-TASK-001 | Should_SubmitTaskAndReturnPending_When_PostTasks | 提交异步任务返回 PENDING 状态 | 用户已鉴权 | `POST /api/v1/tasks` + goal | 响应 200，taskId 格式 `tk_xxx`，status=PENDING | P0 |
| FT-TASK-002 | Should_AssessL1AndSkipPlanning_When_SimpleTask | L1 简单任务跳过规划直入 RUNNING | F2.D4 true 分支 | goal="今天天气如何" | complexity=L1，状态直接 RUNNING，dag_id=null | P0 |
| FT-TASK-003 | Should_AssessL3AndPlan_When_ComplexTask | L3 复杂任务触发规划生成 DAG | F2.D5 false 分支 | goal="生成行业调研报告并发邮件" | complexity=L3，状态 PLANNING，调用 planning.Plan | P0 |
| FT-TASK-004 | Should_MatchTemplateAndFillParams_When_HighFrequencyScenario | 高频场景模板化规划 | F3 模板分支 | goal="生成周报" + 场景标签 | mode=TEMPLATE，DAG 来自预置模板，仅参数填充 | P0 |
| FT-TASK-005 | Should_GenerateAiDagAndValidate_When_NoTemplateMatched | 无模板匹配进入智能规划并 5 维度自检 | F3 智能分支 | goal="个性化长尾需求" | mode=AI，调用强模型生成 DAG，执行 5 维度校验 | P0 |
| FT-TASK-006 | Should_ReturnValidationFailed_When_DagHasCycle | 智能规划生成含环 DAG 返回错误 | F3 自检失败 | 模型生成含环 DAG | 返回 `DAG_CYCLE_DETECTED` (409)，重试 2 次仍失败转 WAITING_HUMAN | P0 |
| FT-TASK-007 | Should_CancelTaskAndCleanup_When_UserCancels | 用户取消任务级联清理子任务 | 任务运行中 | `POST /tasks/{id}/cancel` | status=CANCELLED，发送 task.subtask.cancel MQ 消息 | P1 |

来源：doc 11-detail-flow F2/F3、doc 02-api §1.3、doc 08 §1

---

## 4. 子任务并行调度与失败重试

测试目标：验证 task-orchestrator 的 DAG 批次划分、并行调度、失败重试与重规划触发。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-ORCH-001 | Should_PartitionBatchesAndDispatchParallel_When_DagHasNoDeps | 同层无依赖节点并行调度 | DAG 已落库 | 3 个根节点无依赖 | 3 个子任务同时投递 RocketMQ，批次 1 并行 | P0 |
| FT-ORCH-002 | Should_SequentialExecute_When_NodesHaveDependency | 有依赖节点串行执行 | DAG 含 A→B 依赖 | A 完成后 B 才投递 | B 在 A success 后才进入批次 | P0 |
| FT-ORCH-003 | Should_RetrySubtaskWithBackoff_When_TransientError | 瞬时异常指数退避重试 | 子任务级 maxRetry=2 | 子任务首次失败（瞬时） | 重试最多 2 次，退避 1s/2s，成功则继续 | P0 |
| FT-ORCH-004 | Should_NotRetry_When_BusinessError | 业务异常不重试直接降级 | F4.D5 | 子任务返回业务错误（权限拒绝） | 不重试，标记 FAILED，触发降级或重规划 | P0 |
| FT-ORCH-005 | Should_AggregateResults_When_AllSubtasksDone | 全部子任务完成后结果聚合 | 全部 success | 5 个子任务全完成 | 调用 `ResultAggregator.aggregate()`，结果落盘，状态转 SUCCESS | P0 |

来源：doc 11-detail-flow F4.D1~D8、PRD §三(二)1

---

## 5. 工具调用全链路（注册→召回→校验→执行→容错）

测试目标：验证 tool-engine 从工具注册到调用执行的 9 步全链路，覆盖 R1/R2/R3 风险分级。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-TOOL-001 | Should_RecallAndSelectTool_When_AgentRequests | 工具语义召回并选 Top-1 | 已注册 10 个工具 | query="查询订单状态" | 返回最相关工具，score 降序 | P0 |
| FT-TOOL-002 | Should_PreCheckParamsAndRoute_When_R1ToolInvoked | R1 工具前置校验通过后通用执行器执行 | F8 R1 分支 | R1 工具 + 合法参数 | 校验通过，executor_type=general 执行，返回结果 | P0 |
| FT-TOOL-003 | Should_RequireApprovalAndSandbox_When_R3ToolInvoked | R3 工具强制审批 + 沙箱执行 | F8 R3 分支 | R3 工具 + 有效审批 | 审批校验通过，executor_type=sandbox 执行，容器用后即删 | P0 |
| FT-TOOL-004 | Should_CleanAndLimitResult_When_ToolReturnsLarge | 工具返回超长结果清洗与 Token 限流 | 工具返回 5000 字符 | ResultCleaner maxToken=2000 | 裁剪/摘要后 ≤2000 Token，保留核心信息 | P1 |
| FT-TOOL-005 | Should_FallbackToAlternativeTool_When_PrimaryFails | 主工具失败切换同功能备用工具 | F8 容错 | 主工具调用失败 | 切换同功能低成本工具，重试成功 | P1 |
| FT-TOOL-006 | Should_EnforceMaxCallCount_When_LimitExceeded | 单任务工具调用次数熔断 | max_calls=20 | 第 21 次调用 | 抛 `COST_BUDGET_EXCEEDED`，终止工具调用 | P0 |
| FT-TOOL-007 | Should_WriteAuditLog_When_EveryToolCall | 每次工具调用落审计日志 | 调用完成 | 成功/失败的调用 | `tool_call_log` 表有完整记录（traceId/toolId/input/output/status/duration） | P0 |

来源：doc 11-detail-flow F8、PRD §二(二)2、doc 08 §2

---

## 6. 三级记忆（写入/召回/压缩/蒸馏）

测试目标：验证 memory-service 的三级记忆架构、多路召回、Token 压缩、记忆蒸馏功能。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-MEM-001 | Should_WriteLongTermWithImportance_When_TaskCompleted | 任务完成触发长期记忆写入 | 任务 SUCCESS | 任务上下文 + 结果 | 记忆写入 Milvus + MySQL，含重要性评分、向量化、去重校验 | P0 |
| FT-MEM-002 | Should_RecallAndRerankTopN_When_ContextLoading | 上下文加载时多路召回重排序 | 长期记忆已存在 | 召回 query | 4 路召回（语义+关键词+时间+标签）融合重排，返回 Top-N | P0 |
| FT-MEM-003 | Should_CompressLight_When_TokenWarnLevel | Token 预警水位触发轻度压缩 | F7 预警 | token_used=75% | 裁剪冗余字段、精简思考过程，压缩后 < 70% | P0 |
| FT-MEM-004 | Should_CompressHeavy_When_TokenCircuitBreak | Token 熔断水位触发重度压缩 | F7 熔断 | token_used=96% | 滑动窗口保留最近 K 轮，工具历史仅保留结论 | P0 |
| FT-MEM-005 | Should_DistillTopicMemories_When_FragmentsAccumulated | 同主题碎片记忆蒸馏浓缩 | 同主题 ≥5 条 | 触发蒸馏任务 | 生成全局摘要-主题摘要-细节三级结构，压缩比 > 50% | P1 |
| FT-MEM-006 | Should_UpdateNotInsert_When_HighSimilarityMemory | 高相似记忆执行更新合并 | 已有相似记忆 | 新记忆 cosine_sim=0.92 | 触发更新合并，不新增重复记录 | P1 |
| FT-MEM-007 | Should_ExpireAndArchive_When_ColdMemoryTtlReached | 冷记忆 TTL 到期归档 | TTL=30 天 | 记忆超期 | 标记 COLD，迁移至归档存储，热记忆不受影响 | P2 |

来源：doc 11-detail-flow F7/F12、PRD §二(一)

---

## 7. ReAct 循环与 Reflexion 反思

测试目标：验证 agent-runtime 的 ReAct Think-Act-Observe 循环与 Reflexion 反思重试机制。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-REACT-001 | Should_CompleteThinkActObserveLoop_When_SimpleTask | ReAct 循环完成简单任务 | Agent 实例已启动 | 单步任务上下文 | Think→Act→Observe→Finish 全流程，产出最终答案 | P0 |
| FT-REACT-002 | Should_LoopMultipleTimes_When_MultiStepTask | 多步任务多轮 ReAct 循环 | F6 循环 | 需要 3 次工具调用的任务 | 循环 3 次 Think-Act-Observe，每次工具结果注入上下文 | P0 |
| FT-REACT-003 | Should_TriggerReflexion_When_L4Rejects | L4 校验失败触发 Reflexion 重试 | F9.D5 true | L4 返回 AUDIT_REJECTED | 注入 REFLECTION 提示，retry_count+1，重新生成 | P0 |
| FT-REACT-004 | Should_MaxRetryAndEscalate_When_ReflexionExhausted | Reflexion 重试耗尽转人工 | F9.D6 false | retry_count=3 | 抛 `MAX_RETRY_EXCEEDED`，写 Badcase，转人工审核队列 | P0 |
| FT-REACT-005 | Should_BreakCircuit_When_LoopExceedsMax | 循环次数超限熔断 | max_loops=10 | loop_count=11 | 抛 `CIRCUIT_OPEN`，子任务失败，状态转 WAITING_HUMAN | P0 |

来源：doc 11-detail-flow F6/F9、PRD §二(五)

---

## 8. Token 水位压缩

测试目标：验证 agent-runtime + memory-service 的四级 Token 水位压缩机制。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-TOKEN-001 | Should_StaySafeNoCompress_When_UsageLe70 | 安全水位不压缩 | F7 安全 | token_used=65% | 不触发压缩，正常运行 | P0 |
| FT-TOKEN-002 | Should_TriggerLightCompress_When_Usage70To85 | 预警水位轻度压缩 | F7 预警 | token_used=78% | 裁剪冗余、精简思考、折叠闲聊，压缩后 < 70% | P0 |
| FT-TOKEN-003 | Should_TriggerMediumCompress_When_Usage85To95 | 临界水位中度压缩 | F7 临界 | token_used=88% | 早期对话摘要化、裁剪召回数量、归档已完成子任务 | P0 |
| FT-TOKEN-004 | Should_TriggerHeavyCompress_When_UsageGe95 | 熔断水位重度压缩 | F7 熔断 | token_used=97% | 滑动窗口保留最近 K 轮，工具历史仅保留结论 | P0 |
| FT-TOKEN-005 | Should_KeepAuditTrail_When_EveryCompress | 所有压缩操作留痕可回溯 | 压缩执行后 | 任意压缩 | 压缩日志记录前后 Token 数、裁剪内容、时间戳 | P1 |

来源：doc 11-detail-flow F7、PRD §二(一)3

---

## 9. 动态重规划（增量/全量）

测试目标：验证 task-orchestrator + planning-service 的动态重规划机制，覆盖 F5 决策节点。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-REPLAN-001 | Should_TriggerIncrementalReplan_When_SingleSubtaskFails | 单子任务失败触发增量重规划 | F5.D3 true | failed_count=1, others valid | 模式=INCREMENTAL，保留成功节点，仅调整后续路径 | P0 |
| FT-REPLAN-002 | Should_TriggerFullReplan_When_RequirementChanged | 需求变更触发全量重规划 | F5.D2 true | reason=requirement_change | 模式=FULL，快照旧 DAG，调用 planning.Plan 重新生成 | P0 |
| FT-REPLAN-003 | Should_MergePatchDag_When_IncrementalReplan | 增量重规划合并补丁 DAG | 增量分支 | 失败节点 + 邻域 + 补丁 DAG | DagMerger.merge 保留 success 节点，合并补丁无环 | P0 |
| FT-REPLAN-004 | Should_BumpVersionAndLog_When_ReplanCompleted | 重规划完成后版本递增与日志 | 重规划完成 | 新 DAG 已生成 | dag_id 不变 version+1，旧版本标记 superseded，写 task_replan_log | P1 |
| FT-REPLAN-005 | Should_TransitToWaitingHuman_When_ReplanExhausted | 重规划次数超限转人工 | F5.D4 true | replan_count=3 > max=2 | 抛 `REPLAN_EXHAUSTED`，转 WAITING_HUMAN | P0 |
| FT-REPLAN-006 | Should_DetectCycleAndReject_When_MergedDagHasCycle | 合并后 DAG 含环返回错误 | F5.I5 true | 合并后检测到环 | 返回 `DAG_CYCLE_DETECTED`，转人工或全量重规划 | P0 |

来源：doc 11-detail-flow F5.D1~D5、PRD §二(三)2

---

## 10. L4 三级质量校验

测试目标：验证 quality-service 的 L4-1/L4-2/L4-3 三级校验与打回重试机制，覆盖 F9 决策节点。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-L4-001 | Should_PassAllThreeLevels_When_HighQualityOutput | 高质量输出通过全三级校验 | risk=high | 输出含来源、事实一致、overall=0.9 | L4-1 pass → L4-2 pass → L4-3 pass，结果落盘 | P0 |
| FT-L4-002 | Should_RejectAtL4Hard_When_SourceTagMissing | L4-1 缺来源标签返回 `CODE_FORMAT_VIOLATION` | F9.D2 false | 输出无 `[来源:...]` | L4-1 失败，触发 Reflexion 重试 | P0 |
| FT-L4-003 | Should_RejectAtL4Consistency_When_FactMismatch | L4-2 事实不一致返回 `FACT_INCONSISTENCY` | F9.D3 false | sim=0.60 < 0.75 | L4-2 失败，触发 Reflexion 重试 | P0 |
| FT-L4-004 | Should_RejectAtL4Audit_When_OverallScoreLow | L4-3 终审驳回返回 `AUDIT_REJECTED` | F9.D4 false | overall=0.65 < 0.7 | L4-3 失败，转人工审核 | P0 |
| FT-L4-005 | Should_SkipL4ConsistencyAndAudit_When_LowRiskChitchat | 低风险闲聊仅执行 L4-1 | F9.D1 low | risk=low, chitchat | 仅 L4-1，跳过 L4-2/L4-3，节省成本 | P1 |
| FT-L4-006 | Should_SampleL4Audit_When_MidRiskTask | 中风险任务 L4-3 抽样执行 | F9.D1 mid | risk=mid | L4-1+L4-2 必执行，L4-3 按采样比例执行 | P1 |

来源：doc 11-detail-flow F9.D1~D6、PRD §三(一)2

---

## 11. 幻觉六层治理

测试目标：验证幻觉治理六层架构的层间联动与失败短路，覆盖 F10 决策节点。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-HALU-001 | Should_RouteToStrongModelLowTemp_When_HighRiskScene | 第一层：高风险路由强模型低温度 | F10 L1 | scene=high_risk | 模型 tier=strong，temperature=0.1 | P0 |
| FT-HALU-002 | Should_TriggerSelfCheckAndRefuse_When_InsufficientInfo | 第二层：信息不足主动拒答 | F10 L2 | 召回为空 | 返回"信息不足"，不编造，不继续 | P0 |
| FT-HALU-003 | Should_AnchorByRagAndRequireSource_When_FactualTask | 第三层：RAG 事实约束强制来源标注 | F10 L3 | factual task | 强制召回知识库，输出绑定溯源 ID | P0 |
| FT-HALU-004 | Should_BlockInvalidToolCall_When_ParamsSchemaMismatch | 第五层：工具网关前置拦截 | F10 L5 | 工具参数非法 | `ToolGateway` 拦截，返回 `VALIDATION_FAILED` | P0 |
| FT-HALU-005 | Should_CollectBadcaseAndIterate_When_HallucinationDetected | 第六层：Badcase 归集与闭环优化 | F10 L6 | 检测到幻觉 | 写入 badcase 表，触发提示词/规则/知识库迭代 | P1 |
| FT-HALU-006 | Should_ApplyFullSixLayers_When_FinancialScenario | 金融高风险场景全六级治理 + 人工终审 | PRD §四(三) | scene=finance | 六层全执行，最终人工终审 | P0 |

来源：doc 11-detail-flow F10、PRD §四(二)/(三)

---

## 12. 漂移监测与纠偏

测试目标：验证漂移四层管控闭环，覆盖 F11 决策节点。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-DRIFT-001 | Should_AnchorBaselines_When_FirstRun | 第一层：首次运行锚定三类基准 | 黄金基准集 | 首次任务执行 | 写入 eval_baseline（行为/效果/对齐基准） | P0 |
| FT-DRIFT-002 | Should_DetectBehaviorDrift_When_ToolCallRateAnomaly | 第二层：行为漂移检测 | F11 | 连续 3 次工具调用率上升 >20% | 返回 `BEHAVIOR_DRIFT`，记录指标 | P0 |
| FT-DRIFT-003 | Should_InjectConstraint_When_SessionLevelDrift | 第三层：会话级轻度漂移注入约束 | F11 会话级 | drift_level=session | 注入核心约束摘要，抵消上下文稀释 | P0 |
| FT-DRIFT-004 | Should_RollbackVersion_When_SystemLevelDrift | 第三层：系统级重度漂移自动回滚 | F11 系统级 | drift_level=system | 自动回滚至上一稳定版本，切换备用模型 | P0 |
| FT-DRIFT-005 | Should_PauseAgentAndAlert_When_DriftExceedsThreshold | 漂移超阈值自动暂停并告警 | F11 | drift_score > 0.8 | Agent 状态变 paused，触发告警通知 | P0 |

来源：doc 11-detail-flow F11、PRD §五(二)

---

## 13. Agent 版本管理与灰度发布

测试目标：验证 agent-repo 的 Agent 版本化、灰度发布、回滚能力。

| 用例 ID | 用例名 | 测试目标 | 前置条件 | 输入 | 期望输出 | 优先级 |
|---|---|---|---|---|---|---|
| FT-VER-001 | Should_CreateNewVersion_When_AgentUpdated | Agent 配置变更创建新版本 | agent 已存在 v1 | 更新 prompt 并发布 | 创建 v2，v1 标记 superseded 但保留可回滚 | P0 |
| FT-VER-002 | Should_GrayscaleRelease_When_CanaryPercentageSet | 灰度发布按比例分流 | v2 灰度 10% | 流量按比例分流 | 10% 流量走 v2，90% 走 v1，AB 对比无显著退化 | P0 |
| FT-VER-003 | Should_RollbackToPreviousVersion_When_CanaryFails | 灰度失败自动回滚到上一稳定版本 | v2 灰度退化 | v2 效果显著下降 | 自动回滚至 v1，v2 标记 failed，全量切回 v1 | P0 |
| FT-VER-004 | Should_PromoteToFull_When_CanaryStable | 灰度稳定后全量发布 | v2 灰度 7 天无退化 | 灰度期满 | v2 全量发布，v1 归档 | P1 |
| FT-VER-005 | Should_KeepAllVersionsTraceable_When_VersionHistoryQueried | 版本历史可追溯 | 多版本存在 | `GET /agents/{id}/versions` | 返回所有版本列表，含变更记录、发布时间、状态 | P1 |

来源：PRD §五(二)4、doc 01-database §6 agent_version 表

---

## 14. 用例统计汇总

| 功能域 | 用例数 | P0 | P1 | P2 |
|---|---|---|---|---|
| 多端接入 | 5 | 4 | 1 | 0 |
| 会话管理 | 5 | 1 | 3 | 1 |
| 任务创建与 DAG 规划 | 7 | 6 | 1 | 0 |
| 子任务并行调度 | 5 | 5 | 0 | 0 |
| 工具调用全链路 | 7 | 4 | 2 | 1 |
| 三级记忆 | 7 | 3 | 3 | 1 |
| ReAct 与 Reflexion | 5 | 5 | 0 | 0 |
| Token 水位压缩 | 5 | 4 | 1 | 0 |
| 动态重规划 | 6 | 5 | 1 | 0 |
| L4 三级质量校验 | 6 | 4 | 2 | 0 |
| 幻觉六层治理 | 6 | 5 | 1 | 0 |
| 漂移监测与纠偏 | 5 | 4 | 1 | 0 |
| Agent 版本管理 | 5 | 3 | 2 | 0 |
| **合计** | **74** | **53** | **18** | **3** |

---

## 15. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|---|---|---|---|
| v1.0 | 2026-06-27 | 初始版本，覆盖 13 个功能域共 74 条功能测试用例 | AgentForge 测试团队 |
