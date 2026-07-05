package com.agent.runtime.watermark;

import com.agent.runtime.api.dto.ModelMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * TokenBudgetCalculator unit tests (T8, doc 06-runtime §3.3 / §4.2).
 */
class TokenBudgetCalculatorTest {

    private TokenBudgetCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TokenBudgetCalculator();
    }

    // ============ estimateTokens(String) ============

    @Nested
    @DisplayName("estimateTokens(String)")
    class EstimateTokensStringTests {

        @Test
        @DisplayName("returns 0 for null text")
        void returnsZeroForNull() {
            assertThat(calculator.estimateTokens((String) null)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 for empty text")
        void returnsZeroForEmpty() {
            assertThat(calculator.estimateTokens("")).isEqualTo(0);
        }

        @Test
        @DisplayName("pure English: ~4 chars per token")
        void pureEnglish() {
            // 20 English chars / 4.0 = 5 tokens
            int tokens = calculator.estimateTokens("Hello world test!!");
            assertThat(tokens).isGreaterThanOrEqualTo(4);
            assertThat(tokens).isLessThanOrEqualTo(6);
        }

        @Test
        @DisplayName("pure CJK: ~1.5 chars per token")
        void pureCjk() {
            // 6 CJK chars / 1.5 = 4 tokens
            String cjkText = "你好世界测试";
            int tokens = calculator.estimateTokens(cjkText);
            assertThat(tokens).isGreaterThanOrEqualTo(3);
            assertThat(tokens).isLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("mixed English and CJK: weighted average")
        void mixedEnglishAndCjk() {
            // 6 CJK + 12 English ≈ 6/1.5 + 12/4.0 = 4 + 3 = 7 tokens
            String mixed = "你好世界测试Hello World!";
            int tokens = calculator.estimateTokens(mixed);
            assertThat(tokens).isGreaterThanOrEqualTo(5);
            assertThat(tokens).isLessThanOrEqualTo(9);
        }

        @Test
        @DisplayName("single char = 1 token (ceiling)")
        void singleChar() {
            assertThat(calculator.estimateTokens("a")).isEqualTo(1);
        }

        @Test
        @DisplayName("single CJK char = 1 token (ceiling)")
        void singleCjkChar() {
            // 1 CJK char / 1.5 = 0.67 → ceil = 1
            assertThat(calculator.estimateTokens("你")).isEqualTo(1);
        }

        @Test
        @DisplayName("long text produces reasonable token estimate")
        void longText() {
            // 1000 English chars ≈ 250 tokens
            String longText = "a".repeat(1000);
            int tokens = calculator.estimateTokens(longText);
            assertThat(tokens).isGreaterThanOrEqualTo(240);
            assertThat(tokens).isLessThanOrEqualTo(260);
        }
    }

    // ============ estimateTokens(List<ModelMessage>) ============

    @Nested
    @DisplayName("estimateTokens(List<ModelMessage>)")
    class EstimateTokensMessagesTests {

        @Test
        @DisplayName("returns 0 for null messages")
        void returnsZeroForNull() {
            assertThat(calculator.estimateTokens((List<ModelMessage>) null)).isEqualTo(0);
        }

        @Test
        @DisplayName("returns 0 for empty messages")
        void returnsZeroForEmpty() {
            assertThat(calculator.estimateTokens(Collections.emptyList())).isEqualTo(0);
        }

        @Test
        @DisplayName("single message: 1 role token + content tokens + 3 overhead")
        void singleMessage() {
            // "Hello" = 5 chars → ~2 tokens; + 1 role + 3 overhead = ~6
            List<ModelMessage> messages = List.of(ModelMessage.user("Hello"));
            int tokens = calculator.estimateTokens(messages);
            assertThat(tokens).isGreaterThanOrEqualTo(4);
            assertThat(tokens).isLessThanOrEqualTo(8);
        }

        @Test
        @DisplayName("multiple messages: sum of role + content tokens + overhead")
        void multipleMessages() {
            List<ModelMessage> messages = List.of(
                    ModelMessage.system("You are a helpful assistant."),
                    ModelMessage.user("What is 2+2?")
            );
            int tokens = calculator.estimateTokens(messages);
            // At minimum: 2 role tokens + 2 content estimates + 3 overhead
            assertThat(tokens).isGreaterThan(5);
        }

        @Test
        @DisplayName("message with toolCallId includes toolCallId tokens")
        void messageWithToolCallId() {
            List<ModelMessage> messages = List.of(
                    ModelMessage.tool("call_abc123", "Tool result: 42")
            );
            int tokens = calculator.estimateTokens(messages);
            // Should include tokens for toolCallId "call_abc123"
            assertThat(tokens).isGreaterThan(5);
        }

        @Test
        @DisplayName("null content in message contributes only role token")
        void nullContentInMessage() {
            List<ModelMessage> messages = List.of(
                    new ModelMessage("user", null, null, null)
            );
            int tokens = calculator.estimateTokens(messages);
            // 1 role + 0 content + 3 overhead = 4
            assertThat(tokens).isEqualTo(4);
        }
    }

    // ============ usageRatio() ============

    @Nested
    @DisplayName("usageRatio()")
    class UsageRatioTests {

        @Test
        @DisplayName("returns 0.0 when budget <= 0")
        void returnsZeroWhenBudgetInvalid() {
            assertThat(calculator.usageRatio(100, 0)).isEqualTo(0.0);
            assertThat(calculator.usageRatio(100, -1)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("returns correct ratio for normal usage")
        void normalUsage() {
            assertThat(calculator.usageRatio(5000, 32000)).isCloseTo(0.15625, within(0.001));
        }

        @Test
        @DisplayName("returns 1.0 when fully used")
        void fullyUsed() {
            assertThat(calculator.usageRatio(32000, 32000)).isEqualTo(1.0);
        }

        @Test
        @DisplayName("returns >1.0 when over budget")
        void overBudget() {
            assertThat(calculator.usageRatio(40000, 32000)).isGreaterThan(1.0);
        }

        @Test
        @DisplayName("returns 0.0 when nothing used")
        void nothingUsed() {
            assertThat(calculator.usageRatio(0, 32000)).isEqualTo(0.0);
        }
    }

    // ============ remaining() ============

    @Nested
    @DisplayName("remaining()")
    class RemainingTests {

        @Test
        @DisplayName("returns budget - used")
        void basicRemaining() {
            assertThat(calculator.remaining(5000, 32000)).isEqualTo(27000L);
        }

        @Test
        @DisplayName("returns 0 when fully used")
        void fullyUsed() {
            assertThat(calculator.remaining(32000, 32000)).isEqualTo(0L);
        }

        @Test
        @DisplayName("returns negative when over budget")
        void overBudget() {
            assertThat(calculator.remaining(40000, 32000)).isEqualTo(-8000L);
        }

        @Test
        @DisplayName("returns full budget when nothing used")
        void nothingUsed() {
            assertThat(calculator.remaining(0, 32000)).isEqualTo(32000L);
        }
    }
}
