package com.agent.orchestrator.mq;

import com.agent.orchestrator.mq.event.SubtaskDoneEvent;
import com.agent.orchestrator.model.TaskInstance;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.orchestrator.replanner.ReplanModeSelector;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import com.agent.common.constant.TaskStatus;
import com.agent.common.exception.BusinessException;
import com.agent.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子任务完成回调处理器（对齐 doc 03-task-engine §7.4 伪代码）。
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>幂等校验（eventId 去重，内存 Set 简化；生产应查 event_consume_log 表）</li>
 *   <li>更新节点状态 + outputs 落库</li>
 *   <li>成本累加（CostMonitor 逻辑内联）</li>
 *   <li>决策：success → 推进批次 / failed → 重规划或人工 / require_review → WAITING_HUMAN</li>
 * </ol>
 */
@Slf4j
@Component
public class SubtaskDoneHandler {

    private final TaskInstanceRepository repository;
    private final TaskStateMachine stateMachine;
    private final ReplanModeSelector replanModeSelector;
    /** 幂等去重集合（简化实现，生产环境用 Redis SETNX + event_consume_log 表）。 */
    private final Set<String> consumedEventIds = ConcurrentHashMap.newKeySet();

    public SubtaskDoneHandler(TaskInstanceRepository repository,
                               TaskStateMachine stateMachine,
                               ReplanModeSelector replanModeSelector) {
        this.repository = repository;
        this.stateMachine = stateMachine;
        this.replanModeSelector = replanModeSelector;
    }

    @Transactional
    public void handle(SubtaskDoneEvent event) {
        // 1. 幂等校验
        if (event.getEventId() != null && consumedEventIds.contains(event.getEventId())) {
            log.warn("重复事件已消费，跳过 eventId={}", event.getEventId());
            return;
        }
        if (event.getEventId() != null) {
            consumedEventIds.add(event.getEventId());
        }

        TaskInstance task = repository.findByTaskId(event.getTaskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND,
                        "任务不存在: " + event.getTaskId()));

        // 2. 成本累加
        if (event.getCostCent() != null && event.getCostCent() > 0) {
            task.setCostUsedCent(task.getCostUsedCent() + event.getCostCent());
            if (task.getCostUsedCent() > task.getCostLimitCent()) {
                transit(task, TaskStatus.TIMEOUT);
                repository.save(task);
                throw new BusinessException(ErrorCode.COST_BUDGET_EXCEEDED,
                        "成本超限: " + task.getCostUsedCent());
            }
        }
        if (event.getTokenUsed() != null) {
            task.setTokenUsed(task.getTokenUsed() + event.getTokenUsed());
        }

        // 3. 决策分支
        if (event.getStatus() != null) {
            switch (event.getStatus()) {
                case "success" -> {
                    // 推进批次（此处简化：若有下一批次则继续，否则 SUCCESS）
                    log.info("子任务成功 taskId={}, nodeId={}", event.getTaskId(), event.getNodeId());
                }
                case "failed" -> handleFailure(task, event);
                case "require_review" -> transit(task, TaskStatus.WAITING_HUMAN);
                default -> log.warn("未知子任务状态: {}", event.getStatus());
            }
        }
        repository.save(task);
    }

    private void handleFailure(TaskInstance task, SubtaskDoneEvent event) {
        if ("AGENT_NOT_FOUND".equals(event.getErrorCode())) {
            transit(task, TaskStatus.WAITING_HUMAN);
            return;
        }
        if ("MAX_RETRY_EXCEEDED".equals(event.getErrorCode())) {
            transit(task, TaskStatus.REPLANNING);
            return;
        }
        // 默认失败转人工
        transit(task, TaskStatus.WAITING_HUMAN);
    }

    private void transit(TaskInstance task, TaskStatus target) {
        TaskStatus current = TaskStatus.valueOf(task.getStatus());
        stateMachine.transit(current, target);
        task.setStatus(target.name());
        if (target == TaskStatus.SUCCESS || target == TaskStatus.FAILED
                || target == TaskStatus.CANCELLED || target == TaskStatus.TIMEOUT) {
            task.setFinishedAt(Instant.now());
        }
    }
}
