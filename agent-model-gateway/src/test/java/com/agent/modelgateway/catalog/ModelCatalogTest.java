package com.agent.modelgateway.catalog;

import agentplatform.model.v1.ModelInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModelCatalog} 单测（Plan 07 T10）。
 *
 * <p>验证静态模型目录的 tier 过滤逻辑：all/light/middle/strong/空/未识别。</p>
 */
@DisplayName("ModelCatalog 模型目录 tier 过滤（Plan 07 T10）")
class ModelCatalogTest {

    private final ModelCatalog catalog = new ModelCatalog();

    @Test
    @DisplayName("list(all): 返回全部 6 个模型")
    void should_ReturnAllModels_When_TierAll() {
        List<ModelInfo> models = catalog.list("all");
        assertThat(models).hasSize(6);
        assertThat(models).extracting(ModelInfo::getModelId)
                .containsExactly("gpt-4o", "gpt-4o-mini", "claude-3.5-sonnet",
                        "gemini-1.5-pro", "qwen-turbo", "deepseek-chat");
    }

    @Test
    @DisplayName("list(light): 返回 3 个轻量模型")
    void should_ReturnLightModels_When_TierLight() {
        List<ModelInfo> models = catalog.list("light");
        assertThat(models).hasSize(3);
        assertThat(models).extracting(ModelInfo::getTier).containsOnly("light");
        assertThat(models).extracting(ModelInfo::getModelId)
                .containsExactlyInAnyOrder("gpt-4o-mini", "qwen-turbo", "deepseek-chat");
    }

    @Test
    @DisplayName("list(middle): 返回 2 个中等模型")
    void should_ReturnMiddleModels_When_TierMiddle() {
        List<ModelInfo> models = catalog.list("middle");
        assertThat(models).hasSize(2);
        assertThat(models).extracting(ModelInfo::getModelId)
                .containsExactlyInAnyOrder("claude-3.5-sonnet", "gemini-1.5-pro");
    }

    @Test
    @DisplayName("list(strong): 返回 1 个强模型")
    void should_ReturnStrongModels_When_TierStrong() {
        List<ModelInfo> models = catalog.list("strong");
        assertThat(models).hasSize(1);
        assertThat(models.get(0).getModelId()).isEqualTo("gpt-4o");
    }

    @Test
    @DisplayName("list(null): 视为 all，返回全部")
    void should_ReturnAllModels_When_TierNull() {
        List<ModelInfo> models = catalog.list(null);
        assertThat(models).hasSize(6);
    }

    @Test
    @DisplayName("list(空字符串): 视为 all，返回全部")
    void should_ReturnAllModels_When_TierEmpty() {
        List<ModelInfo> models = catalog.list("");
        assertThat(models).hasSize(6);
    }

    @Test
    @DisplayName("list(未识别 tier): 返回空列表")
    void should_ReturnEmpty_When_TierUnknown() {
        List<ModelInfo> models = catalog.list("unknown");
        assertThat(models).isEmpty();
    }

    @Test
    @DisplayName("list(ALL 大写): 大小写不敏感，返回全部")
    void should_ReturnAllModels_When_TierUpperCase() {
        List<ModelInfo> models = catalog.list("ALL");
        assertThat(models).hasSize(6);
    }

    @Test
    @DisplayName("list(LIGHT 大写): 大小写不敏感，返回轻量模型")
    void should_ReturnLightModels_When_TierUpperCase() {
        List<ModelInfo> models = catalog.list("LIGHT");
        assertThat(models).hasSize(3);
        assertThat(models).extracting(ModelInfo::getTier).containsOnly("light");
    }

    @Test
    @DisplayName("模型字段完整性: gpt-4o 含 provider/tier/max_context/streaming/tool_call/price")
    void should_HaveCompleteFields_When_Gpt4o() {
        List<ModelInfo> models = catalog.list("strong");
        ModelInfo gpt4o = models.get(0);
        assertThat(gpt4o.getProvider()).isEqualTo("openai");
        assertThat(gpt4o.getMaxContext()).isEqualTo(128_000);
        assertThat(gpt4o.getSupportsStreaming()).isTrue();
        assertThat(gpt4o.getSupportsToolCall()).isTrue();
        assertThat(gpt4o.getPriceInputPer1KCent()).isEqualTo(0.005);
        assertThat(gpt4o.getPriceOutputPer1KCent()).isEqualTo(0.015);
        assertThat(gpt4o.getDisplayName()).isEqualTo("GPT-4o");
    }
}
