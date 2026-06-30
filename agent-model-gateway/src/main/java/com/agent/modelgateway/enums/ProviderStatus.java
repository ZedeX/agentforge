package com.agent.modelgateway.enums;

/**
 * Provider health status for degradation state machine (doc 02-api §7, PRD §二(二)4 failover).
 *
 * <p>ACTIVE: 正常服务, 健康度达标
 * DEGRADED: 连续失败达阈值 (默认 3 次), 切换到备用 provider, 进入冷却期 (默认 5min)
 * RECOVERING: 冷却期结束, 尝试回切探测请求, 成功则恢复 ACTIVE</p>
 */
public enum ProviderStatus {

    ACTIVE("active", "正常服务"),
    DEGRADED("degraded", "已降级, 切换备用"),
    RECOVERING("recovering", "冷却结束, 探测回切中");

    private final String code;
    private final String description;

    ProviderStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse status from code string (case-insensitive).
     *
     * @param code status code (active / degraded / recovering)
     * @return ProviderStatus enum, default ACTIVE if null/unknown
     */
    public static ProviderStatus fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return ACTIVE;
        }
        String lower = code.toLowerCase();
        for (ProviderStatus s : values()) {
            if (s.code.equals(lower)) {
                return s;
            }
        }
        return ACTIVE;
    }
}
