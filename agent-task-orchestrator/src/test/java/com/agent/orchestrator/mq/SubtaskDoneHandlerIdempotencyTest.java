package com.agent.orchestrator.mq;

import com.agent.orchestrator.entity.EventConsumeLog;
import com.agent.orchestrator.model.TaskInstance;
import com.agent.orchestrator.mq.event.SubtaskDoneEvent;
import com.agent.orchestrator.repository.EventConsumeLogRepository;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.orchestrator.replanner.ReplanModeSelector;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-03 修复验证：RocketMQ 消费幂等（JPA event_consume_log 表方案）。
 *
 * <p>使用 {@link DataJpaTest} + H2 内存数据库验证 SubtaskDoneHandler 的幂等去重
 * 已从内存 ConcurrentHashMap.newKeySet() 替换为 JPA 唯一约束方案。
 * 覆盖三个核心场景：</p>
 * <ol>
 *   <li>相同 eventId 第二次调用应被跳过，event_consume_log 仅一条记录</li>
 *   <li>不同 eventId 均被处理，event_consume_log 有两条记录</li>
 *   <li>eventId 为 null 时基于 taskId+nodeId+status 生成去重键，相同组合跳过</li>
 * </ol>
 *
 * <p>设计说明：</p>
 * <ul>
 *   <li>{@link Import} 引入 SubtaskDoneHandler（@DataJpaTest 默认不扫描 @Component）</li>
 *   <li>{@link MockBean} 替换 TaskStateMachine / ReplanModeSelector（非 JPA 依赖，
 *       且 success 分支不触发状态流转）</li>
 *   <li>{@link TestPropertySource} 覆盖 ddl-auto=validate → create-drop，
 *       dialect → H2Dialect（主 application.yml 为 MySQL 配置）</li>
 * </ul>
 */
@DataJpaTest
@Import(SubtaskDoneHandler.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false"
})
class SubtaskDoneHandlerIdempotencyTest {

    @Autowired private SubtaskDoneHandler handler;
    @Autowired private TaskInstanceRepository taskRepository;
    @Autowired private EventConsumeLogRepository eventConsumeLogRepository;
    @Autowired private TestEntityManager entityManager;

    @MockBean private TaskStateMachine stateMachine;
    @MockBean private ReplanModeSelector replanModeSelector;

    /** 构造一个处于 SUBTASK_RUNNING 状态、成本/token 清零的 TaskInstance 并落库。 */
    private TaskInstance saveTask(String taskId) {
        TaskInstance task = TaskInstance.builder()
                .taskId(taskId)
                .tenantId(1001L)
                .userId("u_1")
                .title("幂等测试任务")
                .goal("幂等测试目标")
                .complexity(2)
                .status("SUBTASK_RUNNING")
                .taskSchema("{}")
                .priority(5)
                .replanCount(0)
                .costLimitCent(100000L)
                .costUsedCent(0L)
                .tokenUsed(0)
                .build();
        return taskRepository.save(task);
    }

    private SubtaskDoneEvent doneEvent(String eventId, String taskId,
                                        String status, Long costCent) {
        return SubtaskDoneEvent.builder()
                .eventId(eventId)
                .taskId(taskId)
                .subtaskId("st_1")
                .nodeId("n_1")
                .status(status)
                .costCent(costCent)
                .build();
    }

    // ============ 场景 1: 相同 eventId 幂等跳过 ============

    @Test
    @DisplayName("相同 eventId 第二次调用应被跳过，event_consume_log 仅一条记录")
    void should_SkipSecondCall_When_SameEventId() {
        saveTask("tk_idem_1");
        SubtaskDoneEvent event = doneEvent("ev-dup-001", "tk_idem_1", "success", 100L);

        handler.handle(event);
        handler.handle(event);

        assertThat(eventConsumeLogRepository.count()).isEqualTo(1);
        // 业务副作用验证：成本仅累加一次（100），证明第二次被跳过
        entityManager.flush();
        entityManager.clear();
        TaskInstance updated = taskRepository.findByTaskId("tk_idem_1").orElseThrow();
        assertThat(updated.getCostUsedCent()).isEqualTo(100L);
    }

    // ============ 场景 2: 不同 eventId 均被处理 ============

    @Test
    @DisplayName("不同 eventId 应均被处理，event_consume_log 有两条记录")
    void should_ProcessBoth_When_DifferentEventIds() {
        saveTask("tk_idem_2");
        SubtaskDoneEvent e1 = doneEvent("ev-a", "tk_idem_2", "success", 100L);
        SubtaskDoneEvent e2 = doneEvent("ev-b", "tk_idem_2", "success", 100L);

        handler.handle(e1);
        handler.handle(e2);

        assertThat(eventConsumeLogRepository.count()).isEqualTo(2);
        // 业务副作用验证：成本累加两次（100+100=200），证明两次都被处理
        entityManager.flush();
        entityManager.clear();
        TaskInstance updated = taskRepository.findByTaskId("tk_idem_2").orElseThrow();
        assertThat(updated.getCostUsedCent()).isEqualTo(200L);
    }

    // ============ 场景 3: null eventId 生成去重键 ============

    @Test
    @DisplayName("eventId 为 null 时应基于 taskId+nodeId+status 生成去重键，相同组合跳过")
    void should_GenerateDedupKey_When_EventIdIsNull() {
        saveTask("tk_idem_3");
        SubtaskDoneEvent e1 = doneEvent(null, "tk_idem_3", "success", 100L);
        SubtaskDoneEvent e2 = doneEvent(null, "tk_idem_3", "success", 100L);

        handler.handle(e1);
        handler.handle(e2);

        // 相同 taskId+nodeId+status 生成相同去重键 → 第二次跳过
        assertThat(eventConsumeLogRepository.count()).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();
        TaskInstance updated = taskRepository.findByTaskId("tk_idem_3").orElseThrow();
        assertThat(updated.getCostUsedCent()).isEqualTo(100L);
    }
}
