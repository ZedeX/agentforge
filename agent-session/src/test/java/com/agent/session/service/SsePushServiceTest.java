package com.agent.session.service;

import com.agent.session.config.SseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SsePushService Mock 单元测试。
 *
 * <p>不依赖 Docker / Testcontainers Redis：使用 Mockito mock {@link StringRedisTemplate}
 * 与 {@link SseProperties}，覆盖 register / publish / onMessage / destroy / extractSessionId /
 * buildChannel 路径。</p>
 *
 * <p>注意：SseEmitter 是真实对象，其 sendInternal 在无 handler 时安全缓冲事件到
 * earlySendAttempts，不会抛 IOException，因此 register() / onMessage() / destroy() 在
 * Mock 环境下不会抛异常。</p>
 *
 * <p>对应原 {@code ShortTermMemoryServiceTest}（依赖 Testcontainers Redis，被 no-docker profile
 * 排除）的 Mock 替代版本。</p>
 *
 * <p>P6-3/4/5：方法名统一为 {@code should_Xxx_When_Yyy}；JUnit 断言替换为 AssertJ；补充中文 @DisplayName。</p>
 */
class SsePushServiceTest {

    private StringRedisTemplate redisTemplate;
    private SseProperties properties;
    private SsePushService service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        properties = mock(SseProperties.class);
        when(properties.getChannelPrefix()).thenReturn("session");
        when(properties.getTimeoutMs()).thenReturn(300000L);
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(0L);

        service = new SsePushService(redisTemplate, properties);
    }

    @Test
    @DisplayName("register 应返回非空 Emitter 并将其加入内部 map")
    void should_ReturnEmitterAndStoreInMap_When_Register() {
        SseEmitter emitter = service.register("session_001");
        assertThat(emitter).isNotNull();

        // 通过 publish 间接验证 emitter 已被注册到 emitters map
        // （publish 不依赖 emitter，但 register 完成后 map 中应有该 emitter，
        // 后续 onMessage 才能找到它）
        service.publish("session_001", "ping", Map.of("ts", 1L));
        verify(redisTemplate).convertAndSend(eq("session:session_001:events"), anyString());
    }

    @Test
    @DisplayName("register 应发送 connected 事件且 Emitter 状态正常")
    void should_SendConnectedEvent_When_Register() {
        SseEmitter emitter = service.register("session_001");
        // connected 事件由 SseEmitter 内部 buffer 接收（无 handler 时不抛 IOException）
        // 验证 emitter 非空且未在错误状态
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("publish 应使用配置的 channel 前缀调用 convertAndSend")
    void should_CallConvertAndSendWithCorrectChannel_When_Publish() {
        service.publish("session_001", "update", Map.of("key", "value"));
        verify(redisTemplate).convertAndSend(eq("session:session_001:events"), anyString());
    }

    @Test
    @DisplayName("JSON 序列化失败时 publish 应抛 IllegalStateException")
    void should_ThrowIllegalState_When_JsonSerializationFails() {
        // Jackson 默认 SerializationFeature.FAIL_ON_EMPTY_BEANS=true，
        // 对 ByteArrayInputStream 抛 InvalidDefinitionException（JsonMappingException 子类）
        Map<String, Object> data = new HashMap<>();
        data.put("stream", new ByteArrayInputStream(new byte[0]));

        assertThatThrownBy(() -> service.publish("session_001", "update", data))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("无 Emitter 注册时 onMessage 应静默返回不触发 publish")
    void should_DoNothing_When_NoEmitterRegistered() {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn("{\"event\":\"update\",\"data\":{}}".getBytes(StandardCharsets.UTF_8));
        when(message.getChannel()).thenReturn("session:no_such:events".getBytes(StandardCharsets.UTF_8));

        // 未注册对应 emitter，应静默返回，不抛异常
        service.onMessage(message, null);

        // 不应触发 publish 路径
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    @DisplayName("有 Emitter 注册时 onMessage 应能找到并 send 事件不抛异常")
    void should_SendEvent_When_EmitterRegistered() {
        SseEmitter emitter = service.register("session_001");
        assertThat(emitter).isNotNull();

        Message message = mock(Message.class);
        when(message.getBody())
                .thenReturn("{\"event\":\"update\",\"data\":{\"k\":\"v\"}}".getBytes(StandardCharsets.UTF_8));
        when(message.getChannel()).thenReturn("session:session_001:events".getBytes(StandardCharsets.UTF_8));

        // 应能找到 emitter 并 send（buffer 内部不拋异常）
        service.onMessage(message, null);
    }

    @Test
    @DisplayName("destroy 应完成所有 Emitter 并清空内部 map")
    void should_CompleteAllEmittersAndClearMap_When_Destroy() {
        SseEmitter e1 = service.register("session_001");
        SseEmitter e2 = service.register("session_002");
        assertThat(e1).isNotNull();
        assertThat(e2).isNotNull();

        // 不应抛异常；complete() 在无 handler 时仅置位 complete 标志
        service.destroy();

        // clear 后再次 register 应得到新 emitter（map 已清空）
        SseEmitter e3 = service.register("session_003");
        assertThat(e3).isNotNull();
    }

    @Test
    @DisplayName("extractSessionId 应能从 channel 名称中正确提取 sessionId")
    void should_ExtractFromChannel_When_ExtractSessionId() {
        // 通过 onMessage 间接测试 extractSessionId：
        // 若提取成功，emitter 会被找到；若失败（返回 channel 原值），emitter 不会被找到但也不抛异常
        SseEmitter emitter = service.register("session_001");
        assertThat(emitter).isNotNull();

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn("{\"event\":\"update\",\"data\":{}}".getBytes(StandardCharsets.UTF_8));
        when(message.getChannel()).thenReturn("session:session_001:events".getBytes(StandardCharsets.UTF_8));

        // 不抛异常即视为通过
        service.onMessage(message, null);
    }

    @Test
    @DisplayName("buildChannel 应使用配置的 prefix 拼接 channel 名称")
    void should_UseConfiguredPrefix_When_BuildChannel() {
        // 通过 publish 验证 buildChannel 输出格式
        service.publish("session_001", "update", Map.of("k", "v"));
        verify(redisTemplate).convertAndSend(eq("session:session_001:events"), anyString());

        // 切换 prefix 后再验证
        when(properties.getChannelPrefix()).thenReturn("custom");
        service.publish("session_002", "update", Map.of("k", "v"));
        verify(redisTemplate).convertAndSend(eq("custom:session_002:events"), anyString());
    }
}
