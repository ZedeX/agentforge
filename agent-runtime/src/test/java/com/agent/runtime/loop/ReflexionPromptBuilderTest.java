package com.agent.runtime.loop;

import com.agent.runtime.enums.ReActPhaseType;
import com.agent.runtime.enums.ReflexionResult;
import com.agent.runtime.enums.StepStatus;
import com.agent.runtime.model.ReActContext;
import com.agent.runtime.model.StepState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReflexionPromptBuilder unit tests (T6, doc 06-runtime §3.2 / §5.2).
 */
class ReflexionPromptBuilderTest {

    private ReflexionPromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ReflexionPromptBuilder();
    }

    // ============ build() ============

    @Nested
    @DisplayName("build()")
    class BuildTests {

        @Test
        @DisplayName("contains all 4 decision options")
        void containsAllDecisionOptions() {
            ReActContext ctx = buildContext("ai_001", 3, 0, 2, 100, 32000, "hello");
            String prompt = builder.build(ctx, Collections.emptyList(), "act_failure");

            assertThat(prompt).contains("CONTINUE");
            assertThat(prompt).contains("RETRY");
            assertThat(prompt).contains("REPLAN");
            assertThat(prompt).contains("ABORT");
        }

        @Test
        @DisplayName("contains context fields: agentInstanceId, stepNumber, retryCount, maxRetry, triggerReason")
        void containsContextFields() {
            ReActContext ctx = buildContext("ai_test42", 7, 1, 3, 5000, 32000, "user query");
            String prompt = builder.build(ctx, Collections.emptyList(), "interval:3");

            assertThat(prompt).contains("ai_test42");
            assertThat(prompt).contains("stepNumber: 7");
            assertThat(prompt).contains("retryCount: 1");
            assertThat(prompt).contains("maxRetry: 3");
            assertThat(prompt).contains("triggerReason: interval:3");
            assertThat(prompt).contains("tokenUsed: 5000");
            assertThat(prompt).contains("tokenBudget: 32000");
        }

        @Test
        @DisplayName("truncates long userInput to 200 chars")
        void truncatesLongUserInput() {
            String longInput = "x".repeat(300);
            ReActContext ctx = buildContext("ai_1", 1, 0, 2, 0, 32000, longInput);
            String prompt = builder.build(ctx, Collections.emptyList(), "test");

            // Should contain truncated version (200 chars + "...")
            assertThat(prompt).contains("x".repeat(200) + "...");
            assertThat(prompt).doesNotContain("x".repeat(300));
        }

        @Test
        @DisplayName("handles null triggerReason as 'unknown'")
        void handlesNullTriggerReason() {
            ReActContext ctx = buildContext("ai_1", 1, 0, 2, 0, 32000, "test");
            String prompt = builder.build(ctx, Collections.emptyList(), null);

            assertThat(prompt).contains("triggerReason: unknown");
        }

        @Test
        @DisplayName("handles null history as 'no prior steps'")
        void handlesNullHistory() {
            ReActContext ctx = buildContext("ai_1", 1, 0, 2, 0, 32000, "test");
            String prompt = builder.build(ctx, null, "test");

            assertThat(prompt).contains("no prior steps");
        }

        @Test
        @DisplayName("handles empty history as 'no prior steps'")
        void handlesEmptyHistory() {
            ReActContext ctx = buildContext("ai_1", 1, 0, 2, 0, 32000, "test");
            String prompt = builder.build(ctx, Collections.emptyList(), "test");

            assertThat(prompt).contains("no prior steps");
        }

        @Test
        @DisplayName("includes last 5 steps from history")
        void includesLast5StepsFromHistory() {
            List<StepState> history = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                StepState s = new StepState("ai_1", i, ReActPhaseType.THINK);
                s.setStatus(StepStatus.SUCCESS);
                s.setThinkContent("think_" + i);
                history.add(s);
            }

            ReActContext ctx = buildContext("ai_1", 9, 0, 2, 0, 32000, "test");
            String prompt = builder.build(ctx, history, "test");

            // Should include steps 4-8 (last 5), not 1-3
            assertThat(prompt).contains("Step 4:");
            assertThat(prompt).contains("Step 8:");
            assertThat(prompt).doesNotContain("Step 1:");
            assertThat(prompt).doesNotContain("Step 2:");
            assertThat(prompt).doesNotContain("Step 3:");
        }

        @Test
        @DisplayName("includes think, observe, error content from steps")
        void includesStepContent() {
            StepState s = new StepState("ai_1", 1, ReActPhaseType.THINK);
            s.setStatus(StepStatus.FAILED);
            s.setThinkContent("I should call tool X");
            s.setObserveContent("Tool returned error code 500");
            s.setErrorMessage("Connection refused");

            ReActContext ctx = buildContext("ai_1", 2, 0, 2, 0, 32000, "test");
            String prompt = builder.build(ctx, List.of(s), "act_failure");

            assertThat(prompt).contains("think=I should call tool X");
            assertThat(prompt).contains("observe=Tool returned error code 500");
            assertThat(prompt).contains("error=Connection refused");
        }

        @Test
        @DisplayName("truncates long think/observe/error content to 100 chars")
        void truncatesLongStepContent() {
            StepState s = new StepState("ai_1", 1, ReActPhaseType.THINK);
            s.setStatus(StepStatus.SUCCESS);
            s.setThinkContent("t".repeat(150));
            s.setObserveContent("o".repeat(150));
            s.setErrorMessage("e".repeat(150));

            ReActContext ctx = buildContext("ai_1", 2, 0, 2, 0, 32000, "test");
            String prompt = builder.build(ctx, List.of(s), "test");

            assertThat(prompt).contains("t".repeat(100) + "...");
            assertThat(prompt).contains("o".repeat(100) + "...");
            assertThat(prompt).contains("e".repeat(100) + "...");
        }

        @Test
        @DisplayName("includes decision instruction with DECISION format")
        void includesDecisionInstruction() {
            ReActContext ctx = buildContext("ai_1", 1, 0, 2, 0, 32000, "test");
            String prompt = builder.build(ctx, Collections.emptyList(), "test");

            assertThat(prompt).contains("DECISION:");
            assertThat(prompt).contains("CONTINUE|RETRY|REPLAN|ABORT");
        }

        @Test
        @DisplayName("handles step with null phase/status as '?'")
        void handlesNullPhaseStatus() {
            StepState s = new StepState();
            s.setStepNumber(1);
            // phase and status left null

            ReActContext ctx = buildContext("ai_1", 2, 0, 2, 0, 32000, "test");
            String prompt = builder.build(ctx, List.of(s), "test");

            assertThat(prompt).contains("phase=?");
            assertThat(prompt).contains("status=?");
        }
    }

    // ============ estimateTokens() ============

    @Nested
    @DisplayName("estimateTokens()")
    class EstimateTokensTests {

        @Test
        @DisplayName("returns 0 for null prompt")
        void returnsZeroForNull() {
            assertThat(builder.estimateTokens(null)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 for empty prompt")
        void returnsZeroForEmpty() {
            assertThat(builder.estimateTokens("")).isEqualTo(0);
        }

        @Test
        @DisplayName("estimates tokens as ceil(length / 4)")
        void estimatesTokensCharBased() {
            // 12 chars / 4 = 3 tokens
            assertThat(builder.estimateTokens("hello world!")).isEqualTo(3);
            // 13 chars / 4 = 3.25 → ceil = 4
            assertThat(builder.estimateTokens("hello world!!")).isEqualTo(4);
        }

        @Test
        @DisplayName("1 char = 1 token (ceiling)")
        void oneCharIsOneToken() {
            assertThat(builder.estimateTokens("a")).isEqualTo(1);
        }
    }

    // ============ parseDecision() ============

    @Nested
    @DisplayName("parseDecision()")
    class ParseDecisionTests {

        @Test
        @DisplayName("returns CONTINUE for null input")
        void returnsContinueForNull() {
            assertThat(builder.parseDecision(null)).isEqualTo(ReflexionResult.CONTINUE);
        }

        @Test
        @DisplayName("returns CONTINUE for unrecognized input")
        void returnsContinueForUnrecognized() {
            assertThat(builder.parseDecision("I think we should proceed")).isEqualTo(ReflexionResult.CONTINUE);
        }

        @Test
        @DisplayName("parses DECISION:CONTINUE:reason")
        void parsesContinue() {
            assertThat(builder.parseDecision("DECISION:CONTINUE:progress is good"))
                    .isEqualTo(ReflexionResult.CONTINUE);
        }

        @Test
        @DisplayName("parses DECISION:RETRY:reason")
        void parsesRetry() {
            assertThat(builder.parseDecision("DECISION:RETRY:tool_call returned invalid JSON"))
                    .isEqualTo(ReflexionResult.RETRY);
        }

        @Test
        @DisplayName("parses DECISION:REPLAN:reason")
        void parsesReplan() {
            assertThat(builder.parseDecision("DECISION:REPLAN:current approach is flawed"))
                    .isEqualTo(ReflexionResult.REPLAN);
        }

        @Test
        @DisplayName("parses DECISION:ABORT:reason")
        void parsesAbort() {
            assertThat(builder.parseDecision("DECISION:ABORT:unrecoverable error"))
                    .isEqualTo(ReflexionResult.ABORT);
        }

        @Test
        @DisplayName("lenient: recognizes ABORT without DECISION: prefix")
        void lenientAbortWithoutPrefix() {
            assertThat(builder.parseDecision("We should ABORT this immediately"))
                    .isEqualTo(ReflexionResult.ABORT);
        }

        @Test
        @DisplayName("lenient: case insensitive")
        void caseInsensitive() {
            assertThat(builder.parseDecision("decision:retry:try again")).isEqualTo(ReflexionResult.RETRY);
            assertThat(builder.parseDecision("DECISION:replan:change plan")).isEqualTo(ReflexionResult.REPLAN);
        }

        @Test
        @DisplayName("priority: ABORT > REPLAN > RETRY when multiple keywords present")
        void priorityOrder() {
            // "ABORT" appears → ABORT regardless of other keywords
            assertThat(builder.parseDecision("ABORT and RETRY")).isEqualTo(ReflexionResult.ABORT);
            // "REPLAN" appears but no "ABORT" → REPLAN
            assertThat(builder.parseDecision("REPLAN with RETRY")).isEqualTo(ReflexionResult.REPLAN);
        }

        @Test
        @DisplayName("lenient: handles multi-line output with DECISION on second line")
        void handlesMultilineOutput() {
            String output = "After analyzing the history...\nDECISION:RETRY:previous step failed";
            assertThat(builder.parseDecision(output)).isEqualTo(ReflexionResult.RETRY);
        }
    }

    // ============ helper ============

    private ReActContext buildContext(String agentInstanceId, int stepNumber,
                                      int retryCount, int maxRetry,
                                      int tokenUsed, int tokenBudget,
                                      String userInput) {
        ReActContext ctx = new ReActContext();
        ctx.setAgentInstanceId(agentInstanceId);
        ctx.setStepNumber(stepNumber);
        ctx.setRetryCount(retryCount);
        ctx.setMaxRetry(maxRetry);
        ctx.setTokenUsed(tokenUsed);
        ctx.setTokenBudget(tokenBudget);
        ctx.setUserInput(userInput);
        return ctx;
    }
}
