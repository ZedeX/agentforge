package com.agent.orchestrator.planning.grpc;

import agentplatform.planning.v1.AssessResponse;
import com.agent.orchestrator.assessor.ComplexityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AssessResultMapper 单元测试（对齐 planning.proto AssessResponse.complexity 字段注释）。
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>toAssessResponse：L1/L2/L3 → complexity 1/2/3 / level 为 null 抛 IllegalArgumentException /
 *       reason 为 null 转 "" / suggestedTags 为 null 转空列表</li>
 *   <li>toNumeric：L1→1 / L2→2 / L3→3</li>
 * </ul>
 */
class AssessResultMapperTest {

    private final AssessResultMapper mapper = new AssessResultMapper();

    // ============ toAssessResponse ============

    @Test
    @DisplayName("toAssessResponse 应映射 L1 → complexity=1 当 level=L1")
    void should_MapL1ToComplexity1_When_LevelIsL1() {
        AssessResponse response = mapper.toAssessResponse(
                ComplexityLevel.L1, "simple task", List.of("query"));

        assertThat(response.getComplexity()).isEqualTo(1);
        assertThat(response.getReason()).isEqualTo("simple task");
        assertThat(response.getSuggestedAbilityTagsList()).containsExactly("query");
    }

    @Test
    @DisplayName("toAssessResponse 应映射 L2 → complexity=2 当 level=L2")
    void should_MapL2ToComplexity2_When_LevelIsL2() {
        AssessResponse response = mapper.toAssessResponse(
                ComplexityLevel.L2, "medium task", List.of("search", "code"));

        assertThat(response.getComplexity()).isEqualTo(2);
        assertThat(response.getReason()).isEqualTo("medium task");
        assertThat(response.getSuggestedAbilityTagsList()).containsExactly("search", "code");
    }

    @Test
    @DisplayName("toAssessResponse 应映射 L3 → complexity=3 当 level=L3")
    void should_MapL3ToComplexity3_When_LevelIsL3() {
        AssessResponse response = mapper.toAssessResponse(
                ComplexityLevel.L3, "complex task", List.of("plan", "execute", "review"));

        assertThat(response.getComplexity()).isEqualTo(3);
        assertThat(response.getReason()).isEqualTo("complex task");
        assertThat(response.getSuggestedAbilityTagsList())
                .containsExactly("plan", "execute", "review");
    }

    @Test
    @DisplayName("toAssessResponse 应抛 IllegalArgumentException 当 level 为 null")
    void should_ThrowIllegalArgumentException_When_LevelIsNull() {
        assertThatThrownBy(() -> mapper.toAssessResponse(null, "reason", List.of("tag")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ComplexityLevel");
    }

    @Test
    @DisplayName("toAssessResponse 应将 null reason 转为空字符串")
    void should_ConvertNullReasonToEmptyString_When_ReasonIsNull() {
        AssessResponse response = mapper.toAssessResponse(
                ComplexityLevel.L1, null, List.of("tag1"));

        assertThat(response.getReason()).isEmpty();
    }

    @Test
    @DisplayName("toAssessResponse 应将 null suggestedTags 转为空列表")
    void should_ConvertNullTagsToEmptyList_When_TagsIsNull() {
        AssessResponse response = mapper.toAssessResponse(
                ComplexityLevel.L2, "reason", null);

        assertThat(response.getSuggestedAbilityTagsList()).isEmpty();
    }

    @Test
    @DisplayName("toAssessResponse 应同时处理 reason 和 suggestedTags 都为 null")
    void should_HandleBothNullReasonAndTags_When_BothAreNull() {
        AssessResponse response = mapper.toAssessResponse(
                ComplexityLevel.L3, null, null);

        assertThat(response.getComplexity()).isEqualTo(3);
        assertThat(response.getReason()).isEmpty();
        assertThat(response.getSuggestedAbilityTagsList()).isEmpty();
    }

    @Test
    @DisplayName("toAssessResponse 应支持空 suggestedTags 列表")
    void should_SupportEmptyTagsList_When_TagsIsEmpty() {
        AssessResponse response = mapper.toAssessResponse(
                ComplexityLevel.L1, "no tools needed", List.of());

        assertThat(response.getSuggestedAbilityTagsList()).isEmpty();
    }

    // ============ toNumeric ============

    @Test
    @DisplayName("toNumeric 应返回 1 当 level=L1")
    void should_Return1_When_LevelIsL1() {
        assertThat(mapper.toNumeric(ComplexityLevel.L1)).isEqualTo(1);
    }

    @Test
    @DisplayName("toNumeric 应返回 2 当 level=L2")
    void should_Return2_When_LevelIsL2() {
        assertThat(mapper.toNumeric(ComplexityLevel.L2)).isEqualTo(2);
    }

    @Test
    @DisplayName("toNumeric 应返回 3 当 level=L3")
    void should_Return3_When_LevelIsL3() {
        assertThat(mapper.toNumeric(ComplexityLevel.L3)).isEqualTo(3);
    }

    @Test
    @DisplayName("toNumeric 返回值应与 toAssessResponse 中的 complexity 一致")
    void should_BeConsistentWithToAssessResponse_When_ToNumericCalled() {
        for (ComplexityLevel level : ComplexityLevel.values()) {
            int numeric = mapper.toNumeric(level);
            AssessResponse response = mapper.toAssessResponse(level, "", List.of());

            assertThat(numeric).as("toNumeric 与 toAssessResponse 一致 level=%s", level)
                    .isEqualTo(response.getComplexity());
        }
    }
}
