package com.agent.tool.engine.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Hashes tool invocation params to a deterministic SHA-256 digest
 * (doc 05-tool-engine §6).
 *
 * <p>Canonicalization: sort keys ascending (TreeMap), serialize to JSON via
 * Jackson, then SHA-256 the UTF-8 bytes. The resulting hex string is used
 * as the {@code paramsHash} segment of the cache key.</p>
 *
 * <p>Same params (any key order) → same hash → same cache key → cache hit.</p>
 */
public final class ParamsHasher {

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private ParamsHasher() {
    }

    /**
     * Hash the given params map to a 64-char hex SHA-256 digest.
     *
     * @param params tool invocation params (may be empty, must not be null)
     * @return lowercase hex SHA-256 digest
     */
    public static String hash(Map<String, ?> params) {
        if (params == null) {
            return hash("");
        }
        try {
            // TreeMap ensures canonical key ordering regardless of input map type.
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<String, ?> e : params.entrySet()) {
                sorted.put(e.getKey(), e.getValue());
            }
            String json = CANONICAL_MAPPER.writeValueAsString(sorted);
            return hash(json);
        } catch (Exception e) {
            // Fallback: hash the toString of the map (still deterministic per JVM).
            return hash(String.valueOf(params));
        }
    }

    /** SHA-256 of a raw string, returned as 64-char lowercase hex. */
    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
