package com.agent.session.controller;

import com.agent.session.model.Message;
import com.agent.session.model.Session;
import com.agent.session.model.SessionStatus;
import com.agent.session.service.SessionService;
import com.agent.session.service.ShortTermMemoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final ShortTermMemoryService memoryService;

    public SessionController(SessionService sessionService, ShortTermMemoryService memoryService) {
        this.sessionService = sessionService;
        this.memoryService = memoryService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSession(@Valid @RequestBody CreateSessionRequest req,
                                                            HttpServletRequest http) {
        Long tenantId = parseTenantId(http.getHeader("X-Tenant-Id"));
        String userId = firstNonBlank(http.getHeader("X-User-Id"), "anonymous");

        Session s = sessionService.createSession(tenantId, userId, req.getAgentId(), req.getTitle());
        return ResponseEntity.ok(Map.of(
                "code", "OK",
                "message", "success",
                "data", Map.of(
                        "sessionId", s.getSessionId(),
                        "agentId", s.getAgentId(),
                        "status", SessionStatus.fromCode(s.getStatus()).getApiValue(),
                        "createdAt", s.getCreatedAt() == null ? Instant.now().toString() : s.getCreatedAt().toString()
                ),
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String sessionId) {
        Optional<Session> opt = sessionService.getSession(sessionId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "SESSION_NOT_FOUND", "会话不存在", Map.of("sessionId", sessionId)));
        }
        Session s = opt.get();
        return ResponseEntity.ok(Map.of(
                "code", "OK",
                "message", "success",
                "data", Map.of(
                        "sessionId", s.getSessionId(),
                        "tenantId", s.getTenantId(),
                        "userId", s.getUserId(),
                        "agentId", s.getAgentId(),
                        "title", s.getTitle() == null ? "" : s.getTitle(),
                        "status", SessionStatus.fromCode(s.getStatus()).getApiValue(),
                        "tokenUsed", s.getTokenUsed(),
                        "createdAt", s.getCreatedAt() == null ? Instant.now().toString() : s.getCreatedAt().toString(),
                        "updatedAt", s.getUpdatedAt() == null ? Instant.now().toString() : s.getUpdatedAt().toString()
                ),
                "timestamp", Instant.now().toString()
        ));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> closeSession(@PathVariable String sessionId) {
        boolean ok = sessionService.closeSession(sessionId);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "SESSION_NOT_FOUND", "会话不存在", Map.of("sessionId", sessionId)));
        }
        return ResponseEntity.ok(Map.of(
                "code", "OK",
                "message", "success",
                "data", Map.of(
                        "sessionId", sessionId,
                        "status", SessionStatus.CLOSED.getApiValue()
                ),
                "timestamp", Instant.now().toString()
        ));
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(@PathVariable String sessionId,
                                                          @Valid @RequestBody SendMessageRequest req,
                                                          HttpServletRequest http) {
        String userId = firstNonBlank(http.getHeader("X-User-Id"), "anonymous");
        try {
            Message reply = sessionService.sendMessage(sessionId, req.getContent(), req.getContentType(), userId);
            return ResponseEntity.accepted().body(Map.of(
                    "code", "OK",
                    "message", "success",
                    "data", Map.of(
                            "messageId", reply.getMsgId(),
                            "role", reply.getRole().name().toLowerCase(),
                            "content", reply.getContent(),
                            "contentType", reply.getContentType(),
                            "tokenUsed", reply.getTokenCount(),
                            "sessionId", reply.getSessionId()
                    ),
                    "timestamp", Instant.now().toString()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(
                    "SESSION_NOT_FOUND", e.getMessage(), Map.of("sessionId", sessionId)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(
                    "SESSION_STATUS_CONFLICT", e.getMessage(), Map.of("sessionId", sessionId)));
        }
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(@PathVariable String sessionId,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size,
                                                            @RequestParam(defaultValue = "createdAt,asc") String sort) {
        String[] parts = sort.split(",");
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        PageRequest pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by(dir, parts[0]));

        Page<Message> p = sessionService.listMessages(sessionId, pageable);
        List<Map<String, Object>> items = p.getContent().stream()
                .map(m -> Map.<String, Object>of(
                        "msgId", m.getMsgId(),
                        "role", m.getRole().name().toLowerCase(),
                        "content", m.getContent(),
                        "contentType", m.getContentType(),
                        "tokenCount", m.getTokenCount(),
                        "createdAt", m.getCreatedAt() == null ? "" : m.getCreatedAt().toString()
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "code", "OK",
                "message", "success",
                "data", Map.of(
                        "items", items,
                        "page", page,
                        "size", size,
                        "total", p.getTotalElements()
                ),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(errorBody(
                "INVALID_ARGUMENT",
                e.getBindingResult().getAllErrors().get(0).getDefaultMessage(),
                Map.of()));
    }

    private Long parseTenantId(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String firstNonBlank(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private Map<String, Object> errorBody(String code, String message, Map<String, Object> details) {
        return Map.of(
                "code", code,
                "message", message,
                "details", details,
                "timestamp", Instant.now().toString()
        );
    }

    public static class CreateSessionRequest {
        @jakarta.validation.constraints.NotNull
        private Long agentId;
        private String title;
        private Map<String, Object> meta;

        public Long getAgentId() {
            return agentId;
        }

        public void setAgentId(Long agentId) {
            this.agentId = agentId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Map<String, Object> getMeta() {
            return meta;
        }

        public void setMeta(Map<String, Object> meta) {
            this.meta = meta;
        }
    }

    public static class SendMessageRequest {
        @NotBlank
        private String content;
        private String contentType;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }
    }
}
