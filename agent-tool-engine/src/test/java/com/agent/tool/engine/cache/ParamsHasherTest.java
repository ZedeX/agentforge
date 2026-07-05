package com.agent.tool.engine.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ParamsHasher} unit tests.
 *
 * <p>Verifies SHA-256 determinism, key-order independence (TreeMap canonicalization),
 * and output format (64-char lowercase hex).
 */
class ParamsHasherTest {

    private static final Pattern HEX_64 = Pattern.compile("^[a-f0-9]{64}$");

    @Test
    @DisplayName("hash: 相同 map → 相同 hash")
    void hash_returnsSameHash_When_SameParams() {
        Map<String, Object> params = new TreeMap<>();
        params.put("a", 1);
        params.put("b", "two");

        String h1 = ParamsHasher.hash(params);
        String h2 = ParamsHasher.hash(params);

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("hash: 不同 key 顺序 → 相同 hash (TreeMap 规范化)")
    void hash_returnsSameHash_When_KeyOrderDiffers() {
        Map<String, Object> map1 = new LinkedHashMap<>();
        map1.put("a", 1);
        map1.put("b", 2);
        map1.put("c", 3);

        Map<String, Object> map2 = new LinkedHashMap<>();
        map2.put("c", 3);
        map2.put("a", 1);
        map2.put("b", 2);

        String h1 = ParamsHasher.hash(map1);
        String h2 = ParamsHasher.hash(map2);

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("hash: 不同 params → 不同 hash")
    void hash_returnsDifferentHash_When_ParamsDiffer() {
        Map<String, Object> map1 = new TreeMap<>();
        map1.put("a", 1);

        Map<String, Object> map2 = new TreeMap<>();
        map2.put("a", 2);

        assertThat(ParamsHasher.hash(map1)).isNotEqualTo(ParamsHasher.hash(map2));
    }

    @Test
    @DisplayName("hash: 空 map → 64 字符 hex")
    void hash_returnsHash_When_EmptyMap() {
        String hash = ParamsHasher.hash(new TreeMap<>());

        assertThat(hash).matches(HEX_64);
    }

    @Test
    @DisplayName("hash: null → 64 字符 hex (fallback 路径)")
    void hash_returnsHash_When_NullParams() {
        String hash = ParamsHasher.hash(null);

        assertThat(hash).matches(HEX_64);
    }

    @Test
    @DisplayName("hash: 嵌套对象 → 64 字符 hex, 相同内容 → 相同 hash")
    void hash_returnsSameHash_When_NestedValuesEquivalent() {
        Map<String, Object> nested1 = new TreeMap<>();
        nested1.put("k", "v");

        Map<String, Object> nested2 = new TreeMap<>();
        nested2.put("k", "v");

        Map<String, Object> params1 = new TreeMap<>();
        params1.put("nested", nested1);

        Map<String, Object> params2 = new TreeMap<>();
        params2.put("nested", nested2);

        assertThat(ParamsHasher.hash(params1)).isEqualTo(ParamsHasher.hash(params2));
    }

    @Test
    @DisplayName("hash: 输出格式为 64 字符小写 hex")
    void hash_outputFormatIs64CharLowerHex() {
        Map<String, Object> params = new TreeMap<>();
        params.put("key", "value");

        String hash = ParamsHasher.hash(params);

        assertThat(hash).matches(HEX_64);
        assertThat(hash).isEqualTo(hash.toLowerCase());
    }
}
