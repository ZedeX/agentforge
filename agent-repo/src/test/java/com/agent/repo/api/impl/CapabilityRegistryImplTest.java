package com.agent.repo.api.impl;

import com.agent.repo.enums.CapabilityTag;
import com.agent.repo.model.Capability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CapabilityRegistryImpl unit tests (doc 06-agent-repo §3.1).
 */
@DisplayName("CapabilityRegistryImpl 能力注册中心")
class CapabilityRegistryImplTest {

    private final CapabilityRegistryImpl registry = new CapabilityRegistryImpl();

    @Test
    @DisplayName("构造函数已种子 3 个默认 capability")
    void should_SeedDefaults_When_Constructed() {
        List<Capability> defaults = registry.list();
        assertThat(defaults).hasSizeGreaterThanOrEqualTo(3);
        assertThat(registry.exists("code-gen")).isTrue();
        assertThat(registry.exists("code-review")).isTrue();
        assertThat(registry.exists("translate")).isTrue();
    }

    @Test
    @DisplayName("register 后能按 code 查询到")
    void should_FindByCode_When_Registered() {
        Capability custom = new Capability("custom-cap", "Custom", CapabilityTag.REASONING);
        registry.register(custom);
        Optional<Capability> found = registry.find("custom-cap");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Custom");
        assertThat(found.get().getTag()).isEqualTo(CapabilityTag.REASONING);
    }

    @Test
    @DisplayName("register 重复 code 覆盖旧实例")
    void should_Overwrite_When_ReRegistered() {
        Capability v1 = new Capability("dup", "V1", CapabilityTag.QA);
        Capability v2 = new Capability("dup", "V2", CapabilityTag.DATA_ANALYSIS);
        registry.register(v1);
        registry.register(v2);
        assertThat(registry.find("dup").get().getName()).isEqualTo("V2");
        assertThat(registry.find("dup").get().getTag()).isEqualTo(CapabilityTag.DATA_ANALYSIS);
    }

    @Test
    @DisplayName("register null capability 或 null code 安全跳过")
    void should_Skip_When_RegisterNullOrNullCode() {
        int before = registry.list().size();
        registry.register(null);
        Capability nullCode = new Capability(null, "NullCode", CapabilityTag.QA);
        registry.register(nullCode);
        assertThat(registry.list()).hasSize(before);
    }

    @Test
    @DisplayName("find 未注册的 code 返回 empty")
    void should_ReturnEmpty_When_NotRegistered() {
        assertThat(registry.find("non-existent")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    @DisplayName("findByTag 返回该 tag 下全部 capability")
    void should_ReturnByTag_When_FindByTagCalled() {
        registry.register(new Capability("qa-1", "QA One", CapabilityTag.QA));
        registry.register(new Capability("qa-2", "QA Two", CapabilityTag.QA));
        registry.register(new Capability("reasoning-1", "Reason", CapabilityTag.REASONING));
        List<Capability> qaCaps = registry.findByTag(CapabilityTag.QA);
        assertThat(qaCaps).hasSize(2);
        assertThat(qaCaps).allMatch(c -> c.getTag() == CapabilityTag.QA);
    }

    @Test
    @DisplayName("findByTag null 返回空列表")
    void should_ReturnEmpty_When_FindByTagNull() {
        assertThat(registry.findByTag(null)).isEmpty();
    }

    @Test
    @DisplayName("remove 已存在的 code 返回 true, 不存在返回 false")
    void should_RemoveAndReturnTrue_When_Exists() {
        assertThat(registry.remove("code-gen")).isTrue();
        assertThat(registry.exists("code-gen")).isFalse();
        assertThat(registry.remove("code-gen")).isFalse();
        assertThat(registry.remove(null)).isFalse();
    }

    @Test
    @DisplayName("list 返回副本, 修改不影响 store")
    void should_ReturnCopy_When_ListCalled() {
        List<Capability> all = registry.list();
        int before = all.size();
        all.clear();
        assertThat(registry.list()).hasSize(before);
    }
}
