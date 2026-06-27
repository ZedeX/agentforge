package com.agent.common.utils;

import com.agent.common.context.TraceContext;

import java.security.SecureRandom;

/**
 * 链路工具，提供 TraceID 生成与 ThreadLocal 传递。
 */
public final class TraceUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int TRACE_ID_LENGTH = 32;

    private static final ThreadLocal<TraceContext> TRACE_HOLDER = new ThreadLocal<>();

    private TraceUtils() {
    }

    /**
     * 生成 32 字符十六进制 traceId（与 SkyWalking/常见 TraceID 兼容）。
     */
    public static String generateTraceId() {
        byte[] bytes = new byte[TRACE_ID_LENGTH / 2];
        RANDOM.nextBytes(bytes);
        char[] out = new char[TRACE_ID_LENGTH];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    public static void setTrace(TraceContext ctx) {
        TRACE_HOLDER.set(ctx);
    }

    public static TraceContext currentTrace() {
        return TRACE_HOLDER.get();
    }

    public static void clear() {
        TRACE_HOLDER.remove();
    }
}
