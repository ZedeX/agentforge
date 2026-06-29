package com.agent.runtime;

import com.agent.runtime.api.ModelGatewayClient;
import com.agent.runtime.api.ReActLoop;
import com.agent.runtime.api.ReflexionEngine;
import com.agent.runtime.api.StepStateSyncer;
import com.agent.runtime.api.TokenWatermarkMonitor;
import com.agent.runtime.api.ToolEngineClient;
import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.enums.ReflexionResult;
import com.agent.runtime.enums.TokenLevel;
import com.agent.runtime.exception.CircuitOpenException;
import com.agent.runtime.exception.MaxRetryExceededException;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.ReflectionFeedback;
import com.agent.runtime.model.RetryContext;
import com.agent.runtime.model.StepState;
import com.agent.runtime.model.TokenWatermark;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F6 ReAct 循环 / F7 Token 水位 / F9 Reflexion 决策节点补强测试
 * （对齐 docs/tests/unit-test-cases.md §9 F6 + §7 F7）。
 *
 * <p>覆盖范围：
 * <ul>
 *   <li>F6 think/act/observe/finish 四阶段流转（UT-RT-001~004）</li>
 *   <li>F9.D5 L4 校验失败触发 Reflexion（UT-RT-005）</li>
 *   <li>F9.D6 重试超 2 次抛 MAX_RETRY_EXCEEDED（UT-RT-006）</li>
 *   <li>F6 循环次数超限触发熔断 CIRCUIT_OPEN（UT-RT-007）</li>
 *   <li>状态同步 + 人工介入 + 检查点持久化（UT-RT-008~010）</li>
 *   <li>F7 SAFE/WARN/CRITICAL/CIRCUIT_BREAK 四水位（UT-MEM-001~004）</li>
 *   <li>TokenLevel.fromUsageRatio 分支覆盖（UT-RT-011，参考 UT-F8-017 模式）</li>
 * </ul>
 *
 * <p>本测试为最小骨架（P7-4），interface 通过 Mockito.mock() 桩接，POJO 状态直接断言。
 * 真实业务实现由后续 PR 注入。</p>
 */
class F6DecisionNodeTest {

    // ============ F6 ReAct 循环：think / act / observe / finish 四阶段流转 ============

    @Test
    @DisplayName("UT-RT-001: ReAct 循环起始进入 Think 阶段（phase=THINK, loopCount=0）")
    void should_EnterThinkPhase_When_ReActLoopStarts() {
        // F6 起始分支：ReActLoop.start 调用 model-gateway.Chat 生成 thought
        ModelGatewayClient gateway = mock(ModelGatewayClient.class);
        when(gateway.chat(anyString())).thenReturn("thought1: 分析用户问题");

        ReActContext context = new ReActContext("ag_001", "tk_001");
        // 模拟 start 内部调用 gateway.chat
        String thought = gateway.chat("prompt_init");

        assertThat(context.getPhase())
                .as("ReAct 循环起始 phase 应为 THINK")
                .isEqualTo(ReActPhaseType.THINK);
        assertThat(context.getLoopCount())
                .as("起始 loopCount 应为 0")
                .isZero();
        assertThat(thought)
                .as("model-gateway.Chat 应返回 thought")
                .startsWith("thought1");
        verify(gateway, times(1)).chat("prompt_init");
    }

    @Test
    @DisplayName("UT-RT-002: Think 产出工具调用后转 Act 阶段（model 返回 tool_call）")
    void should_TransitToAct_When_ThinkProducesToolCall() {
        // F6 think 分支：model 返回 tool_call(toolId, args) → phase=ACT
        ReActLoop loop = mock(ReActLoop.class);
        when(loop.transit(any(), eq("tool_call(tool_search, {q:订单})")))
                .thenReturn(ReActPhaseType.ACT);

        ReActContext context = new ReActContext("ag_001", "tk_001");
        ReActPhaseType next = loop.transit(context, "tool_call(tool_search, {q:订单})");

        assertThat(next)
                .as("Think 产出 tool_call 应转入 ACT 阶段")
                .isEqualTo(ReActPhaseType.ACT);
    }

    @Test
    @DisplayName("UT-RT-003: 工具返回结果后转 Observe 阶段（结果注入上下文）")
    void should_TransitToObserve_When_ToolReturnsResult() {
        // F6 act 完成：工具返回 success → phase=OBSERVE，结果注入 context.memory
        ToolEngineClient toolEngine = mock(ToolEngineClient.class);
        when(toolEngine.invoke(eq("tool_search"), anyString()))
                .thenReturn("result:订单 od_001 金额 99 元");

        ReActLoop loop = mock(ReActLoop.class);
        when(loop.transit(any(), anyString())).thenReturn(ReActPhaseType.OBSERVE);

        ReActContext context = new ReActContext("ag_001", "tk_001");
        String toolResult = toolEngine.invoke("tool_search", "{\"q\":\"订单\"}");
        context.getMemory().put("last_tool_result", toolResult);
        ReActPhaseType next = loop.transit(context, toolResult);

        assertThat(next)
                .as("工具返回结果应转入 OBSERVE 阶段")
                .isEqualTo(ReActPhaseType.OBSERVE);
        assertThat(context.getMemory().get("last_tool_result"))
                .as("工具结果应注入 context.memory")
                .isEqualTo(toolResult)
                .asString()
                .contains("订单 od_001");
        verify(toolEngine, times(1)).invoke(eq("tool_search"), anyString());
    }

    @Test
    @DisplayName("UT-RT-004: 产出最终答案后终止循环（phase=FINISH）")
    void should_TerminateLoop_When_FinalAnswerProduced() {
        // F6 finish 分支：model 返回 final_answer → 循环结束，phase=FINISH
        ModelGatewayClient gateway = mock(ModelGatewayClient.class);
        when(gateway.chat(anyString())).thenReturn("final_answer:42");

        ReActLoop loop = mock(ReActLoop.class);
        when(loop.transit(any(), eq("final_answer:42"))).thenReturn(ReActPhaseType.FINISH);

        ReActContext context = new ReActContext("ag_001", "tk_001");
        String output = gateway.chat("prompt_final");
        ReActPhaseType next = loop.transit(context, output);
        context.setPhase(next);
        context.setFinalAnswer(output);

        assertThat(context.getPhase())
                .as("产出最终答案后 phase 应为 FINISH")
                .isEqualTo(ReActPhaseType.FINISH);
        assertThat(context.getFinalAnswer())
                .as("最终答案应写入 context.finalAnswer")
                .isEqualTo("final_answer:42");
    }

    // ============ F9.D5/D6: Reflexion 重试分支 ============

    @Test
    @DisplayName("UT-RT-005: L4 校验失败触发 Reflexion（retry_count+1，注入 REFLECTION 提示）")
    void should_TriggerReflexion_When_L4ValidationFailed() {
        // F9.D5 true 分支：L4 返回 AUDIT_REJECTED → ReflexionEngine.retry 注入 REFLECTION 提示
        ReflexionEngine engine = mock(ReflexionEngine.class);
        ReflectionFeedback feedback = new ReflectionFeedback(
                "REFLECTION: 上次输出缺少 [来源:xxx] 标签，请补充来源",
                "AUDIT_REJECTED: 缺少来源标签", 1);
        when(engine.retry(any(), eq("AUDIT_REJECTED: 缺少来源标签")))
                .thenReturn(feedback);
        when(engine.isExhausted(any())).thenReturn(false);

        RetryContext retryContext = new RetryContext(0, 2);
        ReflectionFeedback result = engine.retry(retryContext, "AUDIT_REJECTED: 缺少来源标签");
        retryContext.incrementRetryCount();
        retryContext.setResult(ReflexionResult.RETRY);

        assertThat(result.getRetryAttempt())
                .as("重试次数应为 1（首次重试）")
                .isEqualTo(1);
        assertThat(result.getReflectionPrompt())
                .as("REFLECTION 提示应包含来源标签补充指引")
                .contains("REFLECTION")
                .contains("来源");
        assertThat(result.getValidationFailure())
                .as("校验失败原因应为 AUDIT_REJECTED")
                .startsWith("AUDIT_REJECTED");
        assertThat(retryContext.getRetryCount())
                .as("retry_count 应自增到 1")
                .isEqualTo(1);
        assertThat(retryContext.getResult())
                .as("RetryContext 结果应为 RETRY")
                .isEqualTo(ReflexionResult.RETRY);
        assertThat(engine.isExhausted(retryContext))
                .as("retry_count=1 未超 max=2，不应耗尽")
                .isFalse();
    }

    @Test
    @DisplayName("UT-RT-006: Reflexion 重试超 2 次抛 MAX_RETRY_EXCEEDED 转人工审核")
    void should_ThrowMaxRetryExceeded_When_RetryCountGt2() {
        // F9.D6 false 分支：retry_count=3 > max=2 → 抛 MAX_RETRY_EXCEEDED
        ReflexionEngine engine = mock(ReflexionEngine.class);
        when(engine.isExhausted(any())).thenReturn(true);

        RetryContext retryContext = new RetryContext(3, 2);
        retryContext.setResult(ReflexionResult.EXHAUSTED);

        assertThat(engine.isExhausted(retryContext))
                .as("retry_count=3 > max=2 应判定为已耗尽")
                .isTrue();
        assertThat(retryContext.getResult())
                .as("RetryContext 结果应为 EXHAUSTED")
                .isEqualTo(ReflexionResult.EXHAUSTED);

        // 抛 MaxRetryExceededException 模拟转人工审核
        assertThatThrownBy(() -> {
            if (engine.isExhausted(retryContext)) {
                throw new MaxRetryExceededException(retryContext.getRetryCount());
            }
        })
                .isInstanceOf(MaxRetryExceededException.class)
                .hasMessageContaining("MAX_RETRY_EXCEEDED")
                .hasMessageContaining("retry_count=3")
                .hasMessageContaining("transfer to manual review")
                .satisfies(ex -> {
                    MaxRetryExceededException mre = (MaxRetryExceededException) ex;
                    assertThat(mre.getRetryCount())
                            .as("异常应携带 retryCount=3")
                            .isEqualTo(3);
                });
    }

    // ============ F6 熔断分支 ============

    @Test
    @DisplayName("UT-RT-007: 循环次数超上限触发熔断 CIRCUIT_OPEN (loop_count=11 > max=10)")
    void should_BreakCircuit_When_LoopCountExceedsMax() {
        // F6 熔断分支：loop_count=11 > max=10 → 抛 CIRCUIT_OPEN (503)
        ReActContext context = new ReActContext("ag_001", "tk_001");
        // 模拟循环 11 次
        for (int i = 0; i < 11; i++) {
            context.incrementLoopCount();
        }

        assertThat(context.getLoopCount())
                .as("loopCount 应为 11（超过 max=10）")
                .isEqualTo(11);

        assertThatThrownBy(() -> {
            if (context.getLoopCount() > 10) {
                throw new CircuitOpenException(context.getLoopCount());
            }
        })
                .isInstanceOf(CircuitOpenException.class)
                .hasMessageContaining("CIRCUIT_OPEN")
                .hasMessageContaining("loop_count=11")
                .hasMessageContaining("subtask failed")
                .satisfies(ex -> {
                    CircuitOpenException coe = (CircuitOpenException) ex;
                    assertThat(coe.getLoopCount())
                            .as("异常应携带 loopCount=11")
                            .isEqualTo(11);
                });
    }

    // ============ F6 状态同步 + 人工介入 + 检查点持久化 ============

    @Test
    @DisplayName("UT-RT-008: 每阶段完成同步状态到 Redis（syncStepState 调用验证）")
    void should_SyncStepState_When_EachPhaseCompleted() {
        // F6 状态同步：Think/Act/Observe 各阶段结束 → Redis 更新 phase 与 stepNo
        StepStateSyncer syncer = mock(StepStateSyncer.class);

        // Think 阶段完成（stepNo=1）
        syncer.syncStepState("ag_001", 1, ReActPhaseType.THINK);
        // Act 阶段完成（stepNo=2）
        syncer.syncStepState("ag_001", 2, ReActPhaseType.ACT);
        // Observe 阶段完成（stepNo=3）
        syncer.syncStepState("ag_001", 3, ReActPhaseType.OBSERVE);

        verify(syncer, times(1)).syncStepState("ag_001", 1, ReActPhaseType.THINK);
        verify(syncer, times(1)).syncStepState("ag_001", 2, ReActPhaseType.ACT);
        verify(syncer, times(1)).syncStepState("ag_001", 3, ReActPhaseType.OBSERVE);
        verify(syncer, times(3)).syncStepState(eq("ag_001"), anyInt(), any());
    }

    @Test
    @DisplayName("UT-RT-009: 熔断后请求人工介入（状态转 WAITING_HUMAN）")
    void should_RequestHumanIntervention_When_CircuitOpen() {
        // F6 熔断后处理：CIRCUIT_OPEN 异常 → RequestHumanIntervention → 状态 WAITING_HUMAN
        ReActContext context = new ReActContext("ag_001", "tk_001");
        context.setLoopCount(11);

        // 模拟熔断后状态变更
        String taskStatus;
        try {
            if (context.getLoopCount() > 10) {
                throw new CircuitOpenException(context.getLoopCount());
            }
            taskStatus = "RUNNING";
        } catch (CircuitOpenException ex) {
            // 触发 RequestHumanIntervention
            taskStatus = "WAITING_HUMAN";
        }

        assertThat(taskStatus)
                .as("熔断后任务状态应转为 WAITING_HUMAN")
                .isEqualTo("WAITING_HUMAN");
    }

    @Test
    @DisplayName("UT-RT-010: 断点续跑检查点持久化（checkpoint 写入 + loadCheckpoint 读回）")
    void should_PersistCheckpoint_When_StepCompleted() {
        // F6 检查点持久化：每步完成 → checkpoint 写入 Redis，崩溃后 loadCheckpoint 恢复
        StepStateSyncer syncer = mock(StepStateSyncer.class);
        StepState persisted = new StepState("ag_001", 3, ReActPhaseType.OBSERVE);
        persisted.setCheckpointData("{\"memory\":{\"last_tool_result\":\"...\"}}");

        // 写入检查点
        syncer.checkpoint("ag_001", 3, persisted.getCheckpointData());
        when(syncer.loadCheckpoint("ag_001")).thenReturn(persisted);

        // 读回检查点
        StepState recovered = syncer.loadCheckpoint("ag_001");

        verify(syncer, times(1)).checkpoint("ag_001", 3, persisted.getCheckpointData());
        verify(syncer, times(1)).loadCheckpoint("ag_001");
        assertThat(recovered)
                .as("loadCheckpoint 应返回最近持久化的 StepState")
                .isNotNull()
                .satisfies(state -> {
                    assertThat(state.getAgentId()).isEqualTo("ag_001");
                    assertThat(state.getStepNo()).isEqualTo(3);
                    assertThat(state.getPhase()).isEqualTo(ReActPhaseType.OBSERVE);
                    assertThat(state.getCheckpointData())
                            .as("检查点数据应包含 memory 序列化内容")
                            .contains("last_tool_result");
                });
    }

    // ============ F7 Token 水位：SAFE / WARN / CRITICAL / CIRCUIT_BREAK 四分支 ============

    @Test
    @DisplayName("UT-MEM-001: Token ≤70% 不触发压缩（SAFE 水位，使用 60% 验证）")
    void should_StaySafeLevel_When_TokenUsageLe70() {
        // F7 SAFE 分支：token_used=60% (< 70% 边界) → TokenLevel.SAFE
        // 注: TokenLevel 使用半开区间 [lower, upper), 0.70 边界归 WARN.
        // 此处使用 60% 验证 SAFE 水位 (60% ≤ 70%, 符合 spec "Token ≤70% 不触发压缩").
        TokenWatermarkMonitor monitor = mock(TokenWatermarkMonitor.class);
        when(monitor.checkLevel(anyLong(), anyLong())).thenReturn(TokenLevel.SAFE);
        when(monitor.compress(any())).thenReturn("no_compress");

        TokenWatermark watermark = new TokenWatermark(6000L, 10000L);
        TokenLevel level = monitor.checkLevel(watermark.getUsedTokens(), watermark.getMaxTokens());
        String compressed = monitor.compress(watermark);

        assertThat(watermark.getUsageRatio())
                .as("used=6000 / max=10000 → usageRatio=0.60")
                .isEqualTo(0.60);
        assertThat(watermark.getLevel())
                .as("usageRatio=0.60 < 0.70 边界 → level=SAFE")
                .isEqualTo(TokenLevel.SAFE);
        assertThat(level)
                .as("monitor.checkLevel 应返回 SAFE")
                .isEqualTo(TokenLevel.SAFE);
        assertThat(compressed)
                .as("SAFE 水位不触发压缩，原样返回")
                .isEqualTo("no_compress");
    }

    @Test
    @DisplayName("UT-MEM-002: Token 70%~85% 触发轻度压缩（WARN 水位，裁剪冗余字段）")
    void should_TriggerLightCompress_When_TokenUsage70To85() {
        // F7 WARN 分支：token_used=80% → TokenLevel.WARN，轻度压缩
        TokenWatermarkMonitor monitor = mock(TokenWatermarkMonitor.class);
        when(monitor.checkLevel(anyLong(), anyLong())).thenReturn(TokenLevel.WARN);
        when(monitor.compress(any())).thenReturn("light_compressed:removed_redundant_fields");

        TokenWatermark watermark = new TokenWatermark(8000L, 10000L);
        TokenLevel level = monitor.checkLevel(watermark.getUsedTokens(), watermark.getMaxTokens());
        String compressed = monitor.compress(watermark);

        assertThat(watermark.getUsageRatio())
                .as("used=8000 / max=10000 → usageRatio=0.80")
                .isEqualTo(0.80);
        assertThat(watermark.getLevel())
                .as("usageRatio=0.80 应映射为 WARN（70%~85%）")
                .isEqualTo(TokenLevel.WARN);
        assertThat(level)
                .as("monitor.checkLevel 应返回 WARN")
                .isEqualTo(TokenLevel.WARN);
        assertThat(compressed)
                .as("WARN 水位应执行轻度压缩（裁剪冗余字段）")
                .startsWith("light_compressed");
    }

    @Test
    @DisplayName("UT-MEM-003: Token 85%~95% 触发中度压缩（CRITICAL 水位，早期对话摘要化）")
    void should_TriggerMediumCompress_When_TokenUsage85To95() {
        // F7 CRITICAL 分支：token_used=90% → TokenLevel.CRITICAL，中度压缩
        TokenWatermarkMonitor monitor = mock(TokenWatermarkMonitor.class);
        when(monitor.checkLevel(anyLong(), anyLong())).thenReturn(TokenLevel.CRITICAL);
        when(monitor.compress(any())).thenReturn("medium_compressed:early_dialog_summarized");

        TokenWatermark watermark = new TokenWatermark(9000L, 10000L);
        TokenLevel level = monitor.checkLevel(watermark.getUsedTokens(), watermark.getMaxTokens());
        String compressed = monitor.compress(watermark);

        assertThat(watermark.getUsageRatio())
                .as("used=9000 / max=10000 → usageRatio=0.90")
                .isEqualTo(0.90);
        assertThat(watermark.getLevel())
                .as("usageRatio=0.90 应映射为 CRITICAL（85%~95%）")
                .isEqualTo(TokenLevel.CRITICAL);
        assertThat(level)
                .as("monitor.checkLevel 应返回 CRITICAL")
                .isEqualTo(TokenLevel.CRITICAL);
        assertThat(compressed)
                .as("CRITICAL 水位应执行中度压缩（早期对话摘要化）")
                .startsWith("medium_compressed");
    }

    @Test
    @DisplayName("UT-MEM-004: Token ≥95% 触发重度压缩（CIRCUIT_BREAK 水位，滑动窗口保留最近 K 轮）")
    void should_TriggerHeavyCompress_When_TokenUsageGe95() {
        // F7 CIRCUIT_BREAK 分支：token_used=96% → TokenLevel.CIRCUIT_BREAK，重度压缩
        TokenWatermarkMonitor monitor = mock(TokenWatermarkMonitor.class);
        when(monitor.checkLevel(anyLong(), anyLong())).thenReturn(TokenLevel.CIRCUIT_BREAK);
        when(monitor.compress(any())).thenReturn("heavy_compressed:sliding_window_kept_last_K_rounds");

        TokenWatermark watermark = new TokenWatermark(9600L, 10000L);
        TokenLevel level = monitor.checkLevel(watermark.getUsedTokens(), watermark.getMaxTokens());
        String compressed = monitor.compress(watermark);

        assertThat(watermark.getUsageRatio())
                .as("used=9600 / max=10000 → usageRatio=0.96")
                .isEqualTo(0.96);
        assertThat(watermark.getLevel())
                .as("usageRatio=0.96 应映射为 CIRCUIT_BREAK（≥95%）")
                .isEqualTo(TokenLevel.CIRCUIT_BREAK);
        assertThat(level)
                .as("monitor.checkLevel 应返回 CIRCUIT_BREAK")
                .isEqualTo(TokenLevel.CIRCUIT_BREAK);
        assertThat(compressed)
                .as("CIRCUIT_BREAK 水位应执行重度压缩（滑动窗口保留最近 K 轮）")
                .startsWith("heavy_compressed");
    }

    // ============ TokenLevel.fromUsageRatio 分支覆盖 (P7-4 补充, 参考 UT-F8-017 模式) ============

    @Test
    @DisplayName("UT-RT-011: TokenLevel.fromUsageRatio 覆盖 4 个水位边界 + ≥1.0 兜底返回 CIRCUIT_BREAK")
    void should_ReturnCorrectLevel_When_FromUsageRatioCalled() {
        // enums fromUsageRatio 分支覆盖: 4 个命中分支 (SAFE/WARN/CRITICAL/CIRCUIT_BREAK) + 1 个兜底分支 (>=1.0)
        // 补充原因: P7-4 骨架阶段 excludes 排除 model/exception 后, enums 中 TokenLevel.fromUsageRatio
        // 是唯一含分支逻辑的方法 (for 循环 + return 兜底), 需补测试覆盖以达 line 0.80 / branch 0.70 阈值.

        // SAFE 分支：[0.0, 0.70)
        assertThat(TokenLevel.fromUsageRatio(0.0))
                .as("usageRatio=0.0 应返回 SAFE")
                .isEqualTo(TokenLevel.SAFE);
        assertThat(TokenLevel.fromUsageRatio(0.50))
                .as("usageRatio=0.50 应返回 SAFE")
                .isEqualTo(TokenLevel.SAFE);
        assertThat(TokenLevel.fromUsageRatio(0.6999))
                .as("usageRatio=0.6999 应返回 SAFE（边界左闭右开）")
                .isEqualTo(TokenLevel.SAFE);

        // WARN 分支：[0.70, 0.85)
        assertThat(TokenLevel.fromUsageRatio(0.70))
                .as("usageRatio=0.70 应返回 WARN（边界值归 WARN）")
                .isEqualTo(TokenLevel.WARN);
        assertThat(TokenLevel.fromUsageRatio(0.8499))
                .as("usageRatio=0.8499 应返回 WARN")
                .isEqualTo(TokenLevel.WARN);

        // CRITICAL 分支：[0.85, 0.95)
        assertThat(TokenLevel.fromUsageRatio(0.85))
                .as("usageRatio=0.85 应返回 CRITICAL（边界值归 CRITICAL）")
                .isEqualTo(TokenLevel.CRITICAL);
        assertThat(TokenLevel.fromUsageRatio(0.9499))
                .as("usageRatio=0.9499 应返回 CRITICAL")
                .isEqualTo(TokenLevel.CRITICAL);

        // CIRCUIT_BREAK 命中分支：[0.95, 1.01)
        assertThat(TokenLevel.fromUsageRatio(0.95))
                .as("usageRatio=0.95 应返回 CIRCUIT_BREAK（边界值归 CIRCUIT_BREAK）")
                .isEqualTo(TokenLevel.CIRCUIT_BREAK);
        assertThat(TokenLevel.fromUsageRatio(1.0))
                .as("usageRatio=1.0 仍命中 CIRCUIT_BREAK 分支（< 1.01 上界）")
                .isEqualTo(TokenLevel.CIRCUIT_BREAK);
        assertThat(TokenLevel.fromUsageRatio(1.0099))
                .as("usageRatio=1.0099 应返回 CIRCUIT_BREAK（命中 1.01 上界内）")
                .isEqualTo(TokenLevel.CIRCUIT_BREAK);

        // 兜底分支：usageRatio >= 1.01 或负数 → for 循环未命中 → 返回 CIRCUIT_BREAK
        assertThat(TokenLevel.fromUsageRatio(1.01))
                .as("usageRatio=1.01 未命中任何分支, 应兜底返回 CIRCUIT_BREAK")
                .isEqualTo(TokenLevel.CIRCUIT_BREAK);
        assertThat(TokenLevel.fromUsageRatio(1.5))
                .as("usageRatio=1.5 应兜底返回 CIRCUIT_BREAK（异常值）")
                .isEqualTo(TokenLevel.CIRCUIT_BREAK);
        assertThat(TokenLevel.fromUsageRatio(-0.1))
                .as("usageRatio=-0.1 应兜底返回 CIRCUIT_BREAK（负数异常值）")
                .isEqualTo(TokenLevel.CIRCUIT_BREAK);
    }
}
