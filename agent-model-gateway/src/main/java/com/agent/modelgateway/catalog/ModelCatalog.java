package com.agent.modelgateway.catalog;

import agentplatform.model.v1.ModelInfo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型目录（Plan 07 T10，doc 02-api §5 ListModels）。
 *
 * <p>Skeleton stage：维护静态模型列表，供 {@code listModels} RPC 查询。
 * 后续深化可从 model_provider 表 + 模型元数据表加载，或从 provider API 动态发现。</p>
 *
 * <p>tier 分类：</p>
 * <ul>
 *   <li>light：轻量模型（gpt-4o-mini / qwen-turbo / deepseek-chat），意图识别 / 简单任务</li>
 *   <li>middle：中等模型（claude-3.5-sonnet / gemini-1.5-pro），通用任务</li>
 *   <li>strong：强模型（gpt-4o），复杂推理 / 终审</li>
 * </ul>
 */
@Component
public class ModelCatalog {

    /** 静态模型目录（skeleton 阶段硬编码，后续从 DB / 配置加载）。 */
    private static final List<ModelInfo> MODELS = List.of(
            build("gpt-4o", "GPT-4o", "openai", "strong",
                    128_000, true, true, 0.005, 0.015),
            build("gpt-4o-mini", "GPT-4o mini", "openai", "light",
                    128_000, true, true, 0.00015, 0.0006),
            build("claude-3.5-sonnet", "Claude 3.5 Sonnet", "anthropic", "middle",
                    200_000, true, true, 0.003, 0.015),
            build("gemini-1.5-pro", "Gemini 1.5 Pro", "gemini", "middle",
                    1_000_000, true, true, 0.00125, 0.005),
            build("qwen-turbo", "通义千问 Turbo", "qwen", "light",
                    8_000, true, false, 0.0003, 0.0009),
            build("deepseek-chat", "DeepSeek Chat", "deepseek", "light",
                    32_000, true, false, 0.00014, 0.00028)
    );

    /**
     * 列出模型，按 tier 过滤。
     *
     * @param tier light / middle / strong / all（空或不识别视为 all）
     * @return 模型列表（不可变）
     */
    public List<ModelInfo> list(String tier) {
        if (tier == null || tier.isEmpty() || "all".equalsIgnoreCase(tier)) {
            return MODELS;
        }
        return MODELS.stream()
                .filter(m -> tier.equalsIgnoreCase(m.getTier()))
                .collect(Collectors.toList());
    }

    private static ModelInfo build(String modelId, String displayName, String provider, String tier,
                                   int maxContext, boolean streaming, boolean toolCall,
                                   double priceInput, double priceOutput) {
        return ModelInfo.newBuilder()
                .setModelId(modelId)
                .setDisplayName(displayName)
                .setProvider(provider)
                .setTier(tier)
                .setMaxContext(maxContext)
                .setSupportsStreaming(streaming)
                .setSupportsToolCall(toolCall)
                .setPriceInputPer1KCent(priceInput)
                .setPriceOutputPer1KCent(priceOutput)
                .build();
    }
}
