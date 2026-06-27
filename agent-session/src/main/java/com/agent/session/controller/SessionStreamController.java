package com.agent.session.controller;

import com.agent.session.service.SsePushService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 流式端点（agent-session 端）：
 *   GET /api/v1/sessions/{sessionId}/stream
 *
 * 返回 SseEmitter，由 SsePushService 推送事件。
 */
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionStreamController {

    private final SsePushService ssePushService;

    public SessionStreamController(SsePushService ssePushService) {
        this.ssePushService = ssePushService;
    }

    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionId) {
        return ssePushService.register(sessionId);
    }
}
