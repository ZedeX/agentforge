package com.agent.memory.extractor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Memory extraction content filter rules (Plan 03 T3).
 *
 * <p>Filters low-quality content before extraction:
 * <ul>
 *   <li>Length &lt; {@code minContentLength} (default 20) → filter</li>
 *   <li>Contains any blacklist keyword → filter</li>
 * </ul>
 *
 * <p>Configurable via {@code application.yml}:
 * <pre>
 * memory:
 *   extract:
 *     min-content-length: 20
 *     blacklist-keywords: "spam,test,placeholder"
 * </pre>
 */
@Component
public class MemoryExtractRule {

    /** Minimum content length to pass filter. */
    private final int minContentLength;

    /** Blacklist keywords (comma-separated in config). */
    private final Set<String> blacklistKeywords;

    public MemoryExtractRule(
            @Value("${memory.extract.min-content-length:20}") int minContentLength,
            @Value("${memory.extract.blacklist-keywords:}") String blacklistKeywords) {
        this.minContentLength = minContentLength;
        this.blacklistKeywords = parseKeywords(blacklistKeywords);
    }

    /**
     * Returns true if content should be filtered out (low quality).
     */
    public boolean shouldFilter(String content) {
        if (content == null || content.trim().length() < minContentLength) {
            return true;
        }
        String lower = content.toLowerCase();
        for (String keyword : blacklistKeywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public int getMinContentLength() {
        return minContentLength;
    }

    public Set<String> getBlacklistKeywords() {
        return blacklistKeywords;
    }

    private static Set<String> parseKeywords(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Set.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }
}
