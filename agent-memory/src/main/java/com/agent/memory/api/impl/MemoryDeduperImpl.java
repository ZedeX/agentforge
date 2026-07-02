package com.agent.memory.api.impl;

import com.agent.memory.api.MemoryDeduper;
import com.agent.memory.config.MemoryProperties;
import com.agent.memory.model.DedupReport;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.repository.MemoryRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 记忆去重实现（F12.D4 + Plan 03 T9: hash dedup + batch deduplication）。
 *
 * <p>基于内容 SHA-256 哈希去重：
 * <ul>
 *   <li>findMaxSimilarity：查询 Repository 中是否存在相同 contentHash</li>
 *   <li>merge：合并 existing 与 incoming 的 content（以 \n---\n 分隔），
 *       recallCount 取较大值并 +1，importanceScore 取较大值</li>
 *   <li>dedup：批量去重，同 contentHash 分组，保留 createdAt 最早的，其余丢弃</li>
 * </ul>
 *
 * @see MemoryDeduper
 * @see DedupReport
 */
@Component
public class MemoryDeduperImpl implements MemoryDeduper {

    private static final Logger log = LoggerFactory.getLogger(MemoryDeduperImpl.class);

    private final MemoryRecordRepository repository;
    private final MemoryProperties properties;

    public MemoryDeduperImpl(MemoryRecordRepository repository, MemoryProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public double findMaxSimilarity(MemoryRecord record) {
        if (record == null || record.getContentHash() == null) {
            return 0.0;
        }
        Optional<MemoryRecord> existing = repository.findByContentHash(record.getContentHash());
        if (existing.isPresent()) {
            log.debug("发现重复记忆 hash={} memoryId={}", record.getContentHash(), existing.get().getMemoryId());
            return 1.0;
        }
        log.debug("未发现重复记忆 hash={}", record.getContentHash());
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

    @Override
    public DedupReport dedup(List<MemoryRecord> batch) {
        if (batch == null || batch.isEmpty()) {
            return new DedupReport(0, 0, 0, 0);
        }

        int dropped = 0;
        int kept = 0;

        // 按 contentHash 分组
        Map<String, List<MemoryRecord>> groups = new HashMap<>();
        for (MemoryRecord record : batch) {
            String hash = record.getContentHash();
            if (hash == null) {
                // 无 hash 的记录直接保留
                kept++;
                continue;
            }
            groups.computeIfAbsent(hash, k -> new ArrayList<>()).add(record);
        }

        // 同 hash 分组内保留 createdAt 最早的，其余丢弃
        for (Map.Entry<String, List<MemoryRecord>> entry : groups.entrySet()) {
            List<MemoryRecord> group = entry.getValue();
            if (group.size() <= 1) {
                kept += group.size();
                continue;
            }

            // 按 createdAt 升序排序（最早的保留）
            group.sort(Comparator.comparing(r -> r.getCreatedAt() != null ? r.getCreatedAt() : Instant.now()));

            MemoryRecord keeper = group.get(0);
            for (int i = 1; i < group.size(); i++) {
                MemoryRecord duplicate = group.get(i);
                log.info("去重丢弃 hash={} keeperId={} duplicateId={}",
                        entry.getKey(), keeper.getMemoryId(), duplicate.getMemoryId());
                dropped++;
            }
            kept++;
        }

        log.info("批量去重完成 total={} dropped={} kept={}", batch.size(), dropped, kept);
        return new DedupReport(dropped, 0, 0, kept);
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
