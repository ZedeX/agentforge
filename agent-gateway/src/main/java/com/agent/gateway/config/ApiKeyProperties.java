package com.agent.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API-Key 鉴权配置 (R-01 security hardening).
 *
 * <p>API Key 必须绑定 tenantId，禁止客户端通过 X-Tenant-Id header
 * 自行声明租户（防止跨租户越权）。</p>
 *
 * <p>生产环境通过环境变量注入：
 * <pre>
 * API_KEYS=ak_prod_key_001,ak_prod_key_002
 * API_KEY_TENANTS=ak_prod_key_001=tenant-A,ak_prod_key_002=tenant-B
 * </pre>
 * </p>
 */
@ConfigurationProperties(prefix = "gateway.api-keys")
public class ApiKeyProperties {

    /** Valid API keys. Empty list = API-Key auth disabled. */
    private List<String> validKeys = new ArrayList<>();

    /** API Key → tenantId binding. Key not in map → tenant "default". */
    private Map<String, String> keyToTenantId = new HashMap<>();

    public List<String> getValidKeys() {
        return validKeys;
    }

    public void setValidKeys(List<String> validKeys) {
        this.validKeys = validKeys;
    }

    public Map<String, String> getKeyToTenantId() {
        return keyToTenantId;
    }

    public void setKeyToTenantId(Map<String, String> keyToTenantId) {
        this.keyToTenantId = keyToTenantId;
    }
}
