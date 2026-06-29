package com.agent.runtime.model;

import com.agent.runtime.enums.TokenLevel;

/**
 * Token watermark snapshot (doc 11-detail-flow F7, PRD §二(三)3).
 */
public class TokenWatermark {

    private long usedTokens;
    private long maxTokens;
    private double usageRatio;
    private TokenLevel level;

    public TokenWatermark() {
    }

    public TokenWatermark(long usedTokens, long maxTokens) {
        this.usedTokens = usedTokens;
        this.maxTokens = maxTokens;
        this.usageRatio = maxTokens > 0 ? (double) usedTokens / maxTokens : 0.0;
        this.level = TokenLevel.fromUsageRatio(this.usageRatio);
    }

    public long getUsedTokens() { return usedTokens; }
    public void setUsedTokens(long usedTokens) { this.usedTokens = usedTokens; }

    public long getMaxTokens() { return maxTokens; }
    public void setMaxTokens(long maxTokens) { this.maxTokens = maxTokens; }

    public double getUsageRatio() { return usageRatio; }
    public void setUsageRatio(double usageRatio) { this.usageRatio = usageRatio; }

    public TokenLevel getLevel() { return level; }
    public void setLevel(TokenLevel level) { this.level = level; }
}
