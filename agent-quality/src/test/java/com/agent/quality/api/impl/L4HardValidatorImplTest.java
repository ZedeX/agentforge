package com.agent.quality.api.impl;

import com.agent.quality.enums.L4ValidationResult;
import com.agent.quality.model.L4ValidationOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link L4HardValidatorImpl} 单元测试。
 */
class L4HardValidatorImplTest {

    private final L4HardValidatorImpl validator = new L4HardValidatorImpl();

    @Test
    @DisplayName("含来源标签且无黑名单: PASS")
    void should_ReturnPass_When_SourceTagPresentAndNoBlacklist() {
        String output = "数据增长 5% [来源:doc1]";

        L4ValidationOutput result = validator.validate(output);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getResult()).isEqualTo(L4ValidationResult.PASS);
        assertThat(result.getViolationDetail()).isNull();
    }

    @Test
    @DisplayName("缺失来源标签: 返回 FORMAT_VIOLATION 并填写违规详情")
    void should_ReturnFormatViolation_When_SourceTagMissing() {
        String output = "数据增长 5%";

        L4ValidationOutput result = validator.validate(output);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getResult()).isEqualTo(L4ValidationResult.FORMAT_VIOLATION);
        assertThat(result.getViolationDetail()).contains("来源标签");
    }

    @Test
    @DisplayName("命中黑名单关键词: 返回 FORMAT_VIOLATION")
    void should_ReturnFormatViolation_When_BlacklistHit() {
        String output = "保证 100% 准确 [来源:doc1]";

        L4ValidationOutput result = validator.validate(output);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getResult()).isEqualTo(L4ValidationResult.FORMAT_VIOLATION);
        assertThat(result.getViolationDetail()).contains("黑名单");
    }

    @Test
    @DisplayName("输出为空或 null: 返回 FORMAT_VIOLATION")
    void should_ReturnFormatViolation_When_OutputBlank() {
        L4ValidationOutput emptyResult = validator.validate("");
        assertThat(emptyResult.isPassed()).isFalse();
        assertThat(emptyResult.getResult()).isEqualTo(L4ValidationResult.FORMAT_VIOLATION);

        L4ValidationOutput nullResult = validator.validate(null);
        assertThat(nullResult.isPassed()).isFalse();
        assertThat(nullResult.getResult()).isEqualTo(L4ValidationResult.FORMAT_VIOLATION);
    }

    @Test
    @DisplayName("JSON 括号不配对: 返回 FORMAT_VIOLATION")
    void should_ReturnFormatViolation_When_JsonUnbalanced() {
        String output = "{\"a\": [1, 2} [来源:doc1]";

        L4ValidationOutput result = validator.validate(output);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getResult()).isEqualTo(L4ValidationResult.FORMAT_VIOLATION);
        assertThat(result.getViolationDetail()).contains("JSON");
    }
}
