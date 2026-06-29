package com.agent.orchestrator.mq;

import com.agent.orchestrator.config.RocketMqProperties;
import com.agent.orchestrator.mq.event.StateChangeEvent;
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
 * StateChangeProducer 单元测试（对齐 doc 03-task-engine §6.3）。
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>正常路径：broadcast 成功返回 msgId，自动填充 createdAt</li>
 *   <li>预填 createdAt 保留</li>
 *   <li>destination 构造：topic:tenantId</li>
 *   <li>keys 头：taskId:fromStatus->toStatus</li>
 *   <li>syncSend 超时参数：3000L / 1（注意是 1 不是 3/2）</li>
 *   <li>异常路径：syncSend 抛异常时透传</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StateChangeProducerTest {

    @Mock private RocketMQTemplate rocketMQTemplate;
    @Mock private RocketMqProperties properties;
    @InjectMocks private StateChangeProducer producer;

    @Test
    @DisplayName("broadcast 应返回 msgId 当 syncSend 成功 并 自动填充 createdAt")
    void should_ReturnMsgId_When_SyncSendSuccess() {
        StateChangeEvent event = StateChangeEvent.builder()
                .taskId("tk_1")
                .fromStatus("PENDING")
                .toStatus("RUNNING")
                .tenantId(1001L)
                .operator("u_1")
                .trigger("auto")
                .build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_state_1");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        String msgId = producer.broadcast(event);

        assertThat(msgId).isEqualTo("msg_state_1");
        assertThat(event.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("broadcast 应保留事件已有的 createdAt 当字段非空")
    void should_PreserveExistingCreatedAt_When_FieldNotNull() {
        StateChangeEvent event = StateChangeEvent.builder()
                .taskId("tk_2")
                .fromStatus("RUNNING")
                .toStatus("SUCCESS")
                .tenantId(2002L)
                .createdAt("2026-06-29T12:00:00Z")
                .build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_state_2");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        String msgId = producer.broadcast(event);

        assertThat(msgId).isEqualTo("msg_state_2");
        assertThat(event.getCreatedAt()).isEqualTo("2026-06-29T12:00:00Z");
    }

    @Test
    @DisplayName("broadcast 应构造 destination 为 topic:tenantId 格式 并 使用 3000L/1 参数")
    void should_BuildDestinationAndUseCorrectSendParams_When_Broadcast() {
        StateChangeEvent event = StateChangeEvent.builder()
                .taskId("tk_3")
                .fromStatus("PENDING")
                .toStatus("RUNNING")
                .tenantId(3003L)
                .build();
        RocketMqProperties.Topics topics = new RocketMqProperties.Topics();
        topics.setStateChange("custom.state.topic");
        when(properties.getTopics()).thenReturn(topics);
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_state_3");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        producer.broadcast(event);

        verify(rocketMQTemplate).syncSend(
                eq("custom.state.topic:3003"),
                any(Message.class),
                eq(3000L),
                eq(1));
    }

    @Test
    @DisplayName("broadcast 应将 keys=taskId:fromStatus->toStatus 放入消息头")
    void should_PutKeysHeaderWithStatusTransition_When_Broadcast() {
        StateChangeEvent event = StateChangeEvent.builder()
                .taskId("tk_4")
                .fromStatus("PENDING")
                .toStatus("RUNNING")
                .tenantId(1001L)
                .build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_state_4");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        producer.broadcast(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<StateChangeEvent>> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(anyString(), captor.capture(),
                eq(3000L), eq(1));
        Message<StateChangeEvent> sent = captor.getValue();
        assertThat(sent.getHeaders().get(RocketMQHeaders.KEYS))
                .isEqualTo("tk_4:PENDING->RUNNING");
    }

    @Test
    @DisplayName("broadcast 应使用默认 topic 当 properties.topics.stateChange 未配置")
    void should_UseDefaultTopic_When_TopicsNotCustomized() {
        StateChangeEvent event = StateChangeEvent.builder()
                .taskId("tk_5")
                .fromStatus("RUNNING")
                .toStatus("SUCCESS")
                .tenantId(1001L)
                .build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        SendResult sendResult = mock(SendResult.class);
        when(sendResult.getMsgId()).thenReturn("msg_state_default");
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenReturn(sendResult);

        producer.broadcast(event);

        verify(rocketMQTemplate).syncSend(
                eq("task.state.change:1001"),
                any(Message.class),
                eq(3000L),
                eq(1));
    }

    @Test
    @DisplayName("broadcast 应抛异常 当 syncSend 抛异常")
    void should_ThrowException_When_SyncSendFails() {
        StateChangeEvent event = StateChangeEvent.builder()
                .taskId("tk_6")
                .fromStatus("PENDING")
                .toStatus("RUNNING")
                .tenantId(1001L)
                .build();
        when(properties.getTopics()).thenReturn(new RocketMqProperties.Topics());
        when(rocketMQTemplate.syncSend(anyString(), any(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("network timeout"));

        assertThatThrownBy(() -> producer.broadcast(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("network timeout");
    }
}
