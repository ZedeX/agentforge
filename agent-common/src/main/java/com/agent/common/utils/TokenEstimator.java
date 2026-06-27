package com.agent.common.utils;

/**
 * Token 估算器（参考 doc 09 §3.2.c）。
 *
 * 规则：
 * - 中文字符按 1.7 倍系数估算（即 1 中文字符 ≈ 1.7 token）
 * - 英文/ASCII 字符按 4 字符/token 估算
 * - 累加后向下取整
 */
public final class TokenEstimator {

    private static final double CHINESE_COEFFICIENT = 1.7;
    private static final int ENGLISH_CHARS_PER_TOKEN = 4;

    private TokenEstimator() {
    }

    /**
     * 估算给定文本的 token 数。
     *
     * @param text 待估算文本，null 或空字符串返回 0
     * @return 估算的 token 数
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseCount = 0;
        int otherCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }

        double chineseTokens = chineseCount * CHINESE_COEFFICIENT;
        double englishTokens = (double) otherCount / ENGLISH_CHARS_PER_TOKEN;
        return (int) Math.floor(chineseTokens + englishTokens);
    }

    private static boolean isChinese(char c) {
        // CJK Unified Ideographs 基本区与扩展 A 区
        return (c >= '\u4E00' && c <= '\u9FFF')
                || (c >= '\u3400' && c <= '\u4DBF');
    }
}
