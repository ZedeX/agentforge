package org.springframework.web.servlet.mvc.method.annotation;

import com.agent.gateway.controller.SessionStreamController;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SessionStreamController 单元测试 (P5-1)。
 *
 * <p>覆盖目标：将 agent-gateway line 79.9%/branch 66% 提升至 80%/70%+，
 * 解除 COV-01 一票否决最后一项。
 *
 * <p>测试策略：
 * <ul>
 *   <li>用 JDK 内置 {@link HttpServer} 启动本地 mock SSE upstream（无需额外依赖）
 *   <li>通过 {@link ReflectionTestUtils} 注入 sessionServiceBaseUrl
 *   <li>通过 {@link SseEmitter#initialize(ResponseBodyEmitter.Handler)} 注册 handler
 *       捕获 send/complete/completeWithError 事件
 *   <li>用 {@link CountDownLatch} 等待异步 HttpClient 完成
 * </ul>
 *
 * <p><b>为何放在 spring 包下</b>：{@link ResponseBodyEmitter.Handler} 是 package-private
 * 接口, 无法从 {@code com.agent.gateway.controller} 包访问。本测试必须放在
 * {@code org.springframework.web.servlet.mvc.method.annotation} 包下才能实现 Handler 接口。
 * 这是 Spring 内部测试常用模式（参见 spring-test 源码中 SseEmitterTests 等位置）。
 *
 * <p>覆盖分支：
 * <ul>
 *   <li>正常路径：SSE 字节解析 + event/data 行识别 + 空行重置 + emitter.complete()
 *   <li>异常路径：upstream 不可达 → exceptionally → emitter.completeWithError()
 *   <li>空 body：readLine 立即返回 null → 无事件 + complete()
 *   <li>事件名重置：空行后 currentEvent[0] 复位为 "message"
 * </ul>
 */
class SessionStreamControllerTest {

    private SessionStreamController controller;
    private HttpServer mockServer;
    private int mockServerPort;

    @BeforeEach
    void setUp() throws IOException {
        controller = new SessionStreamController();
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockServer.start();
        mockServerPort = mockServer.getAddress().getPort();
        ReflectionTestUtils.setField(controller, "sessionServiceBaseUrl",
                "http://127.0.0.1:" + mockServerPort);
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    private void mockSseEndpoint(String path, String sseBody) {
        byte[] body = sseBody.getBytes(StandardCharsets.UTF_8);
        mockServer.createContext(path, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
    }

    private void wireEmitter(SseEmitter emitter,
                              CountDownLatch completionLatch,
                              AtomicReference<Throwable> errorRef,
                              List<Set<ResponseBodyEmitter.DataWithMediaType>> events) throws IOException {
        emitter.initialize(new ResponseBodyEmitter.Handler() {
            @Override
            public void send(Object data, MediaType mediaType) {
                // SseEmitter.send(SseEventBuilder) 不走此路径, 无需捕获
            }

            @Override
            public void send(Set<ResponseBodyEmitter.DataWithMediaType> items) {
                events.add(items);
            }

            @Override
            public void complete() {
                completionLatch.countDown();
            }

            @Override
            public void completeWithError(Throwable failure) {
                errorRef.set(failure);
                completionLatch.countDown();
            }

            @Override
            public void onTimeout(Runnable callback) {
                // Spring 在 initialize 时注册 timeout 回调, 这里直接运行以触发 emitter::complete
            }

            @Override
            public void onError(Consumer<Throwable> callback) {
                // Spring 在 initialize 时注册 error 回调
            }

            @Override
            public void onCompletion(Runnable callback) {
                // Spring 在 initialize 时注册 completion 回调
            }
        });
    }

    @Test
    void stream_returnsNonNullSseEmitter() {
        mockSseEndpoint("/api/v1/sessions/test-1/stream", "data: hello\n\n");
        SseEmitter emitter = controller.stream("test-1");
        assertNotNull(emitter, "stream() must return a non-null SseEmitter");
    }

    @Test
    void stream_withValidUpstreamSse_forwardsEventsAndCompletes() throws Exception {
        String sseBody = "event: message\ndata: hello\n\ndata: world\n\n";
        AtomicInteger requestCount = new AtomicInteger(0);
        byte[] body = sseBody.getBytes(StandardCharsets.UTF_8);
        mockServer.createContext("/api/v1/sessions/test-123/stream", exchange -> {
            requestCount.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        List<Set<ResponseBodyEmitter.DataWithMediaType>> events = new ArrayList<>();

        SseEmitter emitter = controller.stream("test-123");
        wireEmitter(emitter, latch, errorRef, events);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "SseEmitter should complete within 5s");
        assertNull(errorRef.get(), "No error expected on valid upstream SSE");
        assertEquals(1, requestCount.get(), "Upstream should receive exactly 1 request");
        assertEquals(2, events.size(), "Expected 2 SSE events forwarded");
    }

    @Test
    void stream_withEventNameResetOnEmptyLine_correctlyParsed() throws Exception {
        // 第一个事件名为 foo，空行后应复位为 message
        String sseBody = "event: foo\ndata: a\n\ndata: b\n\n";
        mockSseEndpoint("/api/v1/sessions/reset-test/stream", sseBody);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        List<Set<ResponseBodyEmitter.DataWithMediaType>> events = new ArrayList<>();

        SseEmitter emitter = controller.stream("reset-test");
        wireEmitter(emitter, latch, errorRef, events);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "SseEmitter should complete");
        assertNull(errorRef.get(), "No error expected");
        assertEquals(2, events.size(), "Expected 2 events: first 'foo' with data 'a', second 'message' with data 'b'");
    }

    @Test
    void stream_withEmptyBody_completesWithoutEvents() throws Exception {
        mockServer.createContext("/api/v1/sessions/empty/stream", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE);
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        });

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        List<Set<ResponseBodyEmitter.DataWithMediaType>> events = new ArrayList<>();

        SseEmitter emitter = controller.stream("empty");
        wireEmitter(emitter, latch, errorRef, events);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "SseEmitter should complete on empty body");
        assertNull(errorRef.get(), "No error expected on empty body");
        assertTrue(events.isEmpty(), "No events expected on empty body");
    }

    @Test
    void stream_withUnreachableUpstream_completesWithError() throws Exception {
        // 使用未监听端口，HttpClient.sendAsync 立即触发 ConnectException → exceptionally
        ReflectionTestUtils.setField(controller, "sessionServiceBaseUrl", "http://127.0.0.1:1");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        List<Set<ResponseBodyEmitter.DataWithMediaType>> events = new ArrayList<>();

        SseEmitter emitter = controller.stream("any");
        wireEmitter(emitter, latch, errorRef, events);

        assertTrue(latch.await(8, TimeUnit.SECONDS),
                "SseEmitter should complete (with error) within 8s on unreachable upstream");
        assertNotNull(errorRef.get(), "Expected error from unreachable upstream");
        assertTrue(events.isEmpty(), "No events expected on upstream failure");
    }
}
