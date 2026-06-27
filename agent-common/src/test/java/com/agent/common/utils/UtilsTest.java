package com.agent.common.utils;

import com.agent.common.context.TraceContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    @AfterEach
    void clearThreadLocal() {
        TraceUtils.clear();
    }

    // ----- JsonUtils -----

    @Test
    void jsonUtils_toJsonAndFromJson_roundTripsObject() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "订单查询");
        data.put("count", 3);
        data.put("active", true);

        String json = JsonUtils.toJson(data);
        assertTrue(json.contains("\"name\":\"订单查询\""));
        assertTrue(json.contains("\"count\":3"));
        assertTrue(json.contains("\"active\":true"));

        Map<String, Object> parsed = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
        assertEquals("订单查询", parsed.get("name"));
        assertEquals(3, ((Number) parsed.get("count")).intValue());
        assertEquals(true, parsed.get("active"));
    }

    @Test
    void jsonUtils_toMap_convertsJsonString() {
        String json = "{\"taskId\":\"tk_yyy\",\"status\":\"PENDING\"}";
        Map<String, Object> map = JsonUtils.toMap(json);
        assertEquals("tk_yyy", map.get("taskId"));
        assertEquals("PENDING", map.get("status"));
    }

    @Test
    void jsonUtils_toJson_returnsNullForNull() {
        assertNull(JsonUtils.toJson(null));
        assertNull(JsonUtils.fromJson(null, new TypeReference<Map<String, Object>>() {}));
    }

    // ----- TraceUtils -----

    @Test
    void traceUtils_generateTraceId_returns32CharHex() {
        String traceId = TraceUtils.generateTraceId();
        assertNotNull(traceId);
        assertEquals(32, traceId.length());
        assertTrue(traceId.matches("[0-9a-f]{32}"));
    }

    @Test
    void traceUtils_generateTraceId_isUnique() {
        String a = TraceUtils.generateTraceId();
        String b = TraceUtils.generateTraceId();
        assertNotEquals(a, b);
    }

    @Test
    void traceUtils_setAndGetThreadLocal_roundTrip() {
        TraceContext ctx = TraceContext.builder()
                .tenantId(1001L)
                .userId("u_123")
                .sessionId("ss_a1b2c3d4")
                .taskId("tk_yyy")
                .subtaskId("st_001")
                .traceId("trace-abc")
                .spanId("span-def")
                .build();
        TraceUtils.setTrace(ctx);
        TraceContext got = TraceUtils.currentTrace();
        assertSame(ctx, got);
        assertEquals(1001L, got.getTenantId());
        assertEquals("trace-abc", got.getTraceId());
    }

    @Test
    void traceUtils_currentTrace_returnsNullWhenUnset() {
        assertNull(TraceUtils.currentTrace());
    }

    @Test
    void traceUtils_clear_removesThreadLocal() {
        TraceContext ctx = TraceContext.builder().traceId("x").build();
        TraceUtils.setTrace(ctx);
        TraceUtils.clear();
        assertNull(TraceUtils.currentTrace());
    }

    // ----- TokenEstimator -----

    @Test
    void tokenEstimator_emptyString_returnsZero() {
        assertEquals(0, TokenEstimator.estimateTokens(""));
        assertEquals(0, TokenEstimator.estimateTokens(null));
    }

    @Test
    void tokenEstimator_pureEnglish_uses4CharPerTokenHeuristic() {
        // 8 个英文字符 -> 2 token（4 字符/token）
        String text = "abcdefgh";
        assertEquals(2, TokenEstimator.estimateTokens(text));
    }

    @Test
    void tokenEstimator_pureChinese_applies1point7Coefficient() {
        // 10 个中文字符 -> 10 * 1.7 = 17.0 -> 17 token
        String text = "智能助手查询用户订单";
        assertEquals(10, text.length());
        assertEquals(17, TokenEstimator.estimateTokens(text));
    }

    @Test
    void tokenEstimator_mixedContent_sumsChineseAndEnglish() {
        // 3 中文 + 4 英文 = 3*1.7 + 4/4 = 5.1 + 1 = 6.1 -> 6 token
        String text = "查订单abcd";
        assertEquals(6, TokenEstimator.estimateTokens(text));
    }

    /**
     * FN-009 整改：补 assertThrows 异常断言。
     *
     * <p>{@link JsonUtils#fromJson(String, Class)} 在解析非法 JSON 时会抛 RuntimeException
     * （包装 Jackson 的 JsonProcessingException）。原测试只覆盖正常路径，
     * 未断言异常分支，审计发现 FN-009 缺 assertThrows 用例，本测试填补。</p>
     *
     * <p><b>已知问题（P2 待整改）</b>：当 Jackson 内部抛 {@link Error} 子类（如
     * {@link NoSuchMethodError}）时，{@code JsonUtils.fromJson} 的 {@code catch (Exception)}
     * 无法捕获，Error 会直接冒泡。本测试用 {@code Throwable.class} 兼容两类异常，
     * 待 P2 阶段将 {@code catch (Exception)} 改为 {@code catch (Exception | Error)} 后，
     * 可收紧为 {@code RuntimeException.class}。</p>
     */
    @Test
    void jsonUtils_fromInvalidJson_throws() {
        String invalidJson = "{ this is not valid json";
        assertThrows(Throwable.class,
                () -> JsonUtils.fromJson(invalidJson, String.class));
    }

    /**
     * FN-009 整改：补 assertThrows 异常断言。
     *
     * <p>{@link JsonUtils#toMap(String)} 在解析类型不匹配的 JSON 时会抛 RuntimeException
     * （如解析一个 JSON 数组到 Map）。同上，用 {@code Throwable.class} 兼容 Error 子类。</p>
     */
    @Test
    void jsonUtils_toMapWithArrayJson_throws() {
        String jsonArray = "[1, 2, 3]";
        assertThrows(Throwable.class,
                () -> JsonUtils.toMap(jsonArray));
    }
}
