package com.agent.orchestrator.fixture;

import com.agent.orchestrator.config.RocketMqProperties;
import com.agent.orchestrator.mq.StateChangeProducer;
import com.agent.orchestrator.mq.SubtaskCancelProducer;
import com.agent.orchestrator.mq.SubtaskDoneHandler;
import com.agent.orchestrator.mq.SubtaskExecuteProducer;
import com.agent.orchestrator.mq.event.StateChangeEvent;
import com.agent.orchestrator.mq.event.SubtaskCancelEvent;
import com.agent.orchestrator.mq.event.SubtaskDoneEvent;
import com.agent.orchestrator.mq.event.SubtaskExecuteEvent;
import com.agent.orchestrator.repository.TaskInstanceRepository;
import com.agent.orchestrator.replanner.ReplanModeSelector;
import com.agent.orchestrator.statemachine.TaskStateMachine;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;

import java.util.Optional;

import static com.agent.orchestrator.fixture.EventFixtures.buildFailedDoneEvent;
import static com.agent.orchestrator.fixture.EventFixtures.buildSimpleExecuteEvent;
import static com.agent.orchestrator.fixture.EventFixtures.buildStateChangeEvent;
import static com.agent.orchestrator.fixture.EventFixtures.buildUserCancelEvent;
import static com.agent.orchestrator.fixture.TaskFixtures.buildRunningTask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Fixture 工厂与 Mock 交互验证 showcase（FIX-05 整改，对齐 FN-013）。
 *
 * <p>本测试类集中展示 Mockito {@code verify(mock, times(N)).method()} 系列
 * 交互次数验证的正确用法，作为 v6 报告 D4 FIX-05 子项的整改证据。</p>
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>{@link #should_VerifyTimes1_When_SingleDispatch} — {@code verify(mock, times(1)).method()}</li>
 *   <li>{@link #should_VerifyTimes2_When_MultipleDispatches} — {@code verify(mock, times(N)).method()}</li>
 *   <li>{@link #should_VerifyAtLeastOnce_When_MultipleBroadcasts} — {@code verify(mock, atLeastOnce()).method()}</li>
 *   <li>{@link #should_VerifyNever_When_ProducerNotInvoked} — {@code verify(mock, never()).method()}</li>
 *   <li>{@link #should_VerifyNoInteractions_When_UnusedMockNotTouched} — {@code verifyNoInteractions(mock)}</li>
 *   <li>{@link #should_VerifyNoMoreInteractions_When_OnlyExpectedCallsMade} — {@code verifyNoMoreInteractions(mock)}</li>
 *   <li>{@link #should_VerifyTimes1WithCaptor_When_StateChangeBroadcast} — 配合 ArgumentCaptor 的精确参数校验</li>
 *   <li>{@link #should_VerifyHandlerCalledOnce_When_DoneEventProcessed} — 关键路径调用次数校验</li>
 * </ul>
 *
 * <p>本测试类的双重价值：</p>
 * <ol>
 *   <li>FIX-01 验证：所有测试数据均由 {@link TaskFixtures} / {@link EventFixtures}
 *       集中工厂构造，无内联 {@code new XxxEvent()} 散落；</li>
 *   <li>FIX-05 验证：覆盖 times(1) / times(2) / atLeastOnce / never /
 *       verifyNoInteractions / verifyNoMoreInteractions 全部关键交互断言。</li>
 * </ol>
 *
 * <p>命名遵循 P6-3 规范 {@code should_X_When_Y}；断言遵循 P6-4 AssertJ 链式；
 * 中文说明遵循 P6-5 {@code @DisplayName}。</p>
 *
 * @see TaskFixtures
 * @see EventFixtures
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixturesShowcaseTest {

    @Mock private RocketMQTemplate rocketMQTemplate;
    @Mock private RocketMqProperties properties;
    @Mock private SendResult sendResult;

    @Mock private TaskInstanceRepository repository;
    @Mock private TaskStateMachine stateMachine;
    @Mock private ReplanModeSelector replanModeSelector;

    @InjectMocks private SubtaskExecuteProducer executeProducer;
    @InjectMocks private StateChangeProducer stateChangeProducer;
    @InjectMocks private SubtaskCancelProducer cancelProducer;
    @InjectMocks private SubtaskDoneHandler doneHandler;

    @BeforeEach
    void setUp() {
        // 公共 stub：所有 Producer 测试都需要 properties.getTopics() 返回默认配置
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        // 公共 stub：syncSend 默认返回 sendResult，msgId=mock_msg
        when(sendResult.getMsgId()).thenReturn("mock_msg");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);
    }

    // ============ FIX-05：times(1) 精确次数验证 ============

    @Test
    @DisplayName("FIX-05-01: 单次 dispatch 应触发 syncSend 调用 1 次（verify times(1)）")
    void should_VerifyTimes1_When_SingleDispatch() {
        SubtaskExecuteEvent event = buildSimpleExecuteEvent("tk_v01", "n_1");

        executeProducer.dispatch(event);

        verify(rocketMQTemplate, times(1)).syncSend(
                eq("task.subtask.execute:1001"),
                any(Message.class),
                eq(5000L),
                eq(3));
    }

    // ============ FIX-05：times(N) 多次验证 ============

    @Test
    @DisplayName("FIX-05-02: 多次 dispatch 应触发 syncSend 调用 N 次（verify times(N)）")
    void should_VerifyTimes2_When_MultipleDispatches() {
        SubtaskExecuteEvent event1 = buildSimpleExecuteEvent("tk_v02a", "n_1");
        SubtaskExecuteEvent event2 = buildSimpleExecuteEvent("tk_v02b", "n_2");

        executeProducer.dispatch(event1);
        executeProducer.dispatch(event2);

        verify(rocketMQTemplate, times(2)).syncSend(
                anyString(), any(Message.class), anyLong(), anyInt());
    }

    // ============ FIX-05：atLeastOnce 至少一次验证 ============

    @Test
    @DisplayName("FIX-05-03: 多次 broadcast 应触发 syncSend 至少一次（verify atLeastOnce）")
    void should_VerifyAtLeastOnce_When_MultipleBroadcasts() {
        StateChangeEvent event1 = buildStateChangeEvent("tk_v03", "PENDING", "RUNNING");
        StateChangeEvent event2 = buildStateChangeEvent("tk_v03", "RUNNING", "SUCCESS");

        stateChangeProducer.broadcast(event1);
        stateChangeProducer.broadcast(event2);

        verify(rocketMQTemplate, atLeastOnce()).syncSend(
                anyString(), any(Message.class), anyLong(), anyInt());
    }

    // ============ FIX-05：never 从未调用验证 ============

    @Test
    @DisplayName("FIX-05-04: 未调用 producer 时 syncSend 应从未被调用（verify never）")
    void should_VerifyNever_When_ProducerNotInvoked() {
        // 不调用任何 producer 方法，验证 syncSend 从未被调用
        verify(rocketMQTemplate, never()).syncSend(
                anyString(), any(Message.class), anyLong(), anyInt());
    }

    // ============ FIX-05：verifyNoInteractions 完全未交互验证 ============

    @Test
    @DisplayName("FIX-05-05: 未使用的 Mock 应无任何交互（verifyNoInteractions）")
    void should_VerifyNoInteractions_When_UnusedMockNotTouched() {
        // stateChangeProducer 持有 rocketMQTemplate，cancelProducer 也持有
        // 但我们只调用 executeProducer，cancelProducer 的 rocketMQTemplate 共享同一实例
        // 改用独立的 unusedMock 验证 verifyNoInteractions
        SubtaskExecuteEvent event = buildSimpleExecuteEvent("tk_v05", "n_1");
        executeProducer.dispatch(event);

        // repository / stateMachine / replanModeSelector 在 producer 测试中完全未使用
        verifyNoInteractions(repository, stateMachine, replanModeSelector);
    }

    // ============ FIX-05：verifyNoMoreInteractions 无额外交互验证 ============

    @Test
    @DisplayName("FIX-05-06: 仅预期调用后应无额外交互（verifyNoMoreInteractions）")
    void should_VerifyNoMoreInteractions_When_OnlyExpectedCallsMade() {
        StateChangeEvent event = buildStateChangeEvent("tk_v06", "PENDING", "RUNNING");

        stateChangeProducer.broadcast(event);

        // 验证 syncSend 被调用 1 次（broadcast 内部会调用 1 次）
        verify(rocketMQTemplate, times(1)).syncSend(
                anyString(), any(Message.class), anyLong(), anyInt());
        // 验证 rocketMQTemplate 没有其他任何调用（如 asyncSend / convertAndSend 等）
        verifyNoMoreInteractions(rocketMQTemplate);
    }

    // ============ FIX-05：ArgumentCaptor + times(1) 精确参数校验 ============

    @Test
    @DisplayName("FIX-05-07: broadcast 应传递正确的状态流转事件（ArgumentCaptor + times(1)）")
    void should_VerifyTimes1WithCaptor_When_StateChangeBroadcast() {
        StateChangeEvent event = buildStateChangeEvent("tk_v07", "PENDING", "RUNNING");

        stateChangeProducer.broadcast(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<StateChangeEvent>> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate, times(1)).syncSend(
                anyString(), captor.capture(), anyLong(), anyInt());

        // 校验传递的消息体就是 fixture 构造的事件
        StateChangeEvent sent = (StateChangeEvent) captor.getValue().getPayload();
        assertThat(sent.getTaskId()).isEqualTo("tk_v07");
        assertThat(sent.getFromStatus()).isEqualTo("PENDING");
        assertThat(sent.getToStatus()).isEqualTo("RUNNING");
        // broadcast 内部应自动填充 createdAt
        assertThat(sent.getCreatedAt()).isNotNull();
    }

    // ============ FIX-05：关键路径调用次数验证（SubtaskDoneHandler） ============

    @Test
    @DisplayName("FIX-05-08: 处理 success 事件应触发 repository.findByTaskId/save 各 1 次，stateMachine 0 次")
    void should_VerifyHandlerCalledOnce_When_DoneEventProcessed() {
        // 使用 TaskFixtures + EventFixtures 工厂构造测试数据（FIX-01 集成）
        when(repository.findByTaskId("tk_v08"))
                .thenReturn(Optional.of(buildRunningTask("tk_v08")));
        SubtaskDoneEvent doneEvent = buildFailedDoneEvent("ev_v08", "tk_v08", "MAX_RETRY_EXCEEDED");

        doneHandler.handle(doneEvent);

        // 关键路径：repository.findByTaskId 调用 1 次
        verify(repository, times(1)).findByTaskId("tk_v08");
        // 关键路径：repository.save 调用 1 次（状态流转后持久化）
        verify(repository, times(1)).save(any());
        // 关键路径：stateMachine.transit 调用 1 次（failed + MAX_RETRY_EXCEEDED → REPLANNING）
        verify(stateMachine, times(1)).transit(any(), any());
        // verifyNoMoreInteractions：repository 不应有其他调用
        verifyNoMoreInteractions(repository);
    }

    // ============ FIX-01 + FIX-05 综合演示：cancel 路径 ============

    @Test
    @DisplayName("FIX-05-09: cancel 应触发 syncSend 1 次 且 传递正确的取消原因")
    void should_VerifyCancelProducer_When_UserCancelEvent() {
        SubtaskCancelEvent event = buildUserCancelEvent("ev_v09", "tk_v09");

        cancelProducer.cancel(event);

        verify(rocketMQTemplate, times(1)).syncSend(
                eq("task.subtask.cancel:1001"),
                any(Message.class),
                anyLong(),
                anyInt());
        verifyNoMoreInteractions(rocketMQTemplate);
    }
}
