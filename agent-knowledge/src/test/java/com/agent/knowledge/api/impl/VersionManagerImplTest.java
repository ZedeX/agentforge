package com.agent.knowledge.api.impl;

import com.agent.knowledge.model.KnowledgeBase;
import com.agent.knowledge.model.KnowledgeVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VersionManagerImpl unit tests (doc 06-agent-repo §4.2 version pattern).
 */
@DisplayName("VersionManagerImpl 版本快照管理器")
class VersionManagerImplTest {

    private final VersionManagerImpl manager = new VersionManagerImpl();

    @Test
    @DisplayName("snapshot 创建版本快照, 版本号自增")
    void should_CreateSnapshot_When_BaseBoundAndSnapshotCalled() {
        KnowledgeBase base = new KnowledgeBase("kb-1", "Test KB");
        manager.bindBase(base);

        KnowledgeVersion v1 = manager.snapshot("kb-1", "initial");
        assertThat(v1).isNotNull();
        assertThat(v1.getVersion()).isEqualTo(1);
        assertThat(v1.getChangeLog()).isEqualTo("initial");
        assertThat(v1.getSnapshot()).contains("kb-1");
        assertThat(v1.getSnapshot()).contains("Test KB");

        KnowledgeVersion v2 = manager.snapshot("kb-1", "second");
        assertThat(v2.getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("snapshot null kbId 或未绑定 KB 返回 null")
    void should_ReturnNull_When_SnapshotOnMissingKb() {
        assertThat(manager.snapshot(null, "log")).isNull();
        assertThat(manager.snapshot("nonexistent", "log")).isNull();
    }

    @Test
    @DisplayName("snapshot null changeLog 存储为空串")
    void should_StoreEmptyChangeLog_When_NullProvided() {
        KnowledgeBase base = new KnowledgeBase("kb-2", "Test");
        manager.bindBase(base);
        KnowledgeVersion v = manager.snapshot("kb-2", null);
        assertThat(v).isNotNull();
        assertThat(v.getChangeLog()).isEmpty();
    }

    @Test
    @DisplayName("listVersions 返回版本列表, 按版本号降序")
    void should_ListVersionsDesc_When_MultipleSnapshots() {
        KnowledgeBase base = new KnowledgeBase("kb-3", "Test");
        manager.bindBase(base);
        manager.snapshot("kb-3", "v1");
        manager.snapshot("kb-3", "v2");
        manager.snapshot("kb-3", "v3");

        List<KnowledgeVersion> versions = manager.listVersions("kb-3");
        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).getVersion()).isEqualTo(3);
        assertThat(versions.get(2).getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("listVersions null kbId 或无版本返回空列表")
    void should_ReturnEmpty_When_ListVersionsOnMissingKb() {
        assertThat(manager.listVersions(null)).isEmpty();
        assertThat(manager.listVersions("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("getVersion 精确查找指定版本, 不存在返回 null")
    void should_ReturnVersion_When_VersionExists() {
        KnowledgeBase base = new KnowledgeBase("kb-4", "Test");
        manager.bindBase(base);
        manager.snapshot("kb-4", "first");

        KnowledgeVersion found = manager.getVersion("kb-4", 1);
        assertThat(found).isNotNull();
        assertThat(found.getVersion()).isEqualTo(1);

        assertThat(manager.getVersion("kb-4", 99)).isNull();
        assertThat(manager.getVersion("kb-4", 0)).isNull();
        assertThat(manager.getVersion(null, 1)).isNull();
    }

    @Test
    @DisplayName("rollback 回滚到指定版本, 恢复 KB 元数据")
    void should_Rollback_When_VersionExists() {
        KnowledgeBase base = new KnowledgeBase("kb-5", "Original");
        manager.bindBase(base);
        manager.snapshot("kb-5", "initial");

        // Change KB name and snapshot again
        base.setName("Modified");
        manager.snapshot("kb-5", "name changed");

        // Rollback to version 1
        KnowledgeVersion rolledBack = manager.rollback("kb-5", 1);
        assertThat(rolledBack).isNotNull();
        assertThat(rolledBack.getVersion()).isEqualTo(1);
        assertThat(base.getName()).isEqualTo("Original");
    }

    @Test
    @DisplayName("rollback 未找到版本或 KB 返回 null")
    void should_ReturnNull_When_RollbackOnMissing() {
        assertThat(manager.rollback("nonexistent", 1)).isNull();
        assertThat(manager.rollback(null, 1)).isNull();

        KnowledgeBase base = new KnowledgeBase("kb-6", "Test");
        manager.bindBase(base);
        assertThat(manager.rollback("kb-6", 99)).isNull();
    }

    @Test
    @DisplayName("bindBase null 或 null kbId 安全跳过")
    void should_SkipBind_When_BaseOrKbIdNull() {
        manager.bindBase(null);
        KnowledgeBase nullKbId = new KnowledgeBase(null, "NoId");
        manager.bindBase(nullKbId);
        assertThat(manager.snapshot(null, "log")).isNull();
    }
}
