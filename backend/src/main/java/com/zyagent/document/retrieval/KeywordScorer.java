package com.zyagent.document.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 关键词召回的轻量分词与相关性评分。
 *
 * <p>不依赖 MySQL FULLTEXT（对中文支持未经验证）：把查询切成 ASCII 技术标识符与中文 bigram，
 * 再用「命中覆盖度」给候选打分。覆盖率对输入参数做 LIKE 转义，避免 {@code %} / {@code _} 注入通配。
 */
public final class KeywordScorer {
    private static final int MAX_TOKENS = 12;
    private static final int MIN_ASCII_LENGTH = 2;
    private static final Pattern ASCII_TOKEN = Pattern.compile("[a-z0-9][a-z0-9+#._-]*");
    private static final Pattern CJK_RUN = Pattern.compile("[\\u4e00-\\u9fff]+");
    private static final Set<String> STOPWORDS = Set.of("the", "and", "for", "with", "how", "what", "的", "了", "是");

    private KeywordScorer() {
    }

    public static List<String> tokenize(String query) {
        if (query == null) {
            return List.of();
        }
        String normalized = query.toLowerCase(Locale.ROOT).strip();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        Matcher ascii = ASCII_TOKEN.matcher(normalized);
        while (ascii.find()) {
            String token = ascii.group();
            if (token.length() >= MIN_ASCII_LENGTH && !STOPWORDS.contains(token)) {
                tokens.add(token);
            }
        }
        Matcher cjk = CJK_RUN.matcher(normalized);
        while (cjk.find()) {
            String run = cjk.group();
            if (run.length() == 1) {
                tokens.add(run);
                continue;
            }
            for (int i = 0; i + 2 <= run.length(); i++) {
                tokens.add(run.substring(i, i + 2));
            }
        }
        return tokens.stream().distinct().limit(MAX_TOKENS).toList();
    }

    /** 命中的 token 数 / 总 token 数，取值 [0,1]。 */
    public static double coverage(List<String> tokens, String content) {
        if (tokens == null || tokens.isEmpty() || content == null || content.isBlank()) {
            return 0.0;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int hits = 0;
        for (String token : tokens) {
            if (lower.contains(token)) {
                hits++;
            }
        }
        return (double) hits / tokens.size();
    }

    /** 转义 LIKE 通配符，使 token 按字面匹配。 */
    public static String escapeLike(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
