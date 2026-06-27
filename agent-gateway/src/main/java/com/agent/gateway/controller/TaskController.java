package com.agent.gateway.controller;

import com.agent.gateway.dto.TaskCreateRequest;
import com.agent.gateway.dto.TaskCreateResponse;
import com.agent.gateway.service.TaskRouterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskRouterService routerService;

    public TaskController(TaskRouterService routerService) {
        this.routerService = routerService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createTask(@Valid @RequestBody TaskCreateRequest request,
                                                           HttpServletRequest httpRequest) {
        String tenantId = (String) httpRequest.getAttribute("X-Tenant-Id");
        String userId = (String) httpRequest.getAttribute("X-User-Id");

        try {
            TaskCreateResponse response = routerService.route(request, tenantId, userId);
            return ResponseEntity.accepted().body(Map.of(
                    "code", "OK",
                    "message", "success",
                    "data", Map.of(
                            "taskId", response.taskId(),
                            "status", response.status()
                    ),
                    "timestamp", Instant.now().toString()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "INVALID_ARGUMENT",
                    "message", e.getMessage(),
                    "timestamp", Instant.now().toString()
            ));
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", "INVALID_ARGUMENT",
                "message", e.getBindingResult().getAllErrors().get(0).getDefaultMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
