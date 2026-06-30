package com.agent.hallucination.api.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RagAnchorImpl} 单元测试。
 */
class RagAnchorImplTest {

    private final RagAnchorImpl anchor = new RagAnchorImpl();

    @Test
    @DisplayName("事实型任务: 召回充分返回 true")
    void shouldAnchorFactualTask() {
        assertThat(anchor.anchor("请提供数据来源与统计口径")).isTrue();
    }

    @Test
    @DisplayName("非事实型任务: 返回 true")
    void shouldAnchorNonFactualTask() {
        assertThat(anchor.anchor("讲个笑话")).isTrue();
    }

    @Test
    @DisplayName("空任务描述: 召回不足返回 false")
    void shouldRefuseWhenTaskBlank() {
        assertThat(anchor.anchor("")).isFalse();
        assertThat(anchor.anchor(null)).isFalse();
        assertThat(anchor.anchor("   ")).isFalse();
    }
}