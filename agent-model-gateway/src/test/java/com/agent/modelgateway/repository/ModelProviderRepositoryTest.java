package com.agent.modelgateway.repository;

import com.agent.modelgateway.model.ModelProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ModelProviderRepository JPA integration tests (Plan 07 T2).
 *
 * <p>Uses @DataJpaTest with H2 in-memory database (MODE=MySQL) to verify entity mapping
 * and repository query methods without external MySQL dependency.</p>
 */
@DisplayName("ModelProviderRepository JPA 仓储测试")
@DataJpaTest
@ActiveProfiles("test")
class ModelProviderRepositoryTest {

    @Autowired
    private ModelProviderRepository repository;

    private ModelProvider buildProvider(String code, String name, double inputCost, double outputCost, boolean enabled) {
        ModelProvider p = new ModelProvider(code, name, "https://api." + code + ".com/v1");
        p.setApiKeyRef("vault://secret/" + code);
        p.setCostPerInput1k(inputCost);
        p.setCostPerOutput1k(outputCost);
        p.setEnabled(enabled);
        p.setWeight(1);
        p.setMaxQps(100);
        p.setMaxConcurrency(10);
        return p;
    }

    @Test
    @DisplayName("findByProviderCode 按 code 精确查询返回 provider")
    void should_FindByProviderCode_When_Exists() {
        repository.save(buildProvider("openai", "OpenAI", 0.005, 0.015, true));

        Optional<ModelProvider> found = repository.findByProviderCode("openai");

        assertThat(found).isPresent();
        assertThat(found.get().getProviderName()).isEqualTo("OpenAI");
        assertThat(found.get().getCostPerInput1k()).isEqualTo(0.005);
        assertThat(found.get().getApiKeyRef()).isEqualTo("vault://secret/openai");
    }

    @Test
    @DisplayName("findByProviderCode 查询不存在的 code 返回 empty")
    void should_ReturnEmpty_When_ProviderCodeNotFound() {
        Optional<ModelProvider> found = repository.findByProviderCode("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByEnabledTrue 仅返回启用的 provider")
    void should_ReturnOnlyEnabled_When_FindByEnabledTrue() {
        repository.save(buildProvider("openai", "OpenAI", 0.005, 0.015, true));
        repository.save(buildProvider("disabled-prov", "Disabled", 0.001, 0.002, false));
        repository.save(buildProvider("anthropic", "Anthropic", 0.003, 0.015, true));

        List<ModelProvider> enabled = repository.findByEnabledTrue();

        assertThat(enabled).hasSize(2);
        assertThat(enabled).extracting(ModelProvider::getProviderCode)
                .containsExactlyInAnyOrder("openai", "anthropic");
    }

    @Test
    @DisplayName("existsByProviderCode 检查 code 存在性")
    void should_CheckExistence_When_ExistsByProviderCode() {
        repository.save(buildProvider("gemini", "Gemini", 0.0005, 0.0015, true));

        assertThat(repository.existsByProviderCode("gemini")).isTrue();
        assertThat(repository.existsByProviderCode("openai")).isFalse();
    }

    @Test
    @DisplayName("provider_code 唯一约束: 重复插入抛异常")
    void should_Throw_When_DuplicateProviderCodeInserted() {
        repository.save(buildProvider("deepseek", "DeepSeek", 0.00014, 0.00028, true));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                repository.save(buildProvider("deepseek", "Duplicate", 0.001, 0.002, true))
        ).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("@PrePersist 自动填充 createdAt 和 updatedAt")
    void should_AutoFillTimestamps_When_Saved() {
        ModelProvider saved = repository.save(buildProvider("qwen", "Qwen", 0.0003, 0.0009, true));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("weight/maxQps/maxConcurrency 默认值正确持久化")
    void should_PersistDefaults_When_Saved() {
        ModelProvider saved = repository.save(buildProvider("ernie", "ERNIE", 0.0008, 0.002, true));

        assertThat(saved.getWeight()).isEqualTo(1);
        assertThat(saved.getMaxQps()).isEqualTo(100);
        assertThat(saved.getMaxConcurrency()).isEqualTo(10);
    }
}
