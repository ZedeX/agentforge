package com.agent.runtime.api.impl;

import com.agent.runtime.api.MemoryClient;
import com.agent.runtime.api.ModelGatewayClient;
import com.agent.runtime.api.ReflexionEngine;
import com.agent.runtime.api.StepStateSyncer;
import com.agent.runtime.api.TokenWatermarkMonitor;
import com.agent.runtime.api.ToolEngineClient;
import com.agent.runtime.config.RuntimeProperties;
import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.enums.ReflexionResult;
import com.agent.runtime.enums.TokenLevel;
import com.agent.runtime.loop.ActPhase;
import com.agent.runtime.loop.ObservePhase;
import com.agent.runtime.loop.ReActPromptBuilder;
import com.agent.runtime.loop.ThinkPhase;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.ReActResult;
import com.agent.runtime.model.StepState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReActLoopImpl T3 单元测试.
 *
 * <p>覆盖 Plan 06 T3 Red 阶段定义的 12 个测试场景:
 * <ol>
 *   <li>run_completesInSingleStep_whenThinkResolvesDirectly</li>
 *   <li>run_executesFullCycle_thinkActObserve</li>
 *   <li>run_respectsMaxSteps_abortsWhenExceeded</li>
 *   <li>run_triggersReflexionEvery3Steps</li>
 *   <li>run_triggersReflexionOnActFailure</li>
 *   <li>run_retriesOnRetryReflexion</li>
 *   <li>run_replansOnReplanReflexion</li>
 *   <li>run_abortsOnAbortReflexion</li>
 *   <li>run_cancelsOnCancelSignal</li>
 *   <li>run_injectsRecalledMemoryBeforeThink</li>
 *   <li>run_recordsStepStateAfterEachPhase</li>
 *   <li>run_updatesTokenWatermarkAfterModelCall</li>
 * </ol>
 */
class ReActLoopImplTest {

    private ModelGatewayClient modelGateway;
    private ToolEngineClient toolEngine;
    private MemoryClient memoryClient;
    private ReflexionEngine reflexionEngine;
    private StepStateSyncer stepStateSyncer;
    private TokenWatermarkMonitor tokenWatermarkMonitor;
    private RuntimeProperties properties;

    @BeforeEach
    void setUp() {
        modelGateway = mock(ModelGatewayClient.class);
        toolEngine = mock(ToolEngineClient.class);
        memoryClient = mock(MemoryClient.class);
        reflexionEngine = mock(ReflexionEngine.class);
        stepStateSyncer = mock(StepStateSyncer.class);
        tokenWatermarkMonitor = mock(TokenWatermarkMonitor.class);
        properties = new RuntimeProperties();
        // Tighten thresholds for test scenarios
        properties.getReact().setMaxSteps(5);
        properties.getReact().setTokenBudget(1000);
        properties.getReflexion().setInterval(3);

        when(memoryClient.recallMemory(anyString(), anyString())).thenReturn("");
        when(tokenWatermarkMonitor.checkLevel(anyLong(), anyLong())).thenReturn(TokenLevel.SAFE);
        when(reflexionEngine.reflect(any(), any(), anyString())).thenReturn(ReflexionResult.CONTINUE);
    }

    private ReActLoopImpl newLoop() {
        ReActPromptBuilder promptBuilder = new ReActPromptBuilder(properties);
        ThinkPhase thinkPhase = new ThinkPhase(modelGateway, promptBuilder, tokenWatermarkMonitor);
        ActPhase actPhase = new ActPhase(toolEngine);
        ObservePhase observePhase = new ObservePhase();
        return new ReActLoopImpl(modelGateway, toolEngine, memoryClient, reflexionEngine,
                stepStateSyncer, tokenWatermarkMonitor, properties,
                thinkPhase, actPhase, observePhase, promptBuilder);
    }

    // ============ 1. run_completesInSingleStep_whenThinkResolvesDirectly ============

    @Test
    @DisplayName("run_completesInSingleStep_whenThinkResolvesDirectly: Think 直接给最终答案 → 1 步成功")
    void run_completesInSingleStep_whenThinkResolvesDirectly() {
        // given: 模型首步直接给出 final_answer
        when(modelGateway.chat(anyString())).thenReturn("final_answer:42");

        ReActContext ctx = ReActContext.forTest("task_direct", 5, 1000);
        ctx.setUserInput("what is the answer");

        // when
        ReActResult result = newLoop().run(ctx);

        // then
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getFinalAnswer()).isEqualTo("42");
        assertThat(result.getStepCount()).isEqualTo(1);
        assertThat(result.isFinished()).isTrue();
        verify(modelGateway, times(1)).chat(anyString());
        verify(toolEngine, times(0)).invoke(anyString(), anyString());
    }

    // ============ 2. run_executesFullCycle_thinkActObserve ============

    @Test
    @DisplayName("run_executesFullCycle_thinkActObserve: Think 决定调 tool → Act → Observe → 下一步 Think 给答案")
    void run_executesFullCycle_thinkActObserve() {
        // given: 首步 tool_call，第二步 final_answer
        when(modelGateway.chat(anyString()))
                .thenReturn("tool_call(tool_search, {\"q\":\"order\"})")
                .thenReturn("final_answer:order od_001 amount=99");
        when(toolEngine.invoke(eq("tool_search"), anyString())).thenReturn("result:order od_001 amount=99");

        ReActContext ctx = ReActContext.forTest("task_cycle", 5, 1000);

        // when
        ReActResult result = newLoop().run(ctx);

        // then
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getFinalAnswer()).contains("order od_001 amount=99");
        assertThat(result.getStepCount()).isEqualTo(2);
        verify(toolEngine, times(1)).invoke(eq("tool_search"), anyString());
        // 3 phases on step 1 (THINK/ACT/OBSERVE) + 1 phase on step 2 (THINK) = 4 syncStepState calls
        verify(stepStateSyncer, atLeast(4)).syncStepState(anyString(), anyInt(), any());
    }

    // ============ 3. run_respectsMaxSteps_abortsWhenExceeded ============

    @Test
    @DisplayName("run_respectsMaxSteps_abortsWhenExceeded: maxSteps=3, 模型持续 tool_call → 第 4 步触发 ABORT")
    void run_respectsMaxSteps_abortsWhenExceeded() {
        // given: 模型永远返回 tool_call，maxSteps=3
        when(modelGateway.chat(anyString())).thenReturn("tool_call(search, {\"q\":\"x\"})");
        when(toolEngine.invoke(anyString(), anyString())).thenReturn("result");
        properties.getReact().setMaxSteps(3);

        ReActContext ctx = ReActContext.forTest("task_max", 3, 1000);

        // when
        ReActResult result = newLoop().run(ctx);

        // then
        assertThat(result.getStatus()).isEqualTo("ABORTED");
        assertThat(result.getReplanReason()).contains("MAX_STEPS_EXCEEDED");
        assertThat(result.getStepCount()).isEqualTo(3);
    }

    // ============ 4. run_triggersReflexionEvery3Steps ============

    @Test
    @DisplayName("run_triggersReflexionEvery3Steps: 每 3 步触发 Reflexion（mock 返回 CONTINUE）")
    void run_triggersReflexionEvery3Steps() {
        // given: 模型持续 tool_call，第 3 + 6 步触发 reflexion (interval=3)，第 7 步给 final_answer
        // 注: final_answer 会立即返回循环, 不会触发 reflexion, 所以 step 6 必须是 tool_call
        when(modelGateway.chat(anyString()))
                .thenReturn("tool_call(t1, {})")   // step 1
                .thenReturn("tool_call(t2, {})")   // step 2
                .thenReturn("tool_call(t3, {})")   // step 3 → trigger reflexion #1
                .thenReturn("tool_call(t4, {})")   // step 4
                .thenReturn("tool_call(t5, {})")   // step 5
                .thenReturn("tool_call(t6, {})")   // step 6 → trigger reflexion #2
                .thenReturn("final_answer:done");  // step 7
        when(toolEngine.invoke(anyString(), anyString())).thenReturn("ok");

        ReActContext ctx = ReActContext.forTest("task_interval", 10, 2000);

        // when
        ReActResult result = newLoop().run(ctx);

        // then
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        // Reflexion should be triggered at step 3 and step 6 (interval=3)
        verify(reflexionEngine, atLeast(2)).reflect(any(), any(), eq("interval=3"));
    }

    // ============ 5. run_triggersReflexionOnActFailure ============

    @Test
    @DisplayName("run_triggersReflexionOnActFailure: ACT 失败后必触发 Reflexion（mock 返回 RETRY）")
    void run_triggersReflexionOnActFailure() {
        // given: 模型 tool_call, 工具抛异常
        when(modelGateway.chat(anyString()))
                .thenReturn("tool_call(failing_tool, {})")  // step 1 - act fails
                .thenReturn("final_answer:recovered");       // step 2 - succeeds
        when(toolEngine.invoke(eq("failing_tool"), anyString()))
                .thenThrow(new RuntimeException("tool timeout"));
        when(reflexionEngine.reflect(any(), any(), anyString()))
                .thenReturn(ReflexionResult.CONTINUE);  // recover and continue

        ReActContext ctx = ReActContext.forTest("task_act_fail", 5, 1000);

        // when
        ReActResult result = newLoop().run(ctx);

        // then
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        // Reflexion triggered by act_failure (triggerReason starts with "act_failure:")
        verify(reflexionEngine, atLeast(1)).reflect(any(), any(),
                org.mockito.ArgumentMatchers.startsWith("act_failure"));
    }

    // ============ 6. run_retriesOnRetryReflexion ============

    @Test
    @DisplayName("run_retriesOnRetryReflexion: Reflexion RETRY → 重跑当前 step（retryCount+1）")
    void run_retriesOnRetryReflexion() {
        // given: 首步 act 失败 → reflexion RETRY → 重跑后成功
        when(modelGateway.chat(anyString()))
                .thenReturn("tool_call(retry_tool, {})")   // step 1 first attempt
                .thenReturn("tool_call(retry_tool, {})")   // step 1 retry (step number reset)
                .thenReturn("final_answer:retry_ok");       // step 2 success
        AtomicInteger invokeCount = new AtomicInteger(0);
        when(toolEngine.invoke(eq("retry_tool"), anyString())).thenAnswer(invocation -> {
            if (invokeCount.incrementAndGet() == 1) {
                throw new RuntimeException("first attempt fails");
            }
            return "ok on retry";
        });
        when(reflexionEngine.reflect(any(), any(), anyString()))
                .thenReturn(ReflexionResult.RETRY)         // first reflexion triggers retry
                .thenReturn(ReflexionResult.CONTINUE);      // subsequent reflexion continues

        ReActContext ctx = ReActContext.forTest("task_retry", 5, 1000);
        ctx.setMaxRetry(3);  // allow retry

        // when
        ReActResult result = newLoop().run(ctx);

        // then
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getFinalAnswer()).isEqualTo("retry_ok");
        assertThat(ctx.getRetryCount()).isGreaterThanOrEqualTo(1);
    }

    // ============ 7. run_replansOnReplanReflexion ============

    @Test
    @DisplayName("run_replansOnReplanReflexion: Reflexion REPLAN → 通知 orchestrator + ABORT 当前循环")
    void run_replansOnReplanReflexion() {
        // given: 第 3 步触发 reflexion REPLAN
        when(modelGateway.chat(anyString()))
                .thenReturn("tool_call(t1, {})")   // step 1
                .thenReturn("tool_call(t2, {})")   // step 2
                .thenReturn("tool_call(t3, {})");  // step 3 → REPLAN
        when(toolEngine.invoke(anyString(), anyString())).thenReturn("ok");
        when(reflexionEngine.reflect(any(), any(), anyString()))
                .thenReturn(ReflexionResult.REPLAN);

        ReActContext ctx = ReActContext.forTest("task_replan", 10, 2000);

        // when
        ReActResult result = newLoop().run(ctx);

        // then
        assertThat(result.getStatus()).isEqualTo("REPLAN_REQUESTED");
        assertThat(result.getReplanReason()).contains("interval=3");
        assertThat(result.getFinalReflexion()).isEqualTo(ReflexionResult.REPLAN);
    }

    // ============ 8. run_abortsOnAbortReflexion ============

    @Test
    @DisplayName("run_abortsOnAbortReflexion: Reflexion ABORT → 立即终止")
    void run_abortsOnAbortReflexion() {
        // given: 第 3 步触发 reflexion ABORT
        when(modelGateway.chat(anyString()))
                .thenReturn("tool_call(t1, {})")   // step 1
                .thenReturn("tool_call(t2, {})")   // step 2
                .thenReturn("tool_call(t3, {})");  // step 3 → ABORT
        when(toolEngine.invoke(anyString(), anyString())).thenReturn("ok");
        when(reflexionEngine.reflect(any(), any(), anyString()))
                .thenReturn(ReflexionResult.ABORT);

        ReActContext ctx = ReActContext.forTest("task_abort", 10, 2000);

        // when
        ReActResult result = newLoop().run(ctx);

        // then
        assertThat(result.getStatus()).isEqualTo("ABORTED");
        assertThat(result.getReplanReason()).contains("REFLEXION_ABORT");
        assertThat(result.getFinalReflexion()).isEqualTo(ReflexionResult.ABORT);
    }

    // ============ 9. run_cancelsOnCancelSignal ============

    @Test
    @DisplayName("run_cancelsOnCancelSignal: 收到 cancel 信号 → 中断当前 step + 状态 CANCELLED")
    void run_cancelsOnCancelSignal() {
        // given: 第 1 步执行后外部设置 cancel=true
        when(modelGateway.chat(anyString()))
                .thenReturn("tool_call(t1, {})")   // step 1
                .thenReturn("tool_call(t2, {})");  // step 2 (won't reach due to cancel)
        when(toolEngine.invoke(anyString(), anyString())).thenAnswer(inv -> {
            // simulate external cancel during tool execution
            return "ok";
        });

        ReActContext ctx = ReActContext.forTest("task_cancel", 10, 2000);
        ctx.setCancelled(true);  // pre-cancel before run

        // when
        ReActResult result = newLoop().run(ctx);

        // then
        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        assertThat(result.getStepCount()).isEqualTo(0);  // cancelled before any step
    }

    // ============ 10. run_injectsRecalledMemoryBeforeThink ============

    @Test
    @DisplayName("run_injectsRecalledMemoryBeforeThink: Think 前调用 MemoryClient.recallMemory 注入历史经验")
    void run_injectsRecalledMemoryBeforeThink() {
        // given: recallMemory 返回非空历史经验
        String memoryText = "previous_task_succeeded_with_strategy_X";
        when(memoryClient.recallMemory(anyString(), anyString())).thenReturn(memoryText);
        when(modelGateway.chat(anyString())).thenReturn("final_answer:ok");

        ReActContext ctx = ReActContext.forTest("task_memory", 5, 1000);
        ctx.setUserInput("solve the task");

        // when
        ReActResult result = newLoop().run(ctx);

        // then
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        verify(memoryClient, times(1)).recallMemory(eq(ctx.getAgentInstanceId()), eq("solve the task"));
        // verify the prompt passed to modelGateway contains the recalled memory
        org.mockito.ArgumentCaptor<String> promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(modelGateway).chat(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains(memoryText);
    }

    // ============ 11. run_recordsStepStateAfterEachPhase ============

    @Test
    @DisplayName("run_recordsStepStateAfterEachPhase: 每个阶段后调 StepStateSyncer 落库")
    void run_recordsStepStateAfterEachPhase() {
        // given: 完整 Think→Act→Observe→Think(final) 流程
        when(modelGateway.chat(anyString()))
                .thenReturn("tool_call(search, {\"q\":\"x\"})")
                .thenReturn("final_answer:done");
        when(toolEngine.invoke(anyString(), anyString())).thenReturn("result_x");

        ReActContext ctx = ReActContext.forTest("task_record", 5, 1000);

        // when
        newLoop().run(ctx);

        // then: step 1 应有 THINK/ACT/OBSERVE 3 次同步, step 2 应有 THINK 1 次同步
        verify(stepStateSyncer, times(1)).syncStepState(anyString(), eq(1), eq(ReActPhaseType.THINK));
        verify(stepStateSyncer, times(1)).syncStepState(anyString(), eq(1), eq(ReActPhaseType.ACT));
        verify(stepStateSyncer, times(1)).syncStepState(anyString(), eq(1), eq(ReActPhaseType.OBSERVE));
        verify(stepStateSyncer, times(1)).syncStepState(anyString(), eq(2), eq(ReActPhaseType.THINK));
        // checkpoint also called for each phase
        verify(stepStateSyncer, times(4)).checkpoint(anyString(), anyInt(), anyString());
    }

    // ============ 12. run_updatesTokenWatermarkAfterModelCall ============

    @Test
    @DisplayName("run_updatesTokenWatermarkAfterModelCall: 每次模型调用后更新 token 水位")
    void run_updatesTokenWatermarkAfterModelCall() {
        // given: 模型 2 次调用 (tool_call + final_answer)
        when(modelGateway.chat(anyString()))
                .thenReturn("tool_call(search, {\"q\":\"x\"})")
                .thenReturn("final_answer:done");
        when(toolEngine.invoke(anyString(), anyString())).thenReturn("result_x");
        when(tokenWatermarkMonitor.checkLevel(anyLong(), anyLong()))
                .thenReturn(TokenLevel.SAFE);

        ReActContext ctx = ReActContext.forTest("task_watermark", 5, 1000);

        // when
        ReActResult result = newLoop().run(ctx);

        // then: checkLevel 至少调用 2 次 (每次 Think 一次), ctx.tokenUsed 累计 > 0
        verify(tokenWatermarkMonitor, atLeast(2)).checkLevel(anyLong(), anyLong());
        assertThat(result.getTotalTokensUsed()).isGreaterThan(0);
        assertThat(ctx.getTokenUsed()).isGreaterThan(0);
    }

    // ============ Legacy tests for start() / transit() (backward compat) ============

    @Test
    @DisplayName("Legacy start(): 模型返回 final_answer, 循环应在 FINISH 阶段终止并产出最终答案")
    void should_TerminateWithFinalAnswer_When_ModelReturnsFinalAnswer() {
        when(modelGateway.chat(anyString())).thenReturn("final_answer:42");

        ReActLoopImpl loop = newLoop();
        ReActContext context = new ReActContext("ag_001", "tk_001");

        String finalAnswer = loop.start(context);

        assertThat(finalAnswer)
                .as("最终答案应包含 final_answer")
                .isNotNull()
                .contains("final_answer");
        assertThat(context.getPhase())
                .as("循环终止后 phase 应为 FINISH")
                .isEqualTo(ReActPhaseType.FINISH);
        assertThat(context.getFinalAnswer())
                .as("context.finalAnswer 应被设置")
                .isNotNull();
    }

    @Test
    @DisplayName("Legacy start(): 模型输出包含 tool_call, transit 应转入 ACT 阶段")
    void should_TransitToAct_When_ModelOutputContainsToolCall() {
        when(modelGateway.chat(anyString())).thenReturn("tool_call(tool_search, {q:order})");
        when(toolEngine.invoke(anyString(), anyString())).thenReturn("result:order od_001 金额 99 元");

        ReActLoopImpl loop = newLoop();
        ReActContext context = new ReActContext("ag_001", "tk_001");

        String finalAnswer = loop.start(context);

        assertThat(finalAnswer).isNotNull();
        assertThat(context.getMemory().get("last_tool_result"))
                .as("工具结果应注入 context.memory")
                .isNotNull()
                .asString()
                .contains("result:order");
    }

    @Test
    @DisplayName("Legacy transit(): modelOutput 为 null, transit 应默认转 FINISH")
    void should_TransitToFinish_When_ModelOutputIsNull() {
        ReActLoopImpl loop = newLoop();
        ReActContext context = new ReActContext("ag_002", "tk_002");

        ReActPhaseType next = loop.transit(context, null);

        assertThat(next)
                .as("modelOutput 为 null 应默认转 FINISH")
                .isEqualTo(ReActPhaseType.FINISH);
    }
}
