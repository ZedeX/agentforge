package com.agent.orchestrator.mq;

import com.agent.orchestrator.config.RocketMqProperties;
import com.agent.orchestrator.mq.event.SubtaskCancelEvent;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SubtaskCancelProducer 单元测试（对齐 doc 03-task-engine §7.1 task.subtask.cancel）。
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>正常路径：cancel 成功返回 msgId，自动填充 eventId/eventType/eventTime</li>
 *   <li>预填元数据保留</li>
 *   <li>destination 构造：topic:tenantId</li>
 *   <li>keys 头：taskId:nodeId</li>
 *   <li>syncSend 超时参数：5000L / 2（注意是 2 不是 3）</li>
 *   <li>异常路径：syncSend 抛异常时透传</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubtaskCancelProducerTest {

    @Mock private RocketMQTemplate rocketMQTemplate;
    @Mock private RocketMqProperties properties;
    @InjectMocks private SubtaskCancelProducer producer;

    @Test
    @DisplayName("cancel 应返回 msgId 当 syncSend 成功")
    void should_ReturnMsgId_When_SyncSendSuccess() {
        SubtaskCancelEvent event = SubtaskCancelEvent.builder()
                .taskId("tk_1")
                .nodeId("n_1")
                .tenantId(1001L)
                .reason("user cancelled")
                .build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_cancel_1");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        String msgId = producer.cancel(event);

        assertThat(msgId).isEqualTo("msg_cancel_1");
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo("task.subtask.cancel");
        assertThat(event.getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("cancel 应保留事件已有的 eventId/eventType/eventTime 当元数据非空")
    void should_PreserveExistingMetadata_When_EventHasMetadata() {
        SubtaskCancelEvent event = SubtaskCancelEvent.builder()
                .eventId("evt_pre")
                .eventType("custom.cancel")
                .eventTime("2026-06-01T10:00:00Z")
                .taskId("tk_2")
                .nodeId("n_2")
                .tenantId(2002L)
                .build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_cancel_2");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        String msgId = producer.cancel(event);

        assertThat(msgId).isEqualTo("msg_cancel_2");
        assertThat(event.getEventId()).isEqualTo("evt_pre");
        assertThat(event.getEventType()).isEqualTo("custom.cancel");
        assertThat(event.getEventTime()).isEqualTo("2026-06-01T10:00:00Z");
    }

    @Test
    @DisplayName("cancel 应构造 destination 为 topic:tenantId 格式 并 使用 5000L/2 参数")
    void should_BuildDestinationAndUseCorrectSendParams_When_Cancel() {
        SubtaskCancelEvent event = SubtaskCancelEvent.builder()
                .taskId("tk_3").nodeId("n_3").tenantId(3003L).build();
        RocketMqProperties.Topics topics = new RocketMqProperties.Topics();
        topics.setSubtaskCancel("custom.cancel.topic");
        when(properties.getTopics()).thenReturn(topics);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_cancel_3");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        producer.cancel(event);

        // 验证 destination = custom.cancel.topic:3003，超时 5000L，重试 2
        verify(rocketMQTemplate).syncSend(
                eq("custom.cancel.topic:3003"),
                any(Message.class),
                eq(5000L),
                eq(2));
    }

    @Test
    @DisplayName("cancel 应将 keys=taskId:nodeId 放入消息头 且 不设置 TAGS")
    void should_PutKeysHeaderButNotTags_When_Cancel() {
        SubtaskCancelEvent event = SubtaskCancelEvent.builder()
                .taskId("tk_4").nodeId("n_4").tenantId(1001L).build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_cancel_4");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        producer.cancel(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<SubtaskCancelEvent>> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(anyString(), captor.capture(),
                eq(5000L), eq(2));
        Message<SubtaskCancelEvent> sent = captor.getValue();
        assertThat(sent.getHeaders().get(RocketMQHeaders.KEYS))
                .isEqualTo("tk_4:n_4");
        // cancel 不设置 TAGS（与 ExecuteProducer 区分）
        assertThat(sent.getHeaders().get(RocketMQHeaders.TAGS)).isNull();
    }

    @Test
    @DisplayName("cancel 应抛异常 当 syncSend 抛异常")
    void should_ThrowException_When_SyncSendFails() {
        SubtaskCancelEvent event = SubtaskCancelEvent.builder()
                .taskId("tk_5").nodeId("n_5").tenantId(1001L).build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("broker unavailable"));

        assertThatThrownBy(() -> producer.cancel(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("broker unavailable");
    }
}
