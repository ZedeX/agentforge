package com.agent.planning.api.impl;

import com.agent.planning.model.PlanTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TemplateMatcherImpl unit tests (doc 03-task-engine §8.2 TemplateMatcher).
 */
@DisplayName("TemplateMatcherImpl 模板匹配器")
class TemplateMatcherImplTest {

    private final TemplateMatcherImpl matcher = new TemplateMatcherImpl();

    @Test
    @DisplayName("场景标签匹配时返回对应模板")
    void should_ReturnTemplate_When_TagsMatch() {
        PlanTemplate tpl = new PlanTemplate("周报模板", List.of("weekly-report"), "{}");
        tpl.setSuccessRate(0.9);
        matcher.register(tpl);

        Optional<PlanTemplate> result = matcher.match("schema", List.of("weekly-report"));
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("周报模板");
    }

    @Test
    @DisplayName("标签不匹配返回 empty")
    void should_ReturnEmpty_When_NoTagsMatch() {
        PlanTemplate tpl = new PlanTemplate("周报模板", List.of("weekly-report"), "{}");
        tpl.setSuccessRate(0.9);
        matcher.register(tpl);

        Optional<PlanTemplate> result = matcher.match("schema", List.of("order-query"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 场景标签返回 empty")
    void should_ReturnEmpty_When_ScenarioTagsNull() {
        PlanTemplate tpl = new PlanTemplate("周报模板", List.of("weekly-report"), "{}");
        tpl.setSuccessRate(0.9);
        matcher.register(tpl);

        Optional<PlanTemplate> result = matcher.match("schema", null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空场景标签列表返回 empty")
    void should_ReturnEmpty_When_ScenarioTagsEmpty() {
        PlanTemplate tpl = new PlanTemplate("周报模板", List.of("weekly-report"), "{}");
        tpl.setSuccessRate(0.9);
        matcher.register(tpl);

        Optional<PlanTemplate> result = matcher.match("schema", List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("无注册模板时返回 empty")
    void should_ReturnEmpty_When_NoTemplatesRegistered() {
        Optional<PlanTemplate> result = matcher.match("schema", List.of("any-tag"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("成功率低于阈值 (0.6) 的模板被过滤")
    void should_FilterOutTemplate_When_SuccessRateBelowThreshold() {
        PlanTemplate tpl = new PlanTemplate("低质量模板", List.of("weekly-report"), "{}");
        tpl.setSuccessRate(0.5);  // < 0.6
        matcher.register(tpl);

        Optional<PlanTemplate> result = matcher.match("schema", List.of("weekly-report"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("禁用的模板被过滤")
    void should_FilterOutTemplate_When_Disabled() {
        PlanTemplate tpl = new PlanTemplate("禁用模板", List.of("weekly-report"), "{}");
        tpl.setSuccessRate(0.9);
        tpl.setEnabled(false);
        matcher.register(tpl);

        Optional<PlanTemplate> result = matcher.match("schema", List.of("weekly-report"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("多个匹配模板返回成功率最高的")
    void should_ReturnHighestSuccessRate_When_MultipleMatch() {
        PlanTemplate low = new PlanTemplate("低成功率", List.of("report"), "{}");
        low.setSuccessRate(0.7);
        PlanTemplate high = new PlanTemplate("高成功率", List.of("report"), "{}");
        high.setSuccessRate(0.95);
        matcher.register(low);
        matcher.register(high);

        Optional<PlanTemplate> result = matcher.match("schema", List.of("report"));
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("高成功率");
    }

    @Test
    @DisplayName("成功率相同时, useCount 高的优先")
    void should_ReturnHigherUseCount_When_SuccessRateEqual() {
        PlanTemplate less = new PlanTemplate("少用", List.of("report"), "{}");
        less.setSuccessRate(0.9);
        less.setUseCount(5);
        PlanTemplate more = new PlanTemplate("多用", List.of("report"), "{}");
        more.setSuccessRate(0.9);
        more.setUseCount(100);
        matcher.register(less);
        matcher.register(more);

        Optional<PlanTemplate> result = matcher.match("schema", List.of("report"));
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("多用");
    }

    @Test
    @DisplayName("大小写不敏感标签匹配")
    void should_MatchCaseInsensitively_When_TagsDifferentCase() {
        PlanTemplate tpl = new PlanTemplate("模板", List.of("Weekly-Report"), "{}");
        tpl.setSuccessRate(0.9);
        matcher.register(tpl);

        Optional<PlanTemplate> result = matcher.match("schema", List.of("WEEKLY-REPORT"));
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("register null 模板或 null 名称安全跳过")
    void should_SkipRegistration_When_TemplateOrNameNull() {
        matcher.register(null);
        PlanTemplate nullName = new PlanTemplate(null, List.of("tag"), "{}");
        matcher.register(nullName);
        // No crash; matcher still empty
        assertThat(matcher.match("schema", List.of("tag"))).isEmpty();
    }

    @Test
    @DisplayName("模板 scenarioTags 为空时不参与匹配")
    void should_SkipTemplate_When_TemplateTagsEmpty() {
        PlanTemplate tpl = new PlanTemplate("空标签模板", List.of(), "{}");
        tpl.setSuccessRate(0.9);
        matcher.register(tpl);

        assertThat(matcher.match("schema", List.of("any"))).isEmpty();
    }
}
