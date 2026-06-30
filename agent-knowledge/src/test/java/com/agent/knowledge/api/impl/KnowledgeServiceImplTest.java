package com.agent.knowledge.api.impl;

import com.agent.knowledge.enums.KnowledgeStatus;
import com.agent.knowledge.model.KnowledgeBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KnowledgeServiceImpl unit tests (doc 07-knowledge §5).
 */
@DisplayName("KnowledgeServiceImpl 知识库 CRUD")
class KnowledgeServiceImplTest {

    private final KnowledgeServiceImpl service = new KnowledgeServiceImpl();

    @Test
    @DisplayName("合法 kbId + name 创建成功, 状态为 CREATING")
    void should_CreateBase_When_ValidKbIdAndName() {
        KnowledgeBase base = service.createBase("kb-1", "Test KB", "description");
        assertThat(base).isNotNull();
        assertThat(base.getKbId()).isEqualTo("kb-1");
        assertThat(base.getName()).isEqualTo("Test KB");
        assertThat(base.getDescription()).isEqualTo("description");
        assertThat(base.getStatus()).isEqualTo(KnowledgeStatus.CREATING);
        assertThat(base.getDimension()).isEqualTo(1024);
        assertThat(base.getDocCount()).isZero();
        assertThat(base.getCreatedAt()).isPositive();
    }

    @Test
    @DisplayName("name 为 null 或空时返回 null")
    void should_ReturnNull_When_NameNullOrEmpty() {
        assertThat(service.createBase("kb-2", null, "desc")).isNull();
        assertThat(service.createBase("kb-3", "", "desc")).isNull();
    }

    @Test
    @DisplayName("kbId 为 null 时自动生成前缀 kb-")
    void should_AutoGenerateKbId_When_KbIdNull() {
        KnowledgeBase base = service.createBase(null, "Auto KB", null);
        assertThat(base).isNotNull();
        assertThat(base.getKbId()).startsWith("kb-");
        assertThat(base.getName()).isEqualTo("Auto KB");
    }

    @Test
    @DisplayName("重复 kbId 创建返回 null")
    void should_ReturnNull_When_DuplicateKbId() {
        service.createBase("kb-dup", "First", null);
        KnowledgeBase second = service.createBase("kb-dup", "Second", null);
        assertThat(second).isNull();
    }

    @Test
    @DisplayName("getBase 未找到或已删除返回 null")
    void should_ReturnNull_When_KbNotFoundOrDeleted() {
        assertThat(service.getBase("nonexistent")).isNull();
        assertThat(service.getBase(null)).isNull();

        service.createBase("kb-del", "ToDelete", null);
        service.deleteBase("kb-del");
        assertThat(service.getBase("kb-del")).isNull();
    }

    @Test
    @DisplayName("deleteBase 软删除后状态变为 DELETED, 再删返回 false")
    void should_SoftDelete_When_DeleteBaseCalled() {
        service.createBase("kb-soft", "SoftDelete", null);
        assertThat(service.deleteBase("kb-soft")).isTrue();
        assertThat(service.deleteBase("kb-soft")).isFalse();
        assertThat(service.deleteBase("nonexistent")).isFalse();
        assertThat(service.deleteBase(null)).isFalse();
    }

    @Test
    @DisplayName("listBases null status 返回全部非 DELETED, 指定 status 仅返回匹配项")
    void should_FilterByStatus_When_ListBases() {
        service.createBase("kb-a", "A", null);
        service.createBase("kb-b", "B", null);
        service.updateStatus("kb-b", "ready");

        List<KnowledgeBase> all = service.listBases(null);
        assertThat(all).hasSize(2);

        List<KnowledgeBase> readyOnly = service.listBases(KnowledgeStatus.READY);
        assertThat(readyOnly).hasSize(1);
        assertThat(readyOnly.get(0).getKbId()).isEqualTo("kb-b");
    }

    @Test
    @DisplayName("updateStatus 合法 code 解析为对应枚举 (exercises fromCode)")
    void should_UpdateStatus_When_ValidStatusCode() {
        service.createBase("kb-up", "UpdateTest", null);
        KnowledgeBase updated = service.updateStatus("kb-up", "ready");
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(KnowledgeStatus.READY);

        updated = service.updateStatus("kb-up", "UPDATING");
        assertThat(updated.getStatus()).isEqualTo(KnowledgeStatus.UPDATING);

        updated = service.updateStatus("kb-up", "error");
        assertThat(updated.getStatus()).isEqualTo(KnowledgeStatus.ERROR);
    }

    @Test
    @DisplayName("updateStatus null/空/未知 code 兜底为 READY (exercises fromCode default)")
    void should_DefaultToReady_When_StatusCodeNullOrUnknown() {
        service.createBase("kb-def", "DefaultTest", null);
        assertThat(service.updateStatus("kb-def", null).getStatus()).isEqualTo(KnowledgeStatus.READY);
        assertThat(service.updateStatus("kb-def", "").getStatus()).isEqualTo(KnowledgeStatus.READY);
        assertThat(service.updateStatus("kb-def", "nonexistent-code").getStatus()).isEqualTo(KnowledgeStatus.READY);
    }

    @Test
    @DisplayName("updateStatus 未找到 KB 或已删除返回 null")
    void should_ReturnNull_When_UpdateStatusOnMissingOrDeletedKb() {
        assertThat(service.updateStatus("nonexistent", "ready")).isNull();
        assertThat(service.updateStatus(null, "ready")).isNull();

        service.createBase("kb-del2", "ToDelete", null);
        service.deleteBase("kb-del2");
        assertThat(service.updateStatus("kb-del2", "ready")).isNull();
    }
}
