package com.agent.orchestrator.mq;

import com.agent.orchestrator.config.RocketMqProperties;
import com.agent.orchestrator.mq.event.SubtaskExecuteEvent;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
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
 * SubtaskExecuteProducer 单元测试（对齐 doc 03-task-engine §7.2 + §8.1.5）。
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>正常路径：syncSend 成功返回 msgId，自动填充 eventId/eventType/eventTime</li>
 *   <li>预填元数据：保留事件已有的 eventId/eventType/eventTime</li>
 *   <li>destination 构造：topic:tenantId 格式</li>
 *   <li>keys 头：taskId:nodeId</li>
 *   <li>syncSend 超时参数：5000L / 3</li>
 *   <li>异常路径：syncSend 抛异常时透传</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubtaskExecuteProducerTest {

    @Mock private RocketMQTemplate rocketMQTemplate;
    @Mock private RocketMqProperties properties;
    @InjectMocks private SubtaskExecuteProducer producer;

    @Test
    @DisplayName("dispatch 应返回 msgId 当 syncSend 成功")
    void should_ReturnMsgId_When_SyncSendSuccess() {
        // Given
        SubtaskExecuteEvent event = SubtaskExecuteEvent.builder()
                .taskId("tk_1")
                .nodeId("n_1")
                .tenantId(1001L)
                .build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_123");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        // When
        String msgId = producer.dispatch(event);

        // Then
        assertThat(msgId).isEqualTo("msg_123");
        // fillEventMetadata 自动填充校验
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo("task.subtask.execute");
        assertThat(event.getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("dispatch 应保留事件已有的 eventId/eventType/eventTime 当元数据非空")
    void should_PreserveExistingMetadata_When_EventHasMetadata() {
        SubtaskExecuteEvent event = SubtaskExecuteEvent.builder()
                .eventId("custom_evt")
                .eventType("custom.type")
                .eventTime("2026-01-01T00:00:00Z")
                .taskId("tk_2")
                .nodeId("n_2")
                .tenantId(2002L)
                .build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_456");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        String msgId = producer.dispatch(event);

        assertThat(msgId).isEqualTo("msg_456");
        assertThat(event.getEventId()).isEqualTo("custom_evt");
        assertThat(event.getEventType()).isEqualTo("custom.type");
        assertThat(event.getEventTime()).isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("dispatch 应构造 destination 为 topic:tenantId 格式")
    void should_BuildDestinationAsTopicColonTenantId_When_Dispatch() {
        SubtaskExecuteEvent event = SubtaskExecuteEvent.builder()
                .taskId("tk_3").nodeId("n_3").tenantId(3003L).build();
        RocketMqProperties.Topics topics = new RocketMqProperties.Topics();
        topics.setSubtaskExecute("custom.execute.topic");
        when(properties.getTopics()).thenReturn(topics);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_789");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        producer.dispatch(event);

        // destination = custom.execute.topic:3003，超时 5000L，重试 3
        verify(rocketMQTemplate).syncSend(
                eq("custom.execute.topic:3003"),
                any(Message.class),
                eq(5000L),
                eq(3));
    }

    @Test
    @DisplayName("dispatch 应将 keys=taskId:nodeId 放入消息头")
    void should_PutKeysHeaderInMessage_When_Dispatch() {
        SubtaskExecuteEvent event = SubtaskExecuteEvent.builder()
                .taskId("tk_4").nodeId("n_4").tenantId(1001L).build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_keys");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        producer.dispatch(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<SubtaskExecuteEvent>> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(anyString(), captor.capture(),
                eq(5000L), eq(3));
        Message<SubtaskExecuteEvent> sent = captor.getValue();
        assertThat(sent.getHeaders().get(org.apache.rocketmq.spring.support.RocketMQHeaders.KEYS))
                .isEqualTo("tk_4:n_4");
        assertThat(sent.getHeaders().get(org.apache.rocketmq.spring.support.RocketMQHeaders.TAGS))
                .isEqualTo("1001");
    }

    @Test
    @DisplayName("dispatch 应使用默认 topic 当 properties.topics.subtaskExecute 未配置")
    void should_UseDefaultTopic_When_TopicsNotCustomized() {
        SubtaskExecuteEvent event = SubtaskExecuteEvent.builder()
                .taskId("tk_5").nodeId("n_5").tenantId(1001L).build();
        // new Topics() 默认 subtaskExecute="task.subtask.execute"
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_default");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        producer.dispatch(event);

        verify(rocketMQTemplate).syncSend(
                eq("task.subtask.execute:1001"),
                any(Message.class),
                eq(5000L),
                eq(3));
    }

    @Test
    @DisplayName("dispatch 应抛异常 当 syncSend 抛异常")
    void should_ThrowException_When_SyncSendFails() {
        SubtaskExecuteEvent event = SubtaskExecuteEvent.builder()
                .taskId("tk_6").nodeId("n_6").tenantId(1001L).build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("MQ broker offline"));

        assertThatThrownBy(() -> producer.dispatch(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MQ broker offline");
    }
}
