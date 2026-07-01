package com.agent.knowledge.util;

/**
 * Heuristic token counter for mixed CJK + Latin text (doc 07-knowledge §4.1, Plan 08 T9).
 *
 * <p>Estimates token count without external tokenizer dependencies:
 * <ul>
 *   <li>CJK characters (Chinese/Japanese/Korean) → 1.5 tokens per character (rounded up)</li>
 *   <li>Latin words (whitespace-separated) → 1 token per word</li>
 * </ul>
 *
 * <p>This is a coarse approximation suitable for chunk sizing. For precise counts
 * (e.g. billing), integrate tiktoken or model-specific tokenizers in T10.</p>
 */
public final class TokenCounter {

    /** CJK characters cost 1.5 tokens each (BPE typically splits Chinese into 1-2 tokens). */
    private static final double CJK_TOKEN_RATIO = 1.5;

    private TokenCounter() {
        // utility class
    }

    /**
     * Count tokens in mixed-language text.
     *
     * @param text input text, null/empty returns 0
     * @return estimated token count (ceiling for CJK portion + word count for Latin)
     */
    public static int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjkChars = 0;
        int nonCjkWords = 0;
        StringBuilder currentWord = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                cjkChars++;
                if (currentWord.length() > 0) {
                    nonCjkWords++;
                    currentWord.setLength(0);
                }
            } else if (Character.isWhitespace(c)) {
                if (currentWord.length() > 0) {
                    nonCjkWords++;
                    currentWord.setLength(0);
                }
            } else {
                currentWord.append(c);
            }
        }
        if (currentWord.length() > 0) {
            nonCjkWords++;
        }
        int cjkTokens = (int) Math.ceil(cjkChars * CJK_TOKEN_RATIO);
        return cjkTokens + nonCjkWords;
    }

    /**
     * Check if a character belongs to CJK scripts.
     *
     * @param c character to check
     * @return true if CJK Unified Ideograph / Extension A / CJK Symbols
     */
    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)   // CJK Unified Ideographs
                || (c >= 0x3400 && c <= 0x4DBF)  // CJK Unified Ideographs Extension A
                || (c >= 0x3000 && c <= 0x303F)  // CJK Symbols and Punctuation
                || (c >= 0xFF00 && c <= 0xFFEF); // Halfwidth and Fullwidth Forms
    }
}
