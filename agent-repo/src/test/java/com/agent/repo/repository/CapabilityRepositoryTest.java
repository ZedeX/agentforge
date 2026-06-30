package com.agent.repo.repository;

import com.agent.repo.enums.CapabilityTag;
import com.agent.repo.model.Capability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CapabilityRepository JPA integration tests (Plan 08 T4).
 *
 * <p>Capability uses natural key {@code code} as {@code @Id}. Verifies tag-based
 * filtering, enabled-only lookups, and existence checks.</p>
 */
@DisplayName("CapabilityRepository JPA 仓储测试")
@DataJpaTest
@ActiveProfiles("test")
class CapabilityRepositoryTest {

    @Autowired
    private CapabilityRepository repository;

    private Capability buildCapability(String code, String name, CapabilityTag tag, boolean enabled) {
        Capability c = new Capability(code, name, tag);
        c.setEnabled(enabled);
        c.setDescription("Capability " + name);
        return c;
    }

    @Test
    @DisplayName("findById 按 code 自然键查询返回 capability")
    void should_FindByCode_When_Exists() {
        repository.save(buildCapability("code-gen", "Code Generation", CapabilityTag.CODE_GENERATION, true));

        Optional<Capability> found = repository.findById("code-gen");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Code Generation");
        assertThat(found.get().getTag()).isEqualTo(CapabilityTag.CODE_GENERATION);
    }

    @Test
    @DisplayName("findByTag 按标签过滤 capability")
    void should_FilterByTag_When_FindByTag() {
        repository.save(buildCapability("c1", "Code Gen", CapabilityTag.CODE_GENERATION, true));
        repository.save(buildCapability("c2", "Code Review", CapabilityTag.CODE_REVIEW, true));
        repository.save(buildCapability("c3", "Code Refactor", CapabilityTag.CODE_GENERATION, true));

        List<Capability> result = repository.findByTag(CapabilityTag.CODE_GENERATION);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Capability::getCode).containsExactlyInAnyOrder("c1", "c3");
    }

    @Test
    @DisplayName("findByEnabledTrue 仅返回启用的 capability")
    void should_ReturnOnlyEnabled_When_FindByEnabledTrue() {
        repository.save(buildCapability("en1", "Enabled 1", CapabilityTag.QA, true));
        repository.save(buildCapability("dis1", "Disabled 1", CapabilityTag.QA, false));
        repository.save(buildCapability("en2", "Enabled 2", CapabilityTag.REASONING, true));

        List<Capability> enabled = repository.findByEnabledTrue();

        assertThat(enabled).hasSize(2);
        assertThat(enabled).extracting(Capability::getCode).containsExactlyInAnyOrder("en1", "en2");
    }

    @Test
    @DisplayName("findByTagAndEnabledTrue 组合标签与启用状态过滤")
    void should_FilterByTagAndEnabled_When_Combined() {
        repository.save(buildCapability("qa1", "QA 1", CapabilityTag.QA, true));
        repository.save(buildCapability("qa2", "QA 2", CapabilityTag.QA, false));
        repository.save(buildCapability("qa3", "QA 3", CapabilityTag.QA, true));
        repository.save(buildCapability("rs1", "Reasoning 1", CapabilityTag.REASONING, true));

        List<Capability> result = repository.findByTagAndEnabledTrue(CapabilityTag.QA);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Capability::getCode).containsExactlyInAnyOrder("qa1", "qa3");
    }

    @Test
    @DisplayName("existsByCode 检查 code 自然键存在性")
    void should_CheckExistence_When_ExistsByCode() {
        repository.save(buildCapability("exist-code", "Exists", CapabilityTag.QA, true));

        assertThat(repository.existsByCode("exist-code")).isTrue();
        assertThat(repository.existsByCode("missing-code")).isFalse();
    }

    @Test
    @DisplayName("enabled 默认值 true 正确持久化")
    void should_PersistEnabledDefault_When_Saved() {
        Capability c = new Capability("default-en", "Default Enabled", CapabilityTag.QA);
        repository.save(c);

        Capability found = repository.findById("default-en").orElseThrow();

        assertThat(found.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("description 字段可空: 不设置 description 也能持久化")
    void should_PersistWithoutDescription_When_Null() {
        Capability c = new Capability("no-desc", "No Description", CapabilityTag.TRANSLATION);
        repository.save(c);

        Capability found = repository.findById("no-desc").orElseThrow();

        assertThat(found.getDescription()).isNull();
        assertThat(found.getName()).isEqualTo("No Description");
    }
}
