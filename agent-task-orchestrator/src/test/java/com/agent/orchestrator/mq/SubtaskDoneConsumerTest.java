package com.agent.orchestrator.mq;

import com.agent.orchestrator.mq.event.SubtaskDoneEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * SubtaskDoneConsumer 单元测试（对齐 doc 03-task-engine §8.1.7）。
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>正常路径：委托 handler.handle(event)</li>
 *   <li>正常路径：handler 成功处理，onMessage 不抛异常</li>
 *   <li>异常路径：handler 抛 RuntimeException 时 onMessage 重抛（RocketMQ 会重试）</li>
 *   <li>异常路径：handler 抛 BusinessException 时 onMessage 重抛</li>
 *   <li>边界路径：eventId 为 null 时仍正常委托</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubtaskDoneConsumerTest {

    @Mock private SubtaskDoneHandler handler;
    @InjectMocks private SubtaskDoneConsumer consumer;

    @Test
    @DisplayName("onMessage 应委托给 handler.handle 当收到事件")
    void should_DelegateToHandler_When_EventReceived() {
        SubtaskDoneEvent event = SubtaskDoneEvent.builder()
                .eventId("evt_1")
                .taskId("tk_1")
                .nodeId("n_1")
                .status("success")
                .build();

        consumer.onMessage(event);

        verify(handler).handle(event);
    }

    @Test
    @DisplayName("onMessage 应正常完成 当 handler 成功处理")
    void should_CompleteNormally_When_HandlerSucceeds() {
        SubtaskDoneEvent event = SubtaskDoneEvent.builder()
                .eventId("evt_2")
                .taskId("tk_2")
                .status("success")
                .build();
        doNothing().when(handler).handle(event);

        assertThatCode(() -> consumer.onMessage(event))
                .doesNotThrowAnyException();

        verify(handler).handle(event);
    }

    @Test
    @DisplayName("onMessage 应重抛异常 当 handler 抛 RuntimeException")
    void should_RethrowException_When_HandlerThrowsRuntimeException() {
        SubtaskDoneEvent event = SubtaskDoneEvent.builder()
                .eventId("evt_3")
                .taskId("tk_3")
                .build();
        doThrow(new RuntimeException("DB error"))
                .when(handler).handle(event);

        assertThatThrownBy(() -> consumer.onMessage(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB error");

        verify(handler).handle(event);
    }

    @Test
    @DisplayName("onMessage 应重抛异常 当 handler 抛 IllegalArgumentException")
    void should_RethrowException_When_HandlerThrowsIllegalArgument() {
        SubtaskDoneEvent event = SubtaskDoneEvent.builder()
                .eventId("evt_4")
                .taskId("tk_4")
                .build();
        doThrow(new IllegalArgumentException("invalid status"))
                .when(handler).handle(event);

        assertThatThrownBy(() -> consumer.onMessage(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid status");

        verify(handler).handle(event);
    }

    @Test
    @DisplayName("onMessage 应正常委托 当 eventId 为 null")
    void should_DelegateToHandler_When_EventIdIsNull() {
        SubtaskDoneEvent event = SubtaskDoneEvent.builder()
                .eventId(null)
                .taskId("tk_5")
                .nodeId("n_5")
                .status("failed")
                .build();
        doNothing().when(handler).handle(event);

        assertThatCode(() -> consumer.onMessage(event))
                .doesNotThrowAnyException();

        verify(handler).handle(event);
    }

    @Test
    @DisplayName("onMessage 应重抛原始异常类型 当 handler 抛 NullPointerException")
    void should_PreserveOriginalExceptionType_When_HandlerThrowsNpe() {
        SubtaskDoneEvent event = SubtaskDoneEvent.builder().build();
        doThrow(new NullPointerException("null field"))
                .when(handler).handle(event);

        assertThatThrownBy(() -> consumer.onMessage(event))
                .isInstanceOf(NullPointerException.class);

        verify(handler).handle(event);
    }
}
