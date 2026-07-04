package com.agent.memory.grpc;

import agentplatform.memory.v1.GetMemoryByIdRequest;
import agentplatform.memory.v1.MemoryRecord;
import agentplatform.memory.v1.RecallRequest;
import agentplatform.memory.v1.RecallResponse;
import agentplatform.memory.v1.RecalledMemory;
import agentplatform.memory.v1.WriteLongTermRequest;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Proto ↔ JPA Entity 映射器（Plan 03 T10）。
 *
 * <p>负责 gRPC proto 消息（{@link WriteLongTermRequest} / {@link RecallRequest} /
 * {@link GetMemoryByIdRequest}）与 JPA Entity {@link com.agent.memory.model.MemoryRecord}
 * 之间的双向转换，以及 {@link RecalledMemory} 构造。</p>
 *
 * <p>字段映射约定：</p>
 * <ul>
 *   <li>{@code agent_id}（proto int64）↔ {@code tenantId}（entity String）：agent 作为租户隔离维度</li>
 *   <li>{@code memory_type}（proto string，小写）↔ {@link MemoryType} 枚举（大写）</li>
 *   <li>{@code tags}（proto repeated string）↔ {@code keywords}（entity JSON 数组字符串）</li>
 *   <li>{@code domain}（proto）↔ {@code topic}（entity）</li>
 *   <li>时间戳：entity {@link Instant} ↔ proto int64 epoch millis</li>
 * </ul>
 */
@Component
public class MemoryRecordMapper {

    // ===== proto request → entity =====

    /**
     * WriteLongTermRequest → MemoryRecord（新建，status=RAW，memoryId 留空由 writer 生成）。
     */
    public com.agent.memory.model.MemoryRecord toEntity(WriteLongTermRequest req) {
        com.agent.memory.model.MemoryRecord entity = new com.agent.memory.model.MemoryRecord();
        entity.setTenantId(String.valueOf(req.getAgentId()));
        entity.setUserId(req.getUserId());
        entity.setTopic(req.getDomain());
        entity.setType(parseMemoryType(req.getMemoryType()));
        entity.setContent(req.getContent());
        entity.setKeywords(formatTags(req.getTagsList()));
        entity.setSourceTaskId(req.getSourceTaskId());
        entity.setStatus(MemoryStatus.RAW);
        return entity;
    }

    // ===== entity → proto =====

    /**
     * MemoryRecord → proto MemoryRecord。
     */
    public MemoryRecord toProto(com.agent.memory.model.MemoryRecord record) {
        MemoryRecord.Builder b = MemoryRecord.newBuilder()
                .setMemoryId(nullToEmpty(record.getMemoryId()))
                .setAgentId(parseLong(record.getTenantId()))
                .setUserId(nullToEmpty(record.getUserId()))
                .setDomain(nullToEmpty(record.getTopic()))
                .setMemoryType(formatMemoryType(record.getType()))
                .setContent(nullToEmpty(record.getContent()))
                .addAllTags(parseTags(record.getKeywords()))
                .setSourceTaskId(nullToEmpty(record.getSourceTaskId()))
                .setImportanceScore(record.getImportanceScore())
                .setAccessCount(record.getRecallCount());
        if (record.getCreatedAt() != null) {
            b.setCreatedAt(record.getCreatedAt().toEpochMilli());
        }
        if (record.getUpdatedAt() != null) {
            b.setUpdatedAt(record.getUpdatedAt().toEpochMilli());
        }
        return b.build();
    }

    /**
     * 构造 RecalledMemory（Recall RPC 返回项）。
     *
     * @param record         命中的记忆记录
     * @param relevanceScore 向量检索相似度分数 [0, 1]
     */
    public RecalledMemory toRecalledMemory(com.agent.memory.model.MemoryRecord record, double relevanceScore) {
        RecalledMemory.Builder b = RecalledMemory.newBuilder()
                .setMemoryId(nullToEmpty(record.getMemoryId()))
                .setContent(nullToEmpty(record.getContent()))
                .setSourceType(mapSourceType(record))
                .setSourceTaskId(nullToEmpty(record.getSourceTaskId()))
                .setImportanceScore(record.getImportanceScore())
                .setRelevanceScore(relevanceScore);
        if (record.getCreatedAt() != null) {
            b.setCreatedAt(record.getCreatedAt().toEpochMilli());
        }
        return b.build();
    }

    /**
     * 构造空 RecallResponse（查无结果时用）。
     */
    public RecallResponse emptyRecallResponse() {
        return RecallResponse.newBuilder()
                .setMeta(agentplatform.memory.v1.RecallMeta.newBuilder()
                        .setTotalHits(0)
                        .setReturned(0)
                        .setTokenUsed(0)
                        .setStrategiesUsed("vector")
                        .build())
                .build();
    }

    // ===== 工具方法 =====

    /** 解析 memory_type 字符串为枚举（大小写不敏感，无效值默认 SEMANTIC）。 */
    public MemoryType parseMemoryType(String value) {
        if (value == null || value.isEmpty()) {
            return MemoryType.SEMANTIC;
        }
        try {
            return MemoryType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MemoryType.SEMANTIC;
        }
    }

    /** 格式化 MemoryType 枚举为 proto 字符串（小写）。 */
    public String formatMemoryType(MemoryType type) {
        return type == null ? "semantic" : type.name().toLowerCase();
    }

    /** tags List → keywords JSON 数组字符串（简化版：用方括号包裹的逗号分隔）。 */
    public String formatTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(tags.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /** keywords JSON 数组字符串 → tags List（容错解析）。 */
    public List<String> parseTags(String keywords) {
        if (keywords == null || keywords.isEmpty() || "[]".equals(keywords.trim())) {
            return new ArrayList<>();
        }
        // 简化解析：去掉方括号 + 引号，按逗号分隔
        String inner = keywords.trim();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);
        if (inner.isEmpty()) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (String part : inner.split(",")) {
            String trimmed = part.trim().replace("\"", "");
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** 推断 source_type：有 sourceTaskId → "task"，否则 → "system"。 */
    private String mapSourceType(com.agent.memory.model.MemoryRecord record) {
        if (record.getSourceTaskId() != null && !record.getSourceTaskId().isEmpty()) {
            return "task";
        }
        return "system";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 安全解析 Long 字符串，失败返回 0。 */
    private static long parseLong(String s) {
        if (s == null || s.isEmpty()) return 0L;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
