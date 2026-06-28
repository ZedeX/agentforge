package com.agent.common.utils;

import com.agent.common.context.TraceContext;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilsTest {

    @AfterEach
    void clearThreadLocal() {
        TraceUtils.clear();
    }

    // ----- JsonUtils -----

    @Test
    @DisplayName("JsonUtils 应能将对象与 JSON 字符串相互转换")
    void should_RoundTripObject_When_JsonUtilsToJsonAndFromJsonInvoked() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "订单查询");
        data.put("count", 3);
        data.put("active", true);

        String json = JsonUtils.toJson(data);
        assertThat(json).contains("\"name\":\"订单查询\"");
        assertThat(json).contains("\"count\":3");
        assertThat(json).contains("\"active\":true");

        Map<String, Object> parsed = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
        assertThat(parsed.get("name")).isEqualTo("订单查询");
        assertThat(((Number) parsed.get("count")).intValue()).isEqualTo(3);
        assertThat(parsed.get("active")).isEqualTo(true);
    }

    @Test
    @DisplayName("JsonUtils.toMap 应将 JSON 字符串转换为 Map")
    void should_ConvertJsonString_When_JsonUtilsToMapInvoked() {
        String json = "{\"taskId\":\"tk_yyy\",\"status\":\"PENDING\"}";
        Map<String, Object> map = JsonUtils.toMap(json);
        assertThat(map.get("taskId")).isEqualTo("tk_yyy");
        assertThat(map.get("status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("JsonUtils 对 null 入参应返回 null")
    void should_ReturnNull_When_InputIsNull() {
        assertThat(JsonUtils.toJson(null)).isNull();
        assertThat(JsonUtils.fromJson(null, new TypeReference<Map<String, Object>>() {})).isNull();
    }

    // ----- TraceUtils -----

    @Test
    @DisplayName("TraceUtils.generateTraceId 应返回 32 位十六进制字符串")
    void should_Return32CharHex_When_GenerateTraceIdInvoked() {
        String traceId = TraceUtils.generateTraceId();
        assertThat(traceId).isNotNull();
        assertThat(traceId.length()).isEqualTo(32);
        assertThat(traceId).matches("[0-9a-f]{32}");
    }

    @Test
    @DisplayName("TraceUtils.generateTraceId 多次调用应返回不同结果")
    void should_ReturnDifferentValues_When_GenerateTraceIdCalledTwice() {
        String a = TraceUtils.generateTraceId();
        String b = TraceUtils.generateTraceId();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("TraceUtils 的 set 与 currentTrace 应能往返还原")
    void should_RoundTripThreadLocal_When_SetAndCurrentTraceInvoked() {
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
        assertThat(got).isSameAs(ctx);
        assertThat(got.getTenantId()).isEqualTo(1001L);
        assertThat(got.getTraceId()).isEqualTo("trace-abc");
    }

    @Test
    @DisplayName("currentTrace 在未设置时应返回 null")
    void should_ReturnNull_When_CurrentTraceNotSet() {
        assertThat(TraceUtils.currentTrace()).isNull();
    }

    @Test
    @DisplayName("TraceUtils.clear 应移除 ThreadLocal 中的 TraceContext")
    void should_RemoveThreadLocal_When_ClearInvoked() {
        TraceContext ctx = TraceContext.builder().traceId("x").build();
        TraceUtils.setTrace(ctx);
        TraceUtils.clear();
        assertThat(TraceUtils.currentTrace()).isNull();
    }

    // ----- TokenEstimator -----

    @Test
    @DisplayName("空字符串或 null 输入应返回 0 token")
    void should_ReturnZero_When_InputIsEmpty() {
        assertThat(TokenEstimator.estimateTokens("")).isEqualTo(0);
        assertThat(TokenEstimator.estimateTokens(null)).isEqualTo(0);
    }

    @Test
    @DisplayName("纯英文应按 4 字符/token 估算")
    void should_Use4CharPerTokenHeuristic_When_InputIsPureEnglish() {
        // 8 个英文字符 -> 2 token（4 字符/token）
        String text = "abcdefgh";
        assertThat(TokenEstimator.estimateTokens(text)).isEqualTo(2);
    }

    @Test
    @DisplayName("纯中文应按 1.7 倍系数估算")
    void should_Apply1point7Coefficient_When_InputIsPureChinese() {
        // 10 个中文字符 -> 10 * 1.7 = 17.0 -> 17 token
        String text = "智能助手查询用户订单";
        assertThat(text.length()).isEqualTo(10);
        assertThat(TokenEstimator.estimateTokens(text)).isEqualTo(17);
    }

    @Test
    @DisplayName("中英混合应将中文与英文 token 数相加")
    void should_SumChineseAndEnglish_When_InputIsMixedContent() {
        // 3 中文 + 4 英文 = 3*1.7 + 4/4 = 5.1 + 1 = 6.1 -> 6 token
        String text = "查订单abcd";
        assertThat(TokenEstimator.estimateTokens(text)).isEqualTo(6);
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
    @DisplayName("扩展 A 区中文字符应同样适用 1.7 倍系数")
    void should_ApplyCoefficient_When_InputIsExtensionAChinese() {
        // 扩展 A 区 4 字符：㐀(U+3400) 㐁(U+3401) 㐂(U+3402) 㐃(U+3403)
        String text = "㐀㐁㐂㐃";
        assertThat(text.length()).isEqualTo(4);
        // 4 * 1.7 = 6.8 -> 6 token
        assertThat(TokenEstimator.estimateTokens(text)).isEqualTo(6);
    }

    /**
     * P3-6 整改：补 TokenEstimator 边界字符分支覆盖率。
     *
     * <p>验证 {@link TokenEstimator#isChinese(char)} 在边界值的行为：
     * U+4E00（基本区首字符「一」）、U+9FFF（基本区末字符）、U+3400（扩展 A 区首字符）、U+4DBF（扩展 A 区末字符）
     * 均应被识别为中文。U+4DC0（紧邻扩展 A 区末）应被识别为非中文。</p>
     */
    @Test
    @DisplayName("边界字符应被正确识别中文与非中文")
    void should_RecognizeBoundaryCharsCorrectly_When_InputIsBoundaryChar() {
        // 基本区首字符「一」(U+4E00)
        assertThat(TokenEstimator.estimateTokens("一")).isEqualTo(1);
        // 基本区末字符 (U+9FFF)
        // 由于 1.7 向下取整，单字符都是 1 token
        assertThat(TokenEstimator.estimateTokens("\u9FFF")).isEqualTo(1);
        // 扩展 A 区首字符 㐀 (U+3400)
        assertThat(TokenEstimator.estimateTokens("\u3400")).isEqualTo(1);
        // 扩展 A 区末字符 (U+4DBF)
        assertThat(TokenEstimator.estimateTokens("\u4DBF")).isEqualTo(1);
        // 紧邻扩展 A 区末的 U+4DC0 不在中文范围内，按 4 字符/token 算
        // 单字符：1/4=0.25 向下取整 = 0 token
        assertThat(TokenEstimator.estimateTokens("\u4DC0")).isEqualTo(0);
        // 4 个 U+4DC0 字符：4/4=1.0 -> 1 token，证明按英文规则处理
        assertThat(TokenEstimator.estimateTokens("\u4DC0\u4DC0\u4DC0\u4DC0")).isEqualTo(1);
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
    @DisplayName("非法 JSON 字符串解析时应抛 RuntimeException")
    void should_ThrowRuntimeException_When_FromJsonReceivesInvalidJson() {
        String invalidJson = "{ this is not valid json";
        assertThatThrownBy(() -> JsonUtils.fromJson(invalidJson, String.class))
                .isInstanceOf(RuntimeException.class);
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
    @DisplayName("toMap 接收数组 JSON 时应抛 RuntimeException")
    void should_ThrowRuntimeException_When_ToMapReceivesArrayJson() {
        String jsonArray = "[1, 2, 3]";
        assertThatThrownBy(() -> JsonUtils.toMap(jsonArray))
                .isInstanceOf(RuntimeException.class);
    }
}
