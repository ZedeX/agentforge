package com.agent.modelgateway.api.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TokenCounterImpl unit tests (doc 02-api §4, Chinese 1.7x coefficient).
 */
@DisplayName("TokenCounterImpl 令牌估算器")
class TokenCounterImplTest {

    private final TokenCounterImpl counter = new TokenCounterImpl();

    @Test
    @DisplayName("空字符串或 null 返回 0")
    void should_ReturnZero_When_InputNullOrEmpty() {
        assertThat(counter.count(null)).isZero();
        assertThat(counter.count("")).isZero();
    }

    @Test
    @DisplayName("纯英文按 ~4 char/token 估算")
    void should_EstimateEnglishByCharRatio_When_PureAscii() {
        // "hello world" = 11 chars / 4 ≈ 3 tokens (rounded up)
        int tokens = counter.count("hello world");
        assertThat(tokens).isBetween(2, 4);
    }

    @Test
    @DisplayName("中文字符按 1.7x 系数估算 (每个汉字 ≈ 1 token)")
    void should_ApplyChineseCoefficient_When_TextHasCjk() {
        // 10 Chinese chars → ~10 tokens (CJK 1.7x → ~1 token each)
        int tokens = counter.count("你好世界你好世界你好世界");
        assertThat(tokens).isBetween(8, 12);
    }

    @Test
    @DisplayName("中英混合分别计算后求和")
    void should_SumCjkAndAscii_When_MixedContent() {
        // "你好hello" = 2 CJK + 5 ASCII → 2 + (5/4 rounded up = 2) = 4
        int tokens = counter.count("你好hello");
        assertThat(tokens).isBetween(3, 5);
    }
}
