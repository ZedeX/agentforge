package com.agent.modelgateway.repository;

import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.ModelRouteRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ModelRouteRuleRepository JPA integration tests (Plan 07 T3).
 *
 * <p>Verifies scene-based rule lookup ordered by priority (UT-MG-001 / UT-MG-002
 * routing logic backed by repository query).</p>
 */
@DisplayName("ModelRouteRuleRepository JPA 仓储测试")
@DataJpaTest
@ActiveProfiles("test")
class ModelRouteRuleRepositoryTest {

    @Autowired
    private ModelRouteRuleRepository repository;

    private ModelRouteRule buildRule(Scene scene, int priority, String primary, String fallback, boolean enabled) {
        ModelRouteRule rule = new ModelRouteRule(scene, priority, primary, fallback);
        rule.setEnabled(enabled);
        rule.setCostCeilingUsd(1.0);
        return rule;
    }

    @Test
    @DisplayName("findBySceneAndEnabledTrueOrderByPriorityAsc 按优先级升序返回启用的规则")
    void should_ReturnEnabledRulesOrderedByPriority_When_QueryByScene() {
        repository.save(buildRule(Scene.GENERIC, 50, "generic-50", "fallback-50", true));
        repository.save(buildRule(Scene.GENERIC, 10, "generic-10", "fallback-10", true));
        repository.save(buildRule(Scene.GENERIC, 30, "generic-30", "fallback-30", false));
        repository.save(buildRule(Scene.INTENT, 5, "intent-5", "fallback-intent", true));

        List<ModelRouteRule> genericRules = repository.findBySceneAndEnabledTrueOrderByPriorityAsc(Scene.GENERIC);

        assertThat(genericRules).hasSize(2);
        assertThat(genericRules.get(0).getPrimaryProviderCode()).isEqualTo("generic-10");
        assertThat(genericRules.get(1).getPrimaryProviderCode()).isEqualTo("generic-50");
    }

    @Test
    @DisplayName("INTENT 场景查询返回轻量模型规则 (UT-MG-001 路由数据)")
    void should_ReturnIntentRules_When_SceneIsIntent() {
        repository.save(buildRule(Scene.INTENT, 1, "openai-mini", "qwen-turbo", true));
        repository.save(buildRule(Scene.AUDIT, 1, "anthropic", "openai", true));

        List<ModelRouteRule> intentRules = repository.findBySceneAndEnabledTrueOrderByPriorityAsc(Scene.INTENT);

        assertThat(intentRules).hasSize(1);
        assertThat(intentRules.get(0).getPrimaryProviderCode()).isEqualTo("openai-mini");
        assertThat(intentRules.get(0).getFallbackProviderCode()).isEqualTo("qwen-turbo");
    }

    @Test
    @DisplayName("AUDIT 场景查询返回强模型规则 (UT-MG-002 路由数据)")
    void should_ReturnAuditRules_When_SceneIsAudit() {
        repository.save(buildRule(Scene.INTENT, 1, "openai-mini", "qwen-turbo", true));
        repository.save(buildRule(Scene.AUDIT, 1, "anthropic", "openai", true));

        List<ModelRouteRule> auditRules = repository.findBySceneAndEnabledTrueOrderByPriorityAsc(Scene.AUDIT);

        assertThat(auditRules).hasSize(1);
        assertThat(auditRules.get(0).getPrimaryProviderCode()).isEqualTo("anthropic");
        assertThat(auditRules.get(0).getFallbackProviderCode()).isEqualTo("openai");
    }

    @Test
    @DisplayName("findByEnabledTrueOrderByPriorityAsc 返回全部启用规则按优先级排序")
    void should_ReturnAllEnabledRules_When_FindByEnabledTrue() {
        repository.save(buildRule(Scene.GENERIC, 30, "g-30", "g-fb-30", true));
        repository.save(buildRule(Scene.INTENT, 5, "i-5", "i-fb-5", true));
        repository.save(buildRule(Scene.GENERIC, 10, "g-10", "g-fb-10", false));

        List<ModelRouteRule> all = repository.findByEnabledTrueOrderByPriorityAsc();

        assertThat(all).hasSize(2);
        assertThat(all.get(0).getPriority()).isLessThanOrEqualTo(all.get(1).getPriority());
    }

    @Test
    @DisplayName("countBySceneAndEnabledTrue 统计场景下启用规则数")
    void should_CountEnabledRules_When_QueryByScene() {
        repository.save(buildRule(Scene.GENERIC, 10, "g1", "fb1", true));
        repository.save(buildRule(Scene.GENERIC, 20, "g2", "fb2", true));
        repository.save(buildRule(Scene.GENERIC, 30, "g3", "fb3", false));

        long count = repository.countBySceneAndEnabledTrue(Scene.GENERIC);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("空场景查询返回空列表")
    void should_ReturnEmptyList_When_NoRulesForScene() {
        repository.save(buildRule(Scene.INTENT, 1, "i", "fb", true));

        List<ModelRouteRule> auditRules = repository.findBySceneAndEnabledTrueOrderByPriorityAsc(Scene.AUDIT);

        assertThat(auditRules).isEmpty();
    }
}
