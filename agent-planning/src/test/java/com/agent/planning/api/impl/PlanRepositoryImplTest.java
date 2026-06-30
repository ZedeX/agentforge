package com.agent.planning.api.impl;

import com.agent.planning.enums.PlanStatus;
import com.agent.planning.model.Plan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PlanRepositoryImpl unit tests (doc 03-task-engine §8.2.1 plan storage).
 */
@DisplayName("PlanRepositoryImpl 计划仓储")
class PlanRepositoryImplTest {

    private final PlanRepositoryImpl repo = new PlanRepositoryImpl();

    @Test
    @DisplayName("save 后 findById 能查到")
    void should_FindById_When_Saved() {
        Plan plan = new Plan("plan-1", "task-1");
        plan.setTenantId("t1");
        repo.save(plan);

        Optional<Plan> found = repo.findById("plan-1");
        assertThat(found).isPresent();
        assertThat(found.get().getTaskId()).isEqualTo("task-1");
        assertThat(found.get().getId()).isNotNull();
    }

    @Test
    @DisplayName("findByTaskId 返回所有关联计划")
    void should_FindByTaskId_When_MultiplePlansForSameTask() {
        Plan p1 = new Plan("plan-1", "task-A");
        Plan p2 = new Plan("plan-2", "task-A");
        Plan p3 = new Plan("plan-3", "task-B");
        repo.save(p1);
        repo.save(p2);
        repo.save(p3);

        List<Plan> results = repo.findByTaskId("task-A");
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(p -> "task-A".equals(p.getTaskId()));
    }

    @Test
    @DisplayName("findById 不存在返回 empty")
    void should_ReturnEmpty_When_NotFound() {
        assertThat(repo.findById("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("findById null 或空串返回 empty")
    void should_ReturnEmpty_When_IdNullOrEmpty() {
        assertThat(repo.findById(null)).isEmpty();
        assertThat(repo.findById("")).isEmpty();
    }

    @Test
    @DisplayName("findByTaskId null 或空串返回空列表")
    void should_ReturnEmptyList_When_TaskIdNullOrEmpty() {
        assertThat(repo.findByTaskId(null)).isEmpty();
        assertThat(repo.findByTaskId("")).isEmpty();
    }

    @Test
    @DisplayName("save null 或 null planId 返回 null")
    void should_ReturnNull_When_PlanOrPlanIdNull() {
        assertThat(repo.save(null)).isNull();
        Plan nullId = new Plan();
        nullId.setPlanId(null);
        assertThat(repo.save(nullId)).isNull();
    }

    @Test
    @DisplayName("save 空 planId 返回 null")
    void should_ReturnNull_When_PlanIdEmpty() {
        Plan emptyId = new Plan();
        emptyId.setPlanId("");
        assertThat(repo.save(emptyId)).isNull();
    }

    @Test
    @DisplayName("updateStatus 成功更新状态")
    void should_UpdateStatus_When_PlanExists() {
        Plan plan = new Plan("plan-1", "task-1");
        repo.save(plan);

        boolean updated = repo.updateStatus("plan-1", "validated");
        assertThat(updated).isTrue();
        assertThat(repo.findById("plan-1").get().getStatus()).isEqualTo(PlanStatus.VALIDATED);
    }

    @Test
    @DisplayName("updateStatus 不存在返回 false")
    void should_ReturnFalse_When_PlanNotFound() {
        assertThat(repo.updateStatus("nonexistent", "validated")).isFalse();
    }

    @Test
    @DisplayName("updateStatus null 参数返回 false")
    void should_ReturnFalse_When_ArgsNull() {
        Plan plan = new Plan("plan-1", "task-1");
        repo.save(plan);
        assertThat(repo.updateStatus(null, "validated")).isFalse();
        assertThat(repo.updateStatus("plan-1", null)).isFalse();
        assertThat(repo.updateStatus("", "validated")).isFalse();
    }

    @Test
    @DisplayName("updateStatus 未知状态码兜底为 DRAFT")
    void should_DefaultToDraft_When_StatusUnknown() {
        Plan plan = new Plan("plan-1", "task-1");
        repo.save(plan);

        repo.updateStatus("plan-1", "unknown-status");
        assertThat(repo.findById("plan-1").get().getStatus()).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    @DisplayName("delete 已存在计划返回 true")
    void should_ReturnTrue_When_DeleteExisting() {
        Plan plan = new Plan("plan-1", "task-1");
        repo.save(plan);

        assertThat(repo.delete("plan-1")).isTrue();
        assertThat(repo.findById("plan-1")).isEmpty();
    }

    @Test
    @DisplayName("delete 不存在返回 false")
    void should_ReturnFalse_When_DeleteNonExistent() {
        assertThat(repo.delete("nonexistent")).isFalse();
    }

    @Test
    @DisplayName("delete null 或空串返回 false")
    void should_ReturnFalse_When_DeleteWithNullOrEmpty() {
        assertThat(repo.delete(null)).isFalse();
        assertThat(repo.delete("")).isFalse();
    }

    @Test
    @DisplayName("重复 save 同 planId 覆盖旧值 (upsert)")
    void should_Overwrite_When_SamePlanIdReSaved() {
        Plan p1 = new Plan("plan-1", "task-1");
        repo.save(p1);

        Plan p2 = new Plan("plan-1", "task-updated");
        repo.save(p2);

        Optional<Plan> found = repo.findById("plan-1");
        assertThat(found).isPresent();
        assertThat(found.get().getTaskId()).isEqualTo("task-updated");
    }
}
