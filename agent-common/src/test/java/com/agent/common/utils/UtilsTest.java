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
     * P3-6 整改：补 TokenEstimator 扩展 A 区中文字符分支覆盖率。
     *
     * <p>{@link TokenEstimator#isChinese(char)} 检测两个区域：
     * <ul>
     *   <li>基本区：U+4E00 ~ U+9FFF（已有纯中文测试覆盖）</li>
     *   <li>扩展 A 区：U+3400 ~ U+4DBF（未被原测试覆盖，导致 branch_missed=4）</li>
     * </ul>
     * 本测试使用扩展 A 区字符 㐀㐁㐂㐃（U+3400~U+3403）验证 1.7 倍系数同样适用于扩展 A 区。</p>
     */
    @Test
    void tokenEstimator_extensionAChinese_appliesCoefficient() {
        // 扩展 A 区 4 字符：㐀(U+3400) 㐁(U+3401) 㐂(U+3402) 㐃(U+3403)
        String text = "㐀㐁㐂㐃";
        assertEquals(4, text.length());
        // 4 * 1.7 = 6.8 -> 6 token
        assertEquals(6, TokenEstimator.estimateTokens(text));
    }

    /**
     * P3-6 整改：补 TokenEstimator 边界字符分支覆盖率。
     *
     * <p>验证 {@link TokenEstimator#isChinese(char)} 在边界值的行为：
     * U+4E00（基本区首字符「一」）、U+9FFF（基本区末字符）、U+3400（扩展 A 区首字符）、U+4DBF（扩展 A 区末字符）
     * 均应被识别为中文。U+4DC0（紧邻扩展 A 区末）应被识别为非中文。</p>
     */
    @Test
    void tokenEstimator_boundaryChars_recognizedCorrectly() {
        // 基本区首字符「一」(U+4E00)
        assertEquals(1, TokenEstimator.estimateTokens("一"));
        // 基本区末字符 (U+9FFF)
        // 由于 1.7 向下取整，单字符都是 1 token
        assertEquals(1, TokenEstimator.estimateTokens("\u9FFF"));
        // 扩展 A 区首字符 㐀 (U+3400)
        assertEquals(1, TokenEstimator.estimateTokens("\u3400"));
        // 扩展 A 区末字符 (U+4DBF)
        assertEquals(1, TokenEstimator.estimateTokens("\u4DBF"));
        // 紧邻扩展 A 区末的 U+4DC0 不在中文范围内，按 4 字符/token 算
        // 单字符：1/4=0.25 向下取整 = 0 token
        assertEquals(0, TokenEstimator.estimateTokens("\u4DC0"));
        // 4 个 U+4DC0 字符：4/4=1.0 -> 1 token，证明按英文规则处理
        assertEquals(1, TokenEstimator.estimateTokens("\u4DC0\u4DC0\u4DC0\u4DC0"));
    }

    /**
     * FN-009 整改：补 assertThrows 异常断言。
     *
     * <p>{@link JsonUtils#fromJson(String, Class)} 在解析非法 JSON 时会抛 RuntimeException
     * （包装 Jackson 的 JsonProcessingException）。原测试只覆盖正常路径，
     * 未断言异常分支，审计发现 FN-009 缺 assertThrows 用例，本测试填补。</p>
     *
     * <p><b>已修复 FN-022</b>：{@code JsonUtils.fromJson} 的 {@code catch (Exception | Error)}
     * 已能捕获 Jackson 内部抛出的 {@link Error} 子类（如 {@link NoSuchMethodError}）并包装为
     * {@link RuntimeException}，因此本测试断言类型已从 {@code Throwable.class} 收紧为
     * {@code RuntimeException.class}。</p>
     */
    @Test
    void jsonUtils_fromInvalidJson_throws() {
        String invalidJson = "{ this is not valid json";
        assertThrows(RuntimeException.class,
                () -> JsonUtils.fromJson(invalidJson, String.class));
    }

    /**
     * FN-009 整改：补 assertThrows 异常断言。
     *
     * <p>{@link JsonUtils#toMap(String)} 在解析类型不匹配的 JSON 时会抛 RuntimeException
     * （如解析一个 JSON 数组到 Map）。</p>
     *
     * <p><b>已修复 FN-022</b>：{@code catch (Exception | Error)} 已能捕获 Error 子类，
     * 断言类型收紧为 {@code RuntimeException.class}。</p>
     */
    @Test
    void jsonUtils_toMapWithArrayJson_throws() {
        String jsonArray = "[1, 2, 3]";
        assertThrows(RuntimeException.class,
                () -> JsonUtils.toMap(jsonArray));
    }
}
