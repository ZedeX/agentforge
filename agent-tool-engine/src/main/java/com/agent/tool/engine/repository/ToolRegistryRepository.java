package com.agent.tool.engine.repository;

import com.agent.tool.engine.entity.ToolRegistryEntity;
import com.agent.tool.engine.enums.ToolStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ToolRegistry JPA Repository (Plan 05 T2).
 *
 * <p>提供按 toolId / name / status / riskLevel 等维度的查询方法,
 * 供 ToolRegistryImpl (T3) / ToolSemanticRecaller (T10) 使用.</p>
 */
@Repository
public interface ToolRegistryRepository extends JpaRepository<ToolRegistryEntity, Long> {

    /** 按工具业务 ID 查询. */
    Optional<ToolRegistryEntity> findByToolId(String toolId);

    /** 按工具名查询. */
    Optional<ToolRegistryEntity> findByName(String name);

    /** 按状态查询（启用/草稿/下线）. */
    List<ToolRegistryEntity> findByStatus(int status);

    /** 按状态枚举查询. */
    default List<ToolRegistryEntity> findByStatusEnum(ToolStatus status) {
        return findByStatus(status.getCode());
    }

    /** 按风险等级查询. */
    List<ToolRegistryEntity> findByRiskLevel(int riskLevel);

    /** 按 toolId + version 查询（版本精确匹配）. */
    Optional<ToolRegistryEntity> findByToolIdAndVersion(String toolId, int version);

    /** 按状态 + 风险等级查询. */
    List<ToolRegistryEntity> findByStatusAndRiskLevel(int status, int riskLevel);

    /** 场景标签 LIKE 查询（MySQL JSON 函数由业务层组装, 这里走简单 LIKE）. */
    @Query("SELECT t FROM ToolRegistryEntity t WHERE t.sceneTags LIKE %:tag%")
    List<ToolRegistryEntity> findBySceneTagLike(@Param("tag") String tag);

    /** 能力标签 LIKE 查询. */
    @Query("SELECT t FROM ToolRegistryEntity t WHERE t.abilityTags LIKE %:tag%")
    List<ToolRegistryEntity> findByAbilityTagLike(@Param("tag") String tag);

    /** 检查 toolId 是否已存在. */
    boolean existsByToolId(String toolId);

    /** 检查 name 是否已存在. */
    boolean existsByName(String name);
}
