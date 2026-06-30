package com.agent.runtime.api.impl;

import com.agent.runtime.api.TokenWatermarkMonitor;
import com.agent.runtime.enums.TokenLevel;
import com.agent.runtime.model.TokenWatermark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Token 水位监控器默认实现 (doc 11-detail-flow F7, PRD §二(三)3).
 *
 * <p>骨架阶段简单实现: checkLevel() 通过 TokenLevel.fromUsageRatio 计算,
 * compress() 根据 level 返回不同压缩级别描述 (SAFE→原样, WARN→light,
 * CRITICAL→medium, CIRCUIT_BREAK→heavy).</p>
 */
@Component
public class TokenWatermarkMonitorImpl implements TokenWatermarkMonitor {

    private static final Logger log = LoggerFactory.getLogger(TokenWatermarkMonitorImpl.class);

    @Override
    public TokenLevel checkLevel(long usedTokens, long maxTokens) {
        double usageRatio = maxTokens > 0 ? (double) usedTokens / maxTokens : 0.0;
        TokenLevel level = TokenLevel.fromUsageRatio(usageRatio);
        log.info("检查 Token 水位: usedTokens={}, maxTokens={}, usageRatio={}, level={}",
                usedTokens, maxTokens, usageRatio, level);
        return level;
    }

    @Override
    public String compress(TokenWatermark watermark) {
        if (watermark == null) {
            log.warn("TokenWatermark 为 null, 不执行压缩");
            return "no_compress:watermark_null";
        }

        TokenLevel level = watermark.getLevel() != null ? watermark.getLevel()
                : TokenLevel.fromUsageRatio(watermark.getUsageRatio());

        String compressed;
        switch (level) {
            case SAFE:
                compressed = watermark.toString() + "|no_compress";
                break;
            case WARN:
                compressed = "light_compress:removed_redundant_fields";
                break;
            case CRITICAL:
                compressed = "medium_compressed:early_dialog_summarized";
                break;
            case CIRCUIT_BREAK:
                compressed = "heavy_compressed:sliding_window_kept_last_K_rounds";
                break;
            default:
                compressed = "unknown_level";
                break;
        }

        log.info("执行上下文压缩: level={}, usedTokens={}, compressed={}",
                level, watermark.getUsedTokens(), compressed);
        return compressed;
    }
}
