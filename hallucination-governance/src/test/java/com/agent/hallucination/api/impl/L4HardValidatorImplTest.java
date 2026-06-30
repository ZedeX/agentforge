package com.agent.hallucination.api.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link L4HardValidatorImpl} 单元测试。
 */
class L4HardValidatorImplTest {

    private final L4HardValidatorImpl validator = new L4HardValidatorImpl();

    @Test
    @DisplayName("含来源标签且无黑名单: 通过")
    void shouldValidateWhenSourceTagPresent() {
        assertThat(validator.validate("数据增长 5% [来源:doc1]")).isTrue();
    }

    @Test
    @DisplayName("缺失来源标签: 失败")
    void shouldFailWhenSourceTagMissing() {
        assertThat(validator.validate("数据增长 5%")).isFalse();
    }

    @Test
    @DisplayName("命中黑名单关键词: 失败")
    void shouldFailWhenBlacklistHit() {
        assertThat(validator.validate("保证 100% 准确 [来源:doc1]")).isFalse();
    }

    @Test
    @DisplayName("JSON 括号不配对: 失败")
    void shouldFailWhenJsonUnbalanced() {
        assertThat(validator.validate("{\"a\": [1, 2} [来源:doc1]")).isFalse();
    }

    @Test
    @DisplayName("JSON 闭括号先于开括号: 失败")
    void shouldFailWhenJsonClosingBeforeOpening() {
        assertThat(validator.validate("{ } ] [来源:doc1]")).isFalse();
    }

    @Test
    @DisplayName("合法 JSON 含来源标签: 通过")
    void shouldValidateBalancedJson() {
        assertThat(validator.validate("{\"ref\":\"[来源:doc1]\"}")).isTrue();
    }

    @Test
    @DisplayName("空输出: 失败")
    void shouldFailWhenOutputBlank() {
        assertThat(validator.validate("")).isFalse();
        assertThat(validator.validate(null)).isFalse();
    }
}