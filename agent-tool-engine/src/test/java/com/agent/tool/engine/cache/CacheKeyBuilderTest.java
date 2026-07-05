package com.agent.tool.engine.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CacheKeyBuilder} unit tests.
 *
 * <p>Verifies deterministic key format, glob pattern generation, and
 * argument validation. Plan 05 T7 Red case: {@code cacheKey_isDeterministic}.
 */
class CacheKeyBuilderTest {

    @Test
    @DisplayName("build: 三段非空 → tool:cache:{toolId}:{paramsHash}:{tenantId}")
    void build_returnsCorrectFormat_When_AllSegmentsProvided() {
        String key = CacheKeyBuilder.build("tool_1", "hash_1", "tn_1");

        assertThat(key).isEqualTo("tool:cache:tool_1:hash_1:tn_1");
    }

    @Test
    @DisplayName("build_isDeterministic: 相同输入 → 相同输出")
    void build_isDeterministic() {
        String k1 = CacheKeyBuilder.build("tool_x", "h", "tn_y");
        String k2 = CacheKeyBuilder.build("tool_x", "h", "tn_y");

        assertThat(k1).isEqualTo(k2);
    }

    @Test
    @DisplayName("build: 不同 toolId/paramsHash/tenantId → 不同 key")
    void build_returnsDifferentKeys_When_SegmentsDiffer() {
        String k1 = CacheKeyBuilder.build("tool_a", "h", "tn");
        String k2 = CacheKeyBuilder.build("tool_b", "h", "tn");
        String k3 = CacheKeyBuilder.build("tool_a", "h2", "tn");
        String k4 = CacheKeyBuilder.build("tool_a", "h", "tn2");

        assertThat(k1).isNotEqualTo(k2);
        assertThat(k1).isNotEqualTo(k3);
        assertThat(k1).isNotEqualTo(k4);
    }

    @Test
    @DisplayName("build: toolId null/empty/blank → IllegalArgumentException")
    void build_throws_When_ToolIdBlank() {
        assertThatThrownBy(() -> CacheKeyBuilder.build(null, "h", "tn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolId");

        assertThatThrownBy(() -> CacheKeyBuilder.build("", "h", "tn"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> CacheKeyBuilder.build("   ", "h", "tn"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("build: paramsHash null/empty/blank → IllegalArgumentException")
    void build_throws_When_ParamsHashBlank() {
        assertThatThrownBy(() -> CacheKeyBuilder.build("tool", null, "tn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paramsHash");

        assertThatThrownBy(() -> CacheKeyBuilder.build("tool", "", "tn"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> CacheKeyBuilder.build("tool", "  ", "tn"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("build: tenantId null/empty/blank → IllegalArgumentException")
    void build_throws_When_TenantIdBlank() {
        assertThatThrownBy(() -> CacheKeyBuilder.build("tool", "h", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        assertThatThrownBy(() -> CacheKeyBuilder.build("tool", "h", ""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> CacheKeyBuilder.build("tool", "h", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("patternForTool: 返回 tool:cache:{toolId}:* 形式")
    void patternForTool_returnsGlobPattern() {
        String pattern = CacheKeyBuilder.patternForTool("tool_abc");

        assertThat(pattern).isEqualTo("tool:cache:tool_abc:*");
    }

    @Test
    @DisplayName("patternForToolAndTenant: 返回 tool:cache:{toolId}:*:{tenantId} 形式")
    void patternForToolAndTenant_returnsGlobPattern() {
        String pattern = CacheKeyBuilder.patternForToolAndTenant("tool_abc", "tn_42");

        assertThat(pattern).isEqualTo("tool:cache:tool_abc:*:tn_42");
    }

    @Test
    @DisplayName("KEY_PREFIX 常量: tool:cache:")
    void keyPrefix_constant() {
        assertThat(CacheKeyBuilder.KEY_PREFIX).isEqualTo("tool:cache:");
    }

    @Test
    @DisplayName("patternForTool matches actual built key via glob semantics")
    void patternForTool_matchesBuiltKey_When_SameToolId() {
        String key = CacheKeyBuilder.build("tool_match", "hash_x", "tn_y");
        String pattern = CacheKeyBuilder.patternForTool("tool_match");

        // Simple glob → regex check (matches ToolCacheImpl.invalidateCaffeine logic)
        String regex = pattern.replace("*", ".*");
        assertThat(key.matches(regex)).isTrue();

        String otherKey = CacheKeyBuilder.build("tool_other", "hash_x", "tn_y");
        assertThat(otherKey.matches(regex)).isFalse();
    }
}
