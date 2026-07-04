package com.agent.memory.grpc;

import agentplatform.memory.v1.MemoryRecord;
import agentplatform.memory.v1.RecallResponse;
import agentplatform.memory.v1.RecalledMemory;
import agentplatform.memory.v1.WriteLongTermRequest;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MemoryRecordMapper} 单测（Plan 03 T10）。
 *
 * <p>覆盖 proto ↔ JPA Entity 双向映射的所有分支：
 * <ul>
 *   <li>{@code parseMemoryType}：null / 空 / 大写 / 小写 / 无效值</li>
 *   <li>{@code formatMemoryType}：null / 有效值</li>
 *   <li>{@code formatTags}：null / 空 / 单个 / 多个 / 含引号</li>
 *   <li>{@code parseTags}：null / 空 / [] / 单个 / 多个 / 带引号 / 内部为空</li>
 *   <li>{@code toEntity}：完整字段映射</li>
 *   <li>{@code toProto}：null 字段 / 时间戳存在与否 / tenantId 解析</li>
 *   <li>{@code toRecalledMemory}：source_type 推断 / 时间戳</li>
 *   <li>{@code emptyRecallResponse}：meta 字段验证</li>
 * </ul>
 */
@DisplayName("MemoryRecordMapper proto ↔ entity 映射（T10）")
class MemoryRecordMapperTest {

    private final MemoryRecordMapper mapper = new MemoryRecordMapper();

    // ===== parseMemoryType =====

    @Test
    @DisplayName("parseMemoryType 对 null 应返回 SEMANTIC 默认值")
    void should_ReturnSemantic_When_ParseNullType() {
        assertThat(mapper.parseMemoryType(null)).isEqualTo(MemoryType.SEMANTIC);
    }

    @Test
    @DisplayName("parseMemoryType 对空字符串应返回 SEMANTIC 默认值")
    void should_ReturnSemantic_When_ParseEmptyType() {
        assertThat(mapper.parseMemoryType("")).isEqualTo(MemoryType.SEMANTIC);
    }

    @Test
    @DisplayName("parseMemoryType 对小写应正确解析为对应枚举")
    void should_ParseLowercase_When_ValidType() {
        assertThat(mapper.parseMemoryType("episodic")).isEqualTo(MemoryType.EPISODIC);
        assertThat(mapper.parseMemoryType("semantic")).isEqualTo(MemoryType.SEMANTIC);
        assertThat(mapper.parseMemoryType("procedural")).isEqualTo(MemoryType.PROCEDURAL);
        assertThat(mapper.parseMemoryType("reflective")).isEqualTo(MemoryType.REFLECTIVE);
    }

    @Test
    @DisplayName("parseMemoryType 对大写应正确解析为对应枚举")
    void should_ParseUppercase_When_ValidType() {
        assertThat(mapper.parseMemoryType("EPISODIC")).isEqualTo(MemoryType.EPISODIC);
        assertThat(mapper.parseMemoryType("SEMANTIC")).isEqualTo(MemoryType.SEMANTIC);
    }

    @Test
    @DisplayName("parseMemoryType 对无效值应返回 SEMANTIC 默认值")
    void should_ReturnSemantic_When_InvalidType() {
        assertThat(mapper.parseMemoryType("invalid_type")).isEqualTo(MemoryType.SEMANTIC);
        assertThat(mapper.parseMemoryType("unknown")).isEqualTo(MemoryType.SEMANTIC);
    }

    // ===== formatMemoryType =====

    @Test
    @DisplayName("formatMemoryType 对 null 应返回 'semantic'")
    void should_ReturnSemanticString_When_FormatNullType() {
        assertThat(mapper.formatMemoryType(null)).isEqualTo("semantic");
    }

    @Test
    @DisplayName("formatMemoryType 对有效枚举应返回小写字符串")
    void should_ReturnLowercase_When_FormatValidType() {
        assertThat(mapper.formatMemoryType(MemoryType.EPISODIC)).isEqualTo("episodic");
        assertThat(mapper.formatMemoryType(MemoryType.SEMANTIC)).isEqualTo("semantic");
        assertThat(mapper.formatMemoryType(MemoryType.PROCEDURAL)).isEqualTo("procedural");
        assertThat(mapper.formatMemoryType(MemoryType.REFLECTIVE)).isEqualTo("reflective");
    }

    // ===== formatTags =====

    @Test
    @DisplayName("formatTags 对 null 应返回 '[]'")
    void should_ReturnEmptyArray_When_FormatNullTags() {
        assertThat(mapper.formatTags(null)).isEqualTo("[]");
    }

    @Test
    @DisplayName("formatTags 对空列表应返回 '[]'")
    void should_ReturnEmptyArray_When_FormatEmptyTags() {
        assertThat(mapper.formatTags(Collections.emptyList())).isEqualTo("[]");
    }

    @Test
    @DisplayName("formatTags 对单个 tag 应返回单元素 JSON 数组")
    void should_ReturnSingleElement_When_FormatSingleTag() {
        assertThat(mapper.formatTags(List.of("java"))).isEqualTo("[\"java\"]");
    }

    @Test
    @DisplayName("formatTags 对多个 tag 应返回逗号分隔 JSON 数组")
    void should_ReturnMultiElement_When_FormatMultipleTags() {
        assertThat(mapper.formatTags(Arrays.asList("java", "spring")))
                .isEqualTo("[\"java\",\"spring\"]");
    }

    @Test
    @DisplayName("formatTags 对含双引号的 tag 应转义")
    void should_EscapeQuotes_When_TagContainsQuotes() {
        String result = mapper.formatTags(List.of("tag\"with\"quotes"));
        assertThat(result).isEqualTo("[\"tag\\\"with\\\"quotes\"]");
    }

    // ===== parseTags =====

    @Test
    @DisplayName("parseTags 对 null 应返回空列表")
    void should_ReturnEmptyList_When_ParseNullKeywords() {
        assertThat(mapper.parseTags(null)).isEmpty();
    }

    @Test
    @DisplayName("parseTags 对空字符串应返回空列表")
    void should_ReturnEmptyList_When_ParseEmptyKeywords() {
        assertThat(mapper.parseTags("")).isEmpty();
    }

    @Test
    @DisplayName("parseTags 对 '[]' 应返回空列表")
    void should_ReturnEmptyList_When_ParseEmptyArray() {
        assertThat(mapper.parseTags("[]")).isEmpty();
        assertThat(mapper.parseTags(" [] ")).isEmpty();
    }

    @Test
    @DisplayName("parseTags 对单元素 JSON 数组应返回单元素列表")
    void should_ReturnSingleElement_When_ParseSingleTag() {
        List<String> result = mapper.parseTags("[\"java\"]");
        assertThat(result).containsExactly("java");
    }

    @Test
    @DisplayName("parseTags 对多元素 JSON 数组应返回多元素列表")
    void should_ReturnMultiElement_When_ParseMultipleTags() {
        List<String> result = mapper.parseTags("[\"java\",\"spring\"]");
        assertThat(result).containsExactly("java", "spring");
    }

    @Test
    @DisplayName("parseTags 对无引号逗号分隔应容错解析")
    void should_ParseTolerantly_When_NoQuotes() {
        List<String> result = mapper.parseTags("[java,spring]");
        assertThat(result).containsExactly("java", "spring");
    }

    @Test
    @DisplayName("parseTags 对空内部应返回空列表")
    void should_ReturnEmptyList_When_InnerIsEmpty() {
        // "[]" 去掉方括号后为空 → 空列表
        assertThat(mapper.parseTags("[]")).isEmpty();
    }

    // ===== toEntity =====

    @Test
    @DisplayName("toEntity 应将 WriteLongTermRequest 映射为 MemoryRecord（status=RAW）")
    void should_MapRequestToEntity_When_ToEntityInvoked() {
        WriteLongTermRequest req = WriteLongTermRequest.newBuilder()
                .setAgentId(1001L)
                .setUserId("user-001")
                .setDomain("coding")
                .setMemoryType("semantic")
                .setContent("Java 17 record 类")
                .addTags("java")
                .addTags("programming")
                .setSourceTaskId("task-001")
                .build();

        com.agent.memory.model.MemoryRecord entity = mapper.toEntity(req);

        assertThat(entity.getTenantId()).isEqualTo("1001");
        assertThat(entity.getUserId()).isEqualTo("user-001");
        assertThat(entity.getTopic()).isEqualTo("coding");
        assertThat(entity.getType()).isEqualTo(MemoryType.SEMANTIC);
        assertThat(entity.getContent()).isEqualTo("Java 17 record 类");
        assertThat(entity.getKeywords()).isEqualTo("[\"java\",\"programming\"]");
        assertThat(entity.getSourceTaskId()).isEqualTo("task-001");
        assertThat(entity.getStatus()).isEqualTo(MemoryStatus.RAW);
        // memoryId 留空由 writer 生成
        assertThat(entity.getMemoryId()).isNull();
    }

    @Test
    @DisplayName("toEntity 对无效 memory_type 应回退为 SEMANTIC")
    void should_FallbackToSemantic_When_MemoryTypeInvalid() {
        WriteLongTermRequest req = WriteLongTermRequest.newBuilder()
                .setAgentId(1L)
                .setMemoryType("unknown_type")
                .setContent("内容")
                .build();

        com.agent.memory.model.MemoryRecord entity = mapper.toEntity(req);

        assertThat(entity.getType()).isEqualTo(MemoryType.SEMANTIC);
    }

    @Test
    @DisplayName("toEntity 对空 tags 应写入 '[]'")
    void should_WriteEmptyArray_When_TagsEmpty() {
        WriteLongTermRequest req = WriteLongTermRequest.newBuilder()
                .setAgentId(1L)
                .setContent("内容")
                .build();

        com.agent.memory.model.MemoryRecord entity = mapper.toEntity(req);

        assertThat(entity.getKeywords()).isEqualTo("[]");
    }

    // ===== toProto =====

    @Test
    @DisplayName("toProto 应将 entity 映射为 proto MemoryRecord（含时间戳）")
    void should_MapEntityToProto_When_ToProtoInvoked() {
        com.agent.memory.model.MemoryRecord record = buildFullRecord();
        Instant now = Instant.now();
        record.setCreatedAt(now);
        record.setUpdatedAt(now.plusSeconds(60));

        MemoryRecord proto = mapper.toProto(record);

        assertThat(proto.getMemoryId()).isEqualTo("mem-001");
        assertThat(proto.getAgentId()).isEqualTo(1001L);
        assertThat(proto.getUserId()).isEqualTo("user-001");
        assertThat(proto.getDomain()).isEqualTo("coding");
        assertThat(proto.getMemoryType()).isEqualTo("semantic");
        assertThat(proto.getContent()).isEqualTo("Java 内容");
        assertThat(proto.getTagsList()).containsExactly("java", "spring");
        assertThat(proto.getSourceTaskId()).isEqualTo("task-001");
        assertThat(proto.getImportanceScore()).isEqualTo(0.85);
        assertThat(proto.getAccessCount()).isEqualTo(3);
        assertThat(proto.getCreatedAt()).isEqualTo(now.toEpochMilli());
        assertThat(proto.getUpdatedAt()).isEqualTo(now.plusSeconds(60).toEpochMilli());
    }

    @Test
    @DisplayName("toProto 对 null 字段应转为空字符串且不设置时间戳")
    void should_HandleNulls_When_FieldsMissing() {
        com.agent.memory.model.MemoryRecord record = new com.agent.memory.model.MemoryRecord();
        // 所有字段为 null
        record.setType(MemoryType.SEMANTIC);
        record.setImportanceScore(0.0);

        MemoryRecord proto = mapper.toProto(record);

        assertThat(proto.getMemoryId()).isEmpty();
        assertThat(proto.getUserId()).isEmpty();
        assertThat(proto.getDomain()).isEmpty();
        assertThat(proto.getContent()).isEmpty();
        assertThat(proto.getSourceTaskId()).isEmpty();
        assertThat(proto.getMemoryType()).isEqualTo("semantic");
        assertThat(proto.getCreatedAt()).isEqualTo(0L);
        assertThat(proto.getUpdatedAt()).isEqualTo(0L);
    }

    @Test
    @DisplayName("toProto 对非数字 tenantId 应解析为 0")
    void should_ReturnZero_When_TenantIdNotNumeric() {
        com.agent.memory.model.MemoryRecord record = new com.agent.memory.model.MemoryRecord();
        record.setType(MemoryType.SEMANTIC);
        record.setTenantId("not_a_number");

        MemoryRecord proto = mapper.toProto(record);

        assertThat(proto.getAgentId()).isEqualTo(0L);
    }

    @Test
    @DisplayName("toProto 对空 tenantId 应解析为 0")
    void should_ReturnZero_When_TenantIdEmpty() {
        com.agent.memory.model.MemoryRecord record = new com.agent.memory.model.MemoryRecord();
        record.setType(MemoryType.SEMANTIC);
        record.setTenantId("");

        MemoryRecord proto = mapper.toProto(record);

        assertThat(proto.getAgentId()).isEqualTo(0L);
    }

    // ===== toRecalledMemory =====

    @Test
    @DisplayName("toRecalledMemory 有 sourceTaskId 时 source_type='task'")
    void should_SetTaskSourceType_When_SourceTaskIdPresent() {
        com.agent.memory.model.MemoryRecord record = buildFullRecord();
        // sourceTaskId 已设置 = "task-001"

        RecalledMemory recalled = mapper.toRecalledMemory(record, 0.92);

        assertThat(recalled.getMemoryId()).isEqualTo("mem-001");
        assertThat(recalled.getContent()).isEqualTo("Java 内容");
        assertThat(recalled.getSourceType()).isEqualTo("task");
        assertThat(recalled.getSourceTaskId()).isEqualTo("task-001");
        assertThat(recalled.getImportanceScore()).isEqualTo(0.85);
        assertThat(recalled.getRelevanceScore()).isEqualTo(0.92);
    }

    @Test
    @DisplayName("toRecalledMemory 无 sourceTaskId 时 source_type='system'")
    void should_SetSystemSourceType_When_SourceTaskIdAbsent() {
        com.agent.memory.model.MemoryRecord record = buildFullRecord();
        record.setSourceTaskId(null);

        RecalledMemory recalled = mapper.toRecalledMemory(record, 0.5);

        assertThat(recalled.getSourceType()).isEqualTo("system");
        assertThat(recalled.getSourceTaskId()).isEmpty();
    }

    @Test
    @DisplayName("toRecalledMemory 空 sourceTaskId 时 source_type='system'")
    void should_SetSystemSourceType_When_SourceTaskIdEmpty() {
        com.agent.memory.model.MemoryRecord record = buildFullRecord();
        record.setSourceTaskId("");

        RecalledMemory recalled = mapper.toRecalledMemory(record, 0.5);

        assertThat(recalled.getSourceType()).isEqualTo("system");
    }

    @Test
    @DisplayName("toRecalledMemory 有 createdAt 时设置时间戳")
    void should_SetCreatedAt_When_Present() {
        com.agent.memory.model.MemoryRecord record = buildFullRecord();
        Instant now = Instant.now();
        record.setCreatedAt(now);

        RecalledMemory recalled = mapper.toRecalledMemory(record, 0.5);

        assertThat(recalled.getCreatedAt()).isEqualTo(now.toEpochMilli());
    }

    @Test
    @DisplayName("toRecalledMemory 无 createdAt 时不设置时间戳（默认 0）")
    void should_NotSetCreatedAt_When_Null() {
        com.agent.memory.model.MemoryRecord record = buildFullRecord();
        record.setCreatedAt(null);

        RecalledMemory recalled = mapper.toRecalledMemory(record, 0.5);

        assertThat(recalled.getCreatedAt()).isEqualTo(0L);
    }

    // ===== emptyRecallResponse =====

    @Test
    @DisplayName("emptyRecallResponse 应返回空 memories 和正确 meta")
    void should_ReturnEmptyResponse_When_EmptyRecallResponseInvoked() {
        RecallResponse resp = mapper.emptyRecallResponse();

        assertThat(resp.getMemoriesList()).isEmpty();
        assertThat(resp.getMeta().getTotalHits()).isEqualTo(0);
        assertThat(resp.getMeta().getReturned()).isEqualTo(0);
        assertThat(resp.getMeta().getTokenUsed()).isEqualTo(0);
        assertThat(resp.getMeta().getStrategiesUsed()).isEqualTo("vector");
    }

    // ===== 辅助 =====

    private static com.agent.memory.model.MemoryRecord buildFullRecord() {
        com.agent.memory.model.MemoryRecord r = new com.agent.memory.model.MemoryRecord();
        r.setMemoryId("mem-001");
        r.setTenantId("1001");
        r.setUserId("user-001");
        r.setTopic("coding");
        r.setType(MemoryType.SEMANTIC);
        r.setContent("Java 内容");
        r.setKeywords("[\"java\",\"spring\"]");
        r.setSourceTaskId("task-001");
        r.setImportanceScore(0.85);
        r.setImportanceLevel("HIGH");
        r.setRecallCount(3);
        r.setStatus(MemoryStatus.ACTIVE);
        return r;
    }
}
