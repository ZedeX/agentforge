package com.agent.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * SSE 流式端点（agent-gateway 端）：
 *   GET /api/v1/sessions/{sessionId}/stream
 *
 * 实现：以 HTTP 客户端从 session-service（http://localhost:8082）拉取 SSE 流，
 *      逐行透传给客户端 SseEmitter。
 *
 * 说明：保持 gateway 的「接入代理」职责，不在 gateway 端维护 SSE 状态。
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionStreamController {

    @Value("${session-service.base-url:http://localhost:8082}")
    private String sessionServiceBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionId) {
        SseEmitter emitter = new SseEmitter(300_000L);

        String upstreamUrl = sessionServiceBaseUrl + "/api/v1/sessions/" + sessionId + "/stream";

        httpClient.sendAsync(
                HttpRequest.newBuilder(URI.create(upstreamUrl))
                        .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofInputStream()
        ).thenAccept(response -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                String[] currentEvent = {"message"};
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        currentEvent[0] = line.substring("event:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        String data = line.substring("data:".length()).trim();
                        emitter.send(SseEmitter.event().name(currentEvent[0]).data(data));
                    } else if (line.isEmpty()) {
                        currentEvent[0] = "message";
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).exceptionally(ex -> {
            emitter.completeWithError(ex);
            return null;
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> emitter.complete());
        return emitter;
    }
}
