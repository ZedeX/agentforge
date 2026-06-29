package com.agent.gateway.e2e;

import com.agent.common.exception.ErrorCode;
import com.agent.gateway.fixture.ErrorCodeE2EFixtureController;
import com.agent.gateway.handler.GlobalExceptionHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * P7-5 (COV-04)：错误码 HTTP 端到端触发路径测试。
 *
 * <p>本测试配合 P6-7 的 {@code ErrorCodePathTest}（单元层）补足「真实 HTTP 链路」覆盖：
 * <ol>
 *   <li>请求进入 {@link com.agent.gateway.fixture.ErrorCodeE2EFixtureController} 触发 {@link
 *       com.agent.common.exception.BusinessException}</li>
 *   <li>{@link GlobalExceptionHandler} 捕获异常，按 {@link ErrorCode#getHttpStatus()} 返回对应
 *       HTTP 状态码 + JSON body {@code {"code":"...","message":"..."}}</li>
 *   <li>用 MockMvc 拿到 MvcResult 后，全部断言改用 AssertJ 验证状态码与 body 字段（满足 P6-4 规范）</li>
 * </ol>
 *
 * <p>覆盖目标：每个 HTTP 状态码维度（401/403/404/400/413/409/429/500/503/504）至少 1 个错误码，
 * 加上 details 字段序列化路径与未知异常兜底路径，共 12 个用例。</p>
 *
 * <p>断言风格：AssertJ 链式 + Map 字段断言，无 JUnit assertEquals/assertTrue，符合 P6-4 规范。</p>
 */
class ErrorCodeE2ETest {

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 仅装配 fixture controller + 真实 GlobalExceptionHandler，不启动 Spring 上下文
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorCodeE2EFixtureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** 解析 MvcResult body 为 Map，统一用 AssertJ 断言。
     *  显式指定 UTF-8 避免默认 ISO-8859-1 导致中文乱码（MockMvc getContentAsString 默认 charset 问题）。 */
    private Map<String, Object> bodyAsMap(MvcResult result) throws Exception {
        return om.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                new TypeReference<>() {});
    }

    // ============ 401 未认证 ============

    @Test
    @DisplayName("HTTP E2E: UNAUTHENTICATED 应返回 401 + code/message JSON body")
    void should_Return401WithCode_When_Unauthenticated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/UNAUTHENTICATED")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        Map<String, Object> body = bodyAsMap(result);
        assertThat(body).containsEntry("code", ErrorCode.UNAUTHENTICATED.getCode());
        assertThat(body).containsEntry("message", "e2e:UNAUTHENTICATED");
    }

    // ============ 403 无权限 ============

    @Test
    @DisplayName("HTTP E2E: FORBIDDEN 应返回 403")
    void should_Return403WithCode_When_Forbidden() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/FORBIDDEN")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(bodyAsMap(result)).containsEntry("code", ErrorCode.FORBIDDEN.getCode());
    }

    // ============ 404 资源不存在 ============

    @Test
    @DisplayName("HTTP E2E: TASK_NOT_FOUND 应返回 404")
    void should_Return404WithCode_When_TaskNotFound() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/TASK_NOT_FOUND")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(bodyAsMap(result)).containsEntry("code", ErrorCode.TASK_NOT_FOUND.getCode());
    }

    // ============ 400 参数校验 ============

    @Test
    @DisplayName("HTTP E2E: PARAM_INVALID 应返回 400")
    void should_Return400WithCode_When_ParamInvalid() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/PARAM_INVALID")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(bodyAsMap(result)).containsEntry("code", ErrorCode.PARAM_INVALID.getCode());
    }

    // ============ 413 请求体过大 ============

    @Test
    @DisplayName("HTTP E2E: PAYLOAD_TOO_LARGE 应返回 413")
    void should_Return413WithCode_When_PayloadTooLarge() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/PAYLOAD_TOO_LARGE")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(413);
        assertThat(bodyAsMap(result)).containsEntry("code", ErrorCode.PAYLOAD_TOO_LARGE.getCode());
    }

    // ============ 409 状态冲突 ============

    @Test
    @DisplayName("HTTP E2E: TASK_STATUS_CONFLICT 应返回 409")
    void should_Return409WithCode_When_TaskStatusConflict() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/TASK_STATUS_CONFLICT")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(bodyAsMap(result)).containsEntry("code", ErrorCode.TASK_STATUS_CONFLICT.getCode());
    }

    // ============ 429 限流 ============

    @Test
    @DisplayName("HTTP E2E: RATE_LIMITED 应返回 429")
    void should_Return429WithCode_When_RateLimited() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/RATE_LIMITED")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(429);
        assertThat(bodyAsMap(result)).containsEntry("code", ErrorCode.RATE_LIMITED.getCode());
    }

    // ============ 500 内部错误（default 分支） ============

    @Test
    @DisplayName("HTTP E2E: INTERNAL 应返回 500")
    void should_Return500WithCode_When_Internal() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/INTERNAL")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(bodyAsMap(result)).containsEntry("code", ErrorCode.INTERNAL.getCode());
    }

    // ============ 503 服务不可用 ============

    @Test
    @DisplayName("HTTP E2E: DEPENDENCY_DOWN 应返回 503")
    void should_Return503WithCode_When_DependencyDown() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/DEPENDENCY_DOWN")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(bodyAsMap(result)).containsEntry("code", ErrorCode.DEPENDENCY_DOWN.getCode());
    }

    // ============ 504 超时 ============

    @Test
    @DisplayName("HTTP E2E: TIMEOUT 应返回 504")
    void should_Return504WithCode_When_Timeout() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/TIMEOUT")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(504);
        assertThat(bodyAsMap(result)).containsEntry("code", ErrorCode.TIMEOUT.getCode());
    }

    // ============ details 字段序列化路径 ============

    @Test
    @DisplayName("HTTP E2E: BusinessException 携带 details 时 JSON body 应包含 details 字段")
    void should_IncludeDetails_When_BusinessExceptionHasDetails() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/VALIDATION_FAILED/withDetails")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        Map<String, Object> body = bodyAsMap(result);
        assertThat(body).containsEntry("code", ErrorCode.VALIDATION_FAILED.getCode());
        assertThat(body).containsKey("details");
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.get("details");
        assertThat(details).containsEntry("field", "goal").containsEntry("reason", "test");
    }

    // ============ 兜底分支：未知异常 → 500 INTERNAL ============

    @Test
    @DisplayName("HTTP E2E: 非 BusinessException 应由兜底分支返回 500 INTERNAL")
    void should_Return500Internal_When_UnknownExceptionThrown() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/e2e/errors/_unknown")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        Map<String, Object> body = bodyAsMap(result);
        assertThat(body).containsEntry("code", ErrorCode.INTERNAL.getCode());
        assertThat(body).containsEntry("message", "内部错误");
    }
}
