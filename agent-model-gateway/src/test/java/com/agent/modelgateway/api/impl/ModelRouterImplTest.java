package com.agent.modelgateway.api.impl;

import com.agent.modelgateway.enums.Scene;
import com.agent.modelgateway.model.RouteResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ModelRouterImpl unit tests (doc 02-api §3).
 */
@DisplayName("ModelRouterImpl 模型路由器")
class ModelRouterImplTest {

    private final ModelRouterImpl router = new ModelRouterImpl();

    @Test
    @DisplayName("INTENT 场景路由到轻量模型")
    void should_RouteToLightModel_When_SceneIntent() {
        RouteResult result = router.route(Scene.INTENT, null);
        assertThat(result.getPrimaryProviderCode()).isEqualTo("openai-mini");
        assertThat(result.getFallbackProviderCode()).isEqualTo("qwen-turbo");
        assertThat(result.hasFallback()).isTrue();
    }

    @Test
    @DisplayName("AUDIT 场景路由到强模型")
    void should_RouteToStrongModel_When_SceneAudit() {
        RouteResult result = router.route(Scene.AUDIT, null);
        assertThat(result.getPrimaryProviderCode()).isEqualTo("anthropic");
        assertThat(result.getFallbackProviderCode()).isEqualTo("openai");
    }

    @Test
    @DisplayName("preferredModel 优先级最高, 覆盖路由规则")
    void should_PreferUserSpecifiedModel_When_PreferredModelNotNull() {
        RouteResult result = router.route(Scene.GENERIC, "custom-model");
        assertThat(result.getPrimaryProviderCode()).isEqualTo("custom-model");
        assertThat(result.hasFallback()).isFalse();
    }

    @Test
    @DisplayName("null scene 兜底到 GENERIC 默认路由")
    void should_FallbackToGeneric_When_SceneNull() {
        RouteResult result = router.route(null, null);
        assertThat(result.getPrimaryProviderCode()).isNotEmpty();
    }

    @Test
    @DisplayName("addRule 注册自定义规则后, route 匹配新规则")
    void should_MatchCustomRule_When_AddedAndRouteCalled() {
        com.agent.modelgateway.model.ModelRouteRule custom =
                new com.agent.modelgateway.model.ModelRouteRule(Scene.GENERIC, 1, "custom-primary", "custom-fallback");
        router.addRule(custom);
        // priority 1 < default GENERIC priority 10, so custom wins
        RouteResult result = router.route(Scene.GENERIC, null);
        assertThat(result.getPrimaryProviderCode()).isEqualTo("custom-primary");
        assertThat(result.getFallbackProviderCode()).isEqualTo("custom-fallback");
    }

    @Test
    @DisplayName("addRule null 或 null scene 安全跳过, 不影响现有规则")
    void should_SkipNullRule_When_AddRuleCalledWithNull() {
        router.addRule(null);
        com.agent.modelgateway.model.ModelRouteRule nullSceneRule =
                new com.agent.modelgateway.model.ModelRouteRule(null, 1, "p", "f");
        router.addRule(nullSceneRule);
        // existing rules still work
        RouteResult result = router.route(Scene.GENERIC, null);
        assertThat(result.getPrimaryProviderCode()).isNotEmpty();
    }

    @Test
    @DisplayName("empty preferredModel 走默认路由 (不覆盖)")
    void should_UseDefaultRouting_When_PreferredModelEmpty() {
        RouteResult result = router.route(Scene.GENERIC, "");
        assertThat(result.getPrimaryProviderCode()).isNotEqualTo("");
        assertThat(result.hasFallback()).isTrue();
    }
}
