package com.agent.repo.grpc;

import agentplatform.repo.v1.AgentResponse;
import agentplatform.repo.v1.CreateAgentRequest;
import agentplatform.repo.v1.ListAgentsRequest;
import agentplatform.repo.v1.ListAgentsResponse;
import agentplatform.repo.v1.UpdateAgentRequest;
import com.agent.repo.enums.AgentStatus;
import com.agent.repo.enums.AgentTier;
import com.agent.repo.model.AgentDefinition;
import com.agent.repo.model.PageResult;
import com.agent.repo.model.RepoQuery;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Proto ↔ Entity / POJO 映射器（Plan 08 T4）。
 *
 * <p>负责 gRPC proto 消息（{@link CreateAgentRequest} / {@link UpdateAgentRequest}）与
 * JPA Entity {@link AgentDefinition} 之间的双向转换，以及 {@link RepoQuery} /
 * {@link PageResult} 与 proto 请求/响应的转换。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>{@code agent_tier} / {@code status} 在 proto 中用字符串（小写 code）传输，
 *       通过 {@link AgentTier#fromCode} / {@link AgentStatus#fromCode} 解析为枚举</li>
 *   <li>{@code List<String>} ↔ {@code repeated string} 直接转换</li>
 *   <li>{@code CreateAgentRequest} 不含 status/version/created_at/updated_at，由 entity 默认值设置</li>
 *   <li>{@code mergeEntity} 只合并可更新字段，不覆盖 id/agentId/status/version/createdAt</li>
 * </ul>
 */
@Component
public class AgentMapper {

    // ===== proto request → entity =====

    /**
     * CreateAgentRequest → AgentDefinition（新建，status/version/created_at/updated_at 由 entity 默认值）。
     */
    public AgentDefinition toEntity(CreateAgentRequest req) {
        AgentDefinition entity = new AgentDefinition();
        entity.setAgentId(req.getAgentId());
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setAbilityTags(new ArrayList<>(req.getAbilityTagsList()));
        entity.setSystemPrompt(req.getSystemPrompt());
        entity.setAgentTier(AgentTier.fromCode(req.getAgentTier()));
        entity.setMaxSteps(req.getMaxSteps() > 0 ? req.getMaxSteps() : 10);
        entity.setMaxToken(req.getMaxToken() > 0 ? req.getMaxToken() : 4096);
        entity.setBoundTools(new ArrayList<>(req.getBoundToolsList()));
        entity.setBoundKnowledgeIds(new ArrayList<>(req.getBoundKnowledgeIdsList()));
        // status / version / createdAt / updatedAt 由 entity 默认值或 @PrePersist 设置
        return entity;
    }

    /**
     * UpdateAgentRequest + 现有 entity → 合并后的 entity（只合并可更新字段）。
     *
     * <p>不覆盖：id / agentId / status / version / createdAt。
     * version 由调用方（GrpcService）在 save 前自增。</p>
     */
    public AgentDefinition mergeEntity(UpdateAgentRequest req, AgentDefinition existing) {
        existing.setName(req.getName());
        existing.setDescription(req.getDescription());
        existing.setAbilityTags(new ArrayList<>(req.getAbilityTagsList()));
        existing.setSystemPrompt(req.getSystemPrompt());
        existing.setAgentTier(AgentTier.fromCode(req.getAgentTier()));
        if (req.getMaxSteps() > 0) {
            existing.setMaxSteps(req.getMaxSteps());
        }
        if (req.getMaxToken() > 0) {
            existing.setMaxToken(req.getMaxToken());
        }
        existing.setBoundTools(new ArrayList<>(req.getBoundToolsList()));
        existing.setBoundKnowledgeIds(new ArrayList<>(req.getBoundKnowledgeIdsList()));
        return existing;
    }

    // ===== entity → proto response =====

    /**
     * AgentDefinition → AgentResponse proto（含全部字段）。
     */
    public AgentResponse toResponse(AgentDefinition entity) {
        AgentResponse.Builder b = AgentResponse.newBuilder()
                .setAgentId(entity.getAgentId())
                .setName(entity.getName())
                .setDescription(entity.getDescription() == null ? "" : entity.getDescription())
                .setSystemPrompt(entity.getSystemPrompt() == null ? "" : entity.getSystemPrompt())
                .setAgentTier(entity.getAgentTier() == null ? AgentTier.STANDARD.getCode() : entity.getAgentTier().getCode())
                .setMaxSteps(entity.getMaxSteps())
                .setMaxToken(entity.getMaxToken())
                .setStatus(entity.getStatus() == null ? AgentStatus.DRAFT.getCode() : entity.getStatus().getCode())
                .setVersion(entity.getVersion())
                .setCreatedAt(entity.getCreatedAt())
                .setUpdatedAt(entity.getUpdatedAt());
        b.addAllAbilityTags(entity.getAbilityTags());
        b.addAllBoundTools(entity.getBoundTools());
        b.addAllBoundKnowledgeIds(entity.getBoundKnowledgeIds());
        return b.build();
    }

    // ===== list request → query =====

    /**
     * ListAgentsRequest → RepoQuery（status / nameContains / page / size）。
     */
    public RepoQuery toQuery(ListAgentsRequest req) {
        RepoQuery query = new RepoQuery();
        if (!req.getStatus().isEmpty()) {
            query.setStatus(AgentStatus.fromCode(req.getStatus()));
        }
        if (!req.getNameContains().isEmpty()) {
            query.setNameContains(req.getNameContains());
        }
        query.setPage(req.getPage() >= 0 ? req.getPage() : 0);
        query.setSize(req.getSize() > 0 ? req.getSize() : 10);
        return query;
    }

    // ===== page result → proto response =====

    /**
     * PageResult&lt;AgentDefinition&gt; → ListAgentsResponse proto。
     */
    public ListAgentsResponse toListResponse(PageResult<AgentDefinition> page) {
        ListAgentsResponse.Builder b = ListAgentsResponse.newBuilder()
                .setTotal(page.getTotal())
                .setPage(page.getPage())
                .setSize(page.getSize());
        for (AgentDefinition entity : page.getItems()) {
            b.addItems(toResponse(entity));
        }
        return b.build();
    }
}
