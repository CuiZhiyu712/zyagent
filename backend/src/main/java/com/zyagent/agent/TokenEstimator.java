package com.zyagent.agent;

public final class TokenEstimator {
    private TokenEstimator() {
    }

    public static TokenUsage estimate(String prompt, String completion) {
        int promptTokens = estimateText(prompt);
        int completionTokens = estimateText(completion);
        return new TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens, true);
    }

    public static int estimateText(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int tokens = 0;
        int asciiRun = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                tokens += asciiTokens(asciiRun);
                asciiRun = 0;
            } else if (ch <= 127) {
                asciiRun++;
            } else {
                tokens += asciiTokens(asciiRun);
                asciiRun = 0;
                tokens += 1;
            }
        }
        tokens += asciiTokens(asciiRun);
        return Math.max(tokens, 1);
    }

    private static int asciiTokens(int chars) {
        if (chars <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(chars / 4.0));
    }
}
