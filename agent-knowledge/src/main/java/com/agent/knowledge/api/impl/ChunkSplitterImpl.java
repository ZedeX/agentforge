package com.agent.knowledge.api.impl;

import com.agent.knowledge.api.ChunkSplitter;
import com.agent.knowledge.enums.ChunkStrategyType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory chunk splitter (doc 07-knowledge §4.1).
 *
 * <p>Skeleton stage: char-based token heuristic (4 chars/token). Token strategy splits by
 * maxTokens with overlap; Paragraph strategy splits by double-newline; Fixed splits by char count.
 * Full TokenChunkStrategy + TokenCounter deferred to Plan 08 T9.</p>
 */
@Component
public class ChunkSplitterImpl implements ChunkSplitter {

    private static final int CHARS_PER_TOKEN = 4;
    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final int DEFAULT_OVERLAP = 64;

    @Override
    public List<String> split(String content, ChunkStrategyType strategy, int maxTokens, int overlap) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }
        if (strategy == null) {
            strategy = ChunkStrategyType.TOKEN;
        }
        if (maxTokens <= 0) {
            maxTokens = DEFAULT_MAX_TOKENS;
        }
        if (overlap < 0) {
            overlap = 0;
        }
        // Ensure overlap does not exceed maxTokens
        if (overlap >= maxTokens) {
            overlap = maxTokens / 2;
        }
        switch (strategy) {
            case TOKEN:
                return splitByToken(content, maxTokens, overlap);
            case PARAGRAPH:
                return splitByParagraph(content, maxTokens, overlap);
            case FIXED:
                return splitByFixed(content, maxTokens);
            default:
                return splitByToken(content, maxTokens, overlap);
        }
    }

    private List<String> splitByToken(String content, int maxTokens, int overlap) {
        List<String> chunks = new ArrayList<>();
        int maxChars = maxTokens * CHARS_PER_TOKEN;
        int overlapChars = overlap * CHARS_PER_TOKEN;
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + maxChars, content.length());
            chunks.add(content.substring(start, end));
            if (end >= content.length()) {
                break;
            }
            start = start + maxChars - overlapChars;
            if (start < 0) {
                start = 0;
            }
        }
        return chunks;
    }

    private List<String> splitByParagraph(String content, int maxTokens, int overlap) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split("\\n\\s*\\n");
        int maxChars = maxTokens * CHARS_PER_TOKEN;
        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (current.length() + trimmed.length() + 2 > maxChars && current.length() > 0) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(trimmed);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private List<String> splitByFixed(String content, int maxChars) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + maxChars, content.length());
            chunks.add(content.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
