package com.agent.memory.api.impl;

import com.agent.memory.config.MemoryProperties;
import com.agent.memory.enums.MemoryStatus;
import com.agent.memory.enums.MemoryType;
import com.agent.memory.gateway.ModelGatewayClient;
import com.agent.memory.model.MemoryRecord;
import com.agent.memory.model.MemoryTopic;
import com.agent.memory.repository.MemoryRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MemoryDistillerImpl 单元测试（Plan 03 T4）.
 *
 * <p>覆盖两条路径：
 * <ul>
 *   <li>新接口 {@code distill(tenantId, topic, activeRecords)} — Plan 03 T4 设计，
 *       25 条 ACTIVE → 1 条 DISTILLED + 源记录归档，调 ModelGatewayClient 生成摘要</li>
 *   <li>旧接口 {@code distill(MemoryTopic)} — F12 骨架向后兼容，模板拼接，不调模型</li>
 * </ul>
 */
class MemoryDistillerImplTest {

    private static final String TENANT = "tenant_001";
    private static final String TOPIC = "订单查询";

    private MemoryDistillerImpl createDistiller(ModelGatewayClient client, MemoryRecordRepository repository) {
        return new MemoryDistillerImpl(client, repository, new MemoryProperties());
    }

    private MemoryRecord buildActive(String id, String content, double importance) {
        MemoryRecord r = new MemoryRecord(id, MemoryType.EPISODIC, content);
        r.setTenantId(TENANT);
        r.setTopic(TOPIC);
        r.setStatus(MemoryStatus.ACTIVE);
        r.setImportanceScore(importance);
        r.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return r;
    }

    private List<MemoryRecord> buildActiveList(int count) {
        List<MemoryRecord> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(buildActive("mem_" + i, "订单查询内容片段 " + i, 0.5));
        }
        return list;
    }

    // ============ 新接口 distill(tenantId, topic, activeRecords) ============

    @Test
    @DisplayName("distill 在 ACTIVE 记忆数 >= triggerCount(20) 时应产出 1 条 DISTILLED 并归档源记录")
    void should_DistillToSingleRecord_When_ActiveCountExceedsThreshold() {
        ModelGatewayClient client = mock(ModelGatewayClient.class);
        when(client.chat(anyString(), anyString())).thenReturn("蒸馏后的摘要文本");
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        MemoryDistillerImpl distiller = createDistiller(client, repository);

        List<MemoryRecord> actives = buildActiveList(25);
        MemoryRecord distilled = distiller.distill(TENANT, TOPIC, actives);

        assertThat(distilled).isNotNull();
        assertThat(distilled.getStatus()).isEqualTo(MemoryStatus.DISTILLED);
        assertThat(distilled.getContent()).isEqualTo("蒸馏后的摘要文本");
        assertThat(distilled.getTenantId()).isEqualTo(TENANT);
        assertThat(distilled.getTopic()).isEqualTo(TOPIC);
        assertThat(distilled.getMemoryId()).isNotNull();
        // 源记录全部归档
        assertThat(actives).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(MemoryStatus.ARCHIVED));
        // 持久化 distilled 记录
        verify(repository).save(distilled);
    }

    @Test
    @DisplayName("distill 在 ACTIVE 记忆数 < triggerCount 时应返回 null 跳过")
    void should_ReturnNull_When_BelowThreshold() {
        ModelGatewayClient client = mock(ModelGatewayClient.class);
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        MemoryDistillerImpl distiller = createDistiller(client, repository);

        List<MemoryRecord> actives = buildActiveList(15);
        MemoryRecord result = distiller.distill(TENANT, TOPIC, actives);

        assertThat(result).isNull();
        verifyNoInteractions(client);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("distill 应将全部源记录 content 传入 model gateway prompt")
    void should_PassAllContentToModel_When_Distill() {
        ModelGatewayClient client = mock(ModelGatewayClient.class);
        when(client.chat(anyString(), anyString())).thenReturn("摘要");
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        MemoryDistillerImpl distiller = createDistiller(client, repository);

        List<MemoryRecord> actives = buildActiveList(25);
        distiller.distill(TENANT, TOPIC, actives);

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).chat(systemCaptor.capture(), userCaptor.capture());

        String userPrompt = userCaptor.getValue();
        for (MemoryRecord r : actives) {
            assertThat(userPrompt).contains(r.getContent());
        }
    }

    @Test
    @DisplayName("distill 在模型调用失败时应抛异常且源 ACTIVE 状态不变")
    void should_ThrowAndPreserveActive_When_ModelFails() {
        ModelGatewayClient client = mock(ModelGatewayClient.class);
        when(client.chat(anyString(), anyString())).thenThrow(new RuntimeException("model unavailable"));
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        MemoryDistillerImpl distiller = createDistiller(client, repository);

        List<MemoryRecord> actives = buildActiveList(25);

        assertThatThrownBy(() -> distiller.distill(TENANT, TOPIC, actives))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("model unavailable");

        // 源记录仍是 ACTIVE
        assertThat(actives).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(MemoryStatus.ACTIVE));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("distill 在聚合 importance 平均值 >= 0.7 时应赋予 DISTILLED 记忆 HIGH 等级")
    void should_AssignHighImportance_When_AggregatedImportanceHigh() {
        ModelGatewayClient client = mock(ModelGatewayClient.class);
        when(client.chat(anyString(), anyString())).thenReturn("摘要");
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        MemoryDistillerImpl distiller = createDistiller(client, repository);

        List<MemoryRecord> actives = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            actives.add(buildActive("mem_" + i, "内容 " + i, 0.8));  // all 0.8 -> avg 0.8 >= 0.7
        }

        MemoryRecord distilled = distiller.distill(TENANT, TOPIC, actives);

        assertThat(distilled).isNotNull();
        assertThat(distilled.getImportanceScore()).isGreaterThanOrEqualTo(0.7);
        assertThat(distilled.getImportanceLevel()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("distill 在聚合 importance 平均值 < 0.4 时应赋予 DISTILLED 记录 LOW 等级")
    void should_AssignLowImportance_When_AggregatedImportanceLow() {
        ModelGatewayClient client = mock(ModelGatewayClient.class);
        when(client.chat(anyString(), anyString())).thenReturn("摘要");
        MemoryRecordRepository repository = mock(MemoryRecordRepository.class);
        MemoryDistillerImpl distiller = createDistiller(client, repository);

        List<MemoryRecord> actives = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            actives.add(buildActive("mem_" + i, "内容 " + i, 0.2));  // all 0.2 -> avg 0.2 < 0.4
        }

        MemoryRecord distilled = distiller.distill(TENANT, TOPIC, actives);

        assertThat(distilled).isNotNull();
        assertThat(distilled.getImportanceScore()).isLessThan(0.4);
        assertThat(distilled.getImportanceLevel()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("distill 对 null activeRecords 应返回 null")
    void should_ReturnNull_When_ActiveRecordsNull() {
        MemoryDistillerImpl distiller = createDistiller(mock(ModelGatewayClient.class), mock(MemoryRecordRepository.class));
        assertThat(distiller.distill(TENANT, TOPIC, null)).isNull();
    }

    @Test
    @DisplayName("distill 对空 activeRecords 应返回 null")
    void should_ReturnNull_When_ActiveRecordsEmpty() {
        MemoryDistillerImpl distiller = createDistiller(mock(ModelGatewayClient.class), mock(MemoryRecordRepository.class));
        assertThat(distiller.distill(TENANT, TOPIC, List.of())).isNull();
    }

    // ============ 旧接口 distill(MemoryTopic) 向后兼容 ============

    @Test
    @DisplayName("distill(MemoryTopic) 在碎片数 >= 5 时应生成摘要并标记 distilled=true")
    void should_DistillSummary_When_FragmentsReachThreshold_OldApi() {
        MemoryDistillerImpl distiller = createDistiller(mock(ModelGatewayClient.class), mock(MemoryRecordRepository.class));
        MemoryTopic topic = new MemoryTopic("订单查询", 5);

        MemoryTopic result = distiller.distill(topic);

        assertThat(result.isDistilled()).isTrue();
        assertThat(result.getSummary()).contains("订单查询").contains("5");
        assertThat(result.getCompressionRatio()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("distill(MemoryTopic) 在碎片数 < 5 时应跳过蒸馏保持原状")
    void should_SkipDistill_When_FragmentsBelowThreshold_OldApi() {
        MemoryDistillerImpl distiller = createDistiller(mock(ModelGatewayClient.class), mock(MemoryRecordRepository.class));
        MemoryTopic topic = new MemoryTopic("支付", 3);

        MemoryTopic result = distiller.distill(topic);

        assertThat(result.isDistilled()).isFalse();
        assertThat(result.getSummary()).isNull();
    }

    @Test
    @DisplayName("distill(MemoryTopic) 对 null 入参应返回 null")
    void should_ReturnNull_When_TopicIsNull_OldApi() {
        MemoryDistillerImpl distiller = createDistiller(mock(ModelGatewayClient.class), mock(MemoryRecordRepository.class));
        assertThat(distiller.distill((MemoryTopic) null)).isNull();
    }
}
