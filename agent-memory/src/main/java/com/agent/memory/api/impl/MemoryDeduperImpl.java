package com.agent.memory.api.impl;

import com.agent.memory.api.MemoryDeduper;
import com.agent.memory.model.MemoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * 记忆去重实现（F12.D4: cosine >= 0.92 merge, else insert new）。
 *
 * <p>基于内容 SHA-256 哈希去重（Mock 实现，未做真正余弦相似度）：
 * <ul>
 *   <li>findMaxSimilarity：对 content 计算 SHA-256，与已见哈希集合比对。
 *       完全相同返回 1.0，否则 0.0。本实现为有状态 Mock，会将新内容的哈希登记到内部集合，
 *       以便后续相同内容可被识别为重复。</li>
 *   <li>merge：合并 existing 与 incoming 的 content（以 \n---\n 分隔），
 *       recallCount 取较大值并 +1，importanceScore 取较大值。</li>
 * </ul>
 * 默认去重阈值沿用接口 {@link #dedupThreshold()} = 0.92。</p>
 *
 * @see MemoryDeduper
 */
@Component
public class MemoryDeduperImpl implements MemoryDeduper {

    private static final Logger log = LoggerFactory.getLogger(MemoryDeduperImpl.class);

    /** 已见过的内容哈希集合（Mock 状态）。 */
    private final Set<String> seenHashes = new HashSet<>();

    @Override
    public double findMaxSimilarity(MemoryRecord record) {
        if (record == null || record.getContent() == null) {
            return 0.0;
        }
        String hash = sha256(record.getContent());
        if (seenHashes.contains(hash)) {
            log.debug("发现重复记忆 hash={} contentLen={}",
                    hash, record.getContent().length());
            return 1.0;
        }
        seenHashes.add(hash);
        log.debug("登记新记忆 hash={} contentLen={}",
                hash, record.getContent().length());
        return 0.0;
    }

    @Override
    public MemoryRecord merge(MemoryRecord existing, MemoryRecord incoming) {
        if (existing == null) {
            return incoming;
        }
        if (incoming == null) {
            return existing;
        }
        String mergedContent = existing.getContent()
                + "\n---\n"
                + incoming.getContent();
        existing.setContent(mergedContent);
        existing.setRecallCount(Math.max(existing.getRecallCount(), incoming.getRecallCount()) + 1);
        existing.setImportanceScore(Math.max(existing.getImportanceScore(), incoming.getImportanceScore()));
        log.info("合并记忆 existingId={} incomingId={} mergedLen={}",
                existing.getMemoryId(), incoming.getMemoryId(), mergedContent.length());
        return existing;
    }

    /**
     * 计算字符串的 SHA-256 哈希（小写十六进制，64 位）。
     *
     * @param input 待哈希字符串，null 返回 null
     * @return 十六进制哈希字符串
     */
    static String sha256(String input) {
        if (input == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}