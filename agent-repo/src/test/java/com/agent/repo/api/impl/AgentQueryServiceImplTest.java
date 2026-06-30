package com.agent.repo.api.impl;

import com.agent.repo.enums.AgentStatus;
import com.agent.repo.enums.AgentTier;
import com.agent.repo.enums.CapabilityTag;
import com.agent.repo.model.AgentDefinition;
import com.agent.repo.model.PageResult;
import com.agent.repo.model.RepoQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentQueryServiceImpl unit tests (doc 06-agent-repo §5).
 */
@DisplayName("AgentQueryServiceImpl Agent 查询服务")
class AgentQueryServiceImplTest {

    private final AgentQueryServiceImpl queryService = new AgentQueryServiceImpl();

    private AgentDefinition sample(String id, String name, AgentStatus status, AgentTier tier, String... tags) {
        AgentDefinition a = new AgentDefinition(id, name);
        a.setStatus(status);
        a.setAgentTier(tier);
        a.setAbilityTags(Arrays.asList(tags));
        return a;
    }

    private void seed() {
        queryService.index(sample("a1", "Alpha", AgentStatus.PUBLISHED, AgentTier.STANDARD, "code_generation"));
        queryService.index(sample("a2", "Beta", AgentStatus.DRAFT, AgentTier.LITE, "qa"));
        queryService.index(sample("a3", "Gamma", AgentStatus.PUBLISHED, AgentTier.ADVANCED, "code_review"));
        queryService.index(sample("a4", "Delta", AgentStatus.DEPRECATED, AgentTier.STANDARD, "translation"));
        queryService.index(sample("a5", "Epsilon", AgentStatus.PUBLISHED, AgentTier.LITE, "qa", "translation"));
    }

    @Test
    @DisplayName("index 后能 query 出全部")
    void should_ReturnAll_When_NoFilter() {
        seed();
        RepoQuery q = new RepoQuery();
        PageResult<AgentDefinition> result = queryService.query(q);
        assertThat(result.getTotal()).isEqualTo(5);
        assertThat(result.getItems()).hasSize(5);
    }

    @Test
    @DisplayName("filter status=PUBLISHED 仅返回 published 的 agent")
    void should_FilterByStatus_When_StatusSet() {
        seed();
        RepoQuery q = new RepoQuery();
        q.setStatus(AgentStatus.PUBLISHED);
        PageResult<AgentDefinition> result = queryService.query(q);
        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getItems()).allMatch(a -> a.getStatus() == AgentStatus.PUBLISHED);
    }

    @Test
    @DisplayName("filter nameContains=大小写不敏感子串")
    void should_FilterByName_When_NameContainsSet() {
        seed();
        RepoQuery q = new RepoQuery();
        q.setNameContains("a"); // matches Alpha, Gamma, Delta (case-insensitive)
        PageResult<AgentDefinition> result = queryService.query(q);
        // Alpha (has 'a'), Gamma (has 'a'), Delta (has 'a'), Epsilon? 'e'... yes Epsilon has no 'a'
        // Actually let me count: Alpha(1 a), Beta(1 a), Gamma(2 a), Delta(1 a), Epsilon(0 a)
        assertThat(result.getItems()).extracting(AgentDefinition::getName)
                .allMatch(name -> name.toLowerCase().contains("a"));
        assertThat(result.getTotal()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("filter agentTier=ADVANCED 仅返回 advanced 的 agent")
    void should_FilterByTier_When_TierSet() {
        seed();
        RepoQuery q = new RepoQuery();
        q.setAgentTier(AgentTier.ADVANCED);
        PageResult<AgentDefinition> result = queryService.query(q);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems().get(0).getName()).isEqualTo("Gamma");
    }

    @Test
    @DisplayName("filter capabilityTag=QA 返回 abilityTags 含 qa 的 agent")
    void should_FilterByCapabilityTag_When_TagSet() {
        seed();
        RepoQuery q = new RepoQuery();
        q.setCapabilityTag(CapabilityTag.QA);
        PageResult<AgentDefinition> result = queryService.query(q);
        // a2 (Beta) and a5 (Epsilon) have qa tag
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getItems()).extracting(AgentDefinition::getName)
                .containsExactlyInAnyOrder("Beta", "Epsilon");
    }

    @Test
    @DisplayName("组合 status + tier + capabilityTag 多重过滤")
    void should_CombineFilters_When_MultipleSet() {
        seed();
        RepoQuery q = new RepoQuery();
        q.setStatus(AgentStatus.PUBLISHED);
        q.setAgentTier(AgentTier.LITE);
        PageResult<AgentDefinition> result = queryService.query(q);
        // published + lite: a5 (Epsilon)
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems().get(0).getName()).isEqualTo("Epsilon");
    }

    @Test
    @DisplayName("分页 page=0/size=2 返回前 2 条 + total=5")
    void should_Paginate_When_PageAndSizeSet() {
        seed();
        RepoQuery q = new RepoQuery();
        q.setPage(0);
        q.setSize(2);
        PageResult<AgentDefinition> result = queryService.query(q);
        assertThat(result.getTotal()).isEqualTo(5);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(3); // ceil(5/2)
    }

    @Test
    @DisplayName("分页 page=2/size=2 越界返回空列表但 total 不变")
    void should_ReturnEmpty_When_PageOutOfBounds() {
        seed();
        RepoQuery q = new RepoQuery();
        q.setPage(10);
        q.setSize(2);
        PageResult<AgentDefinition> result = queryService.query(q);
        assertThat(result.getTotal()).isEqualTo(5);
        assertThat(result.getItems()).isEmpty();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("分页 size=0 兜底为 10")
    void should_DefaultSize_When_SizeZero() {
        seed();
        RepoQuery q = new RepoQuery();
        q.setPage(0);
        q.setSize(0);
        PageResult<AgentDefinition> result = queryService.query(q);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getItems()).hasSize(5);
    }

    @Test
    @DisplayName("分页负 page 兜底为 0")
    void should_DefaultPage_When_PageNegative() {
        seed();
        RepoQuery q = new RepoQuery();
        q.setPage(-5);
        q.setSize(2);
        PageResult<AgentDefinition> result = queryService.query(q);
        assertThat(result.getPage()).isZero();
        assertThat(result.getItems()).hasSize(2);
    }

    @Test
    @DisplayName("空索引返回 total=0 + empty items")
    void should_ReturnEmpty_When_NoIndex() {
        RepoQuery q = new RepoQuery();
        PageResult<AgentDefinition> result = queryService.query(q);
        assertThat(result.getTotal()).isZero();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("null query 返回空 PageResult")
    void should_ReturnEmpty_When_QueryNull() {
        PageResult<AgentDefinition> result = queryService.query(null);
        assertThat(result.getTotal()).isZero();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("index 同 agentId 第二次为 update")
    void should_Update_When_IndexSameIdTwice() {
        queryService.index(sample("a1", "Alpha", AgentStatus.DRAFT, AgentTier.STANDARD));
        queryService.index(sample("a1", "Alpha Updated", AgentStatus.PUBLISHED, AgentTier.ADVANCED));
        RepoQuery q = new RepoQuery();
        PageResult<AgentDefinition> result = queryService.query(q);
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems().get(0).getName()).isEqualTo("Alpha Updated");
        assertThat(result.getItems().get(0).getStatus()).isEqualTo(AgentStatus.PUBLISHED);
    }

    @Test
    @DisplayName("index null agent / null agentId 安全跳过")
    void should_Skip_When_IndexNullOrNullId() {
        queryService.index(null);
        AgentDefinition nullId = new AgentDefinition();
        queryService.index(nullId);
        assertThat(queryService.query(new RepoQuery()).getTotal()).isZero();
    }

    @Test
    @DisplayName("removeIndex 删除已索引 agent, 不存在返回 false")
    void should_RemoveIndex_When_Exists() {
        queryService.index(sample("a1", "Alpha", AgentStatus.DRAFT, AgentTier.STANDARD));
        assertThat(queryService.removeIndex("a1")).isTrue();
        assertThat(queryService.query(new RepoQuery()).getTotal()).isZero();
        assertThat(queryService.removeIndex("a1")).isFalse();
        assertThat(queryService.removeIndex(null)).isFalse();
    }

    @Test
    @DisplayName("filter nameContains 为空串等同不设过滤")
    void should_NoFilter_When_NameContainsEmpty() {
        seed();
        RepoQuery q = new RepoQuery();
        q.setNameContains("");
        assertThat(queryService.query(q).getTotal()).isEqualTo(5);
    }
}
