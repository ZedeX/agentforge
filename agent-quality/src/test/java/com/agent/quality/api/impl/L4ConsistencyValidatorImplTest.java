package com.agent.quality.api.impl;

import com.agent.quality.enums.L4ValidationResult;
import com.agent.quality.model.L4ValidationOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link L4ConsistencyValidatorImpl} 单元测试。
 */
class L4ConsistencyValidatorImplTest {

    private final L4ConsistencyValidatorImpl validator = new L4ConsistencyValidatorImpl();

    @Test
    @DisplayName("输出与参考源高度相似: 返回 PASS, cosineSim ≥ 0.75")
    void should_ReturnPass_When_OutputSimilarToReference() {
        String reference = "根据年报披露, 公司 2024 年营收增长 12.5%, 主要由海外市场驱动.";
        String output = "根据年报披露, 公司 2024 年营收增长 12.5%, 主要由海外市场驱动. [来源:doc1]";

        L4ValidationOutput result = validator.validate(output, reference);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getResult()).isEqualTo(L4ValidationResult.PASS);
        assertThat(result.getCosineSim()).isGreaterThanOrEqualTo(0.75);
    }

    @Test
    @DisplayName("输出与参考源完全无关: 返回 FACT_INCONSISTENCY")
    void should_ReturnFactInconsistency_When_OutputDissimilar() {
        String reference = "巴黎是法国的首都, 位于塞纳河畔.";
        String output = "Python 是一门动态类型编程语言, 广泛用于数据科学. [来源:doc1]";

        L4ValidationOutput result = validator.validate(output, reference);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getResult()).isEqualTo(L4ValidationResult.FACT_INCONSISTENCY);
        assertThat(result.getCosineSim()).isLessThan(0.75);
        assertThat(result.getViolationDetail()).contains("cosine_sim");
    }

    @Test
    @DisplayName("模型输出为空: 返回 FACT_INCONSISTENCY, cosineSim=0")
    void should_ReturnFactInconsistency_When_OutputBlank() {
        L4ValidationOutput result = validator.validate("", "参考源文本");

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getResult()).isEqualTo(L4ValidationResult.FACT_INCONSISTENCY);
        assertThat(result.getCosineSim()).isZero();
        assertThat(result.getViolationDetail()).contains("输出为空");
    }

    @Test
    @DisplayName("参考源为 null: 返回 FACT_INCONSISTENCY")
    void should_ReturnFactInconsistency_When_ReferenceIsNull() {
        L4ValidationOutput result = validator.validate("模型输出文本", null);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getResult()).isEqualTo(L4ValidationResult.FACT_INCONSISTENCY);
        assertThat(result.getCosineSim()).isZero();
        assertThat(result.getViolationDetail()).contains("参考源");
    }
}
