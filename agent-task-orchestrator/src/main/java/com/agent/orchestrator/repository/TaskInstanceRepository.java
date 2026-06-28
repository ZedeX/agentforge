package com.agent.orchestrator.repository;

import com.agent.orchestrator.model.TaskInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 任务实例 Repository（对齐 task_instance 表）。
 *
 * <p>当前阶段仅声明基础查询方法，复杂查询（按状态/租户/复杂度分页）留待 T5+。</p>
 */
@Repository
public interface TaskInstanceRepository extends JpaRepository<TaskInstance, Long> {

    Optional<TaskInstance> findByTaskId(String taskId);
}
