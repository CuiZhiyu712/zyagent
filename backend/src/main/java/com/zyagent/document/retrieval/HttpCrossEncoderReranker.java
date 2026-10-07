package com.zyagent.document.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部 cross-encoder 重排 adapter。
 *
 * <p>请求体为 {@code {"query":..., "documents":[{"id":..., "text":...}]}}，期望响应为
 * {@code [{"id":..., "score":...}]} 或 {@code {"results":[...]}}。传输前对候选数量与文本长度做上限裁剪。
 *
 * <p>任何超时/连接/解析异常都**降级**为 RRF 顺序，并返回 {@code mode=rrf_fallback, status=failed}；
 * 异常信息只记录类型，不带响应正文，避免把敏感内容写进日志。
 */
public class HttpCrossEncoderReranker implements Reranker {
    private static final String MODE = "cross_encoder";

    private final String endpoint;
    private final long timeoutMs;
    private final int maxCandidates;
    private final int maxContentChars;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpCrossEncoderReranker(
        String endpoint,
        long timeoutMs,
        int maxCandidates,
        int maxContentChars,
        ObjectMapper objectMapper
    ) {
        this.endpoint = endpoint;
        this.timeoutMs = Math.max(1L, timeoutMs);
        this.maxCandidates = Math.max(1, maxCandidates);
        this.maxContentChars = Math.max(32, maxContentChars);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(this.timeoutMs))
            .build();
    }

    @Override
    public RerankOutcome rerank(String query, List<RetrievalCandidate> candidates, int limit) {
        List<RetrievalCandidate> pool = candidates.stream().limit(maxCandidates).toList();
        List<RetrievalCandidate> fallback = pool.stream().limit(Math.max(0, limit)).toList();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query == null ? "" : query);
            body.put("documents", pool.stream()
                .map(candidate -> Map.<String, Object>of("id", candidate.key(), "text", truncate(candidate.hit().content())))
                .toList());
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return degraded(fallback, "rerank HTTP " + response.statusCode());
            }
            Map<String, Double> scores = parseScores(response.body());
            if (scores.isEmpty()) {
                return degraded(fallback, "rerank 响应无有效分数");
            }
            List<RetrievalCandidate> ranked = pool.stream()
                .sorted(Comparator
                    .comparingDouble((RetrievalCandidate candidate) ->
                        scores.getOrDefault(candidate.key(), Double.NEGATIVE_INFINITY))
                    .reversed()
                    .thenComparing(RetrievalCandidate::key))
                .limit(Math.max(0, limit))
                .toList();
            return new RerankOutcome(MODE, RetrievalTrace.OK, ranked, "");
        } catch (Exception ex) {
            return degraded(fallback, "rerank 调用失败：" + ex.getClass().getSimpleName());
        }
    }

    private RerankOutcome degraded(List<RetrievalCandidate> fallback, String note) {
        return new RerankOutcome(RrfFallbackReranker.MODE, RetrievalTrace.FAILED, fallback, note);
    }

    private Map<String, Double> parseScores(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode array = root.isArray() ? root : root.path("results");
        Map<String, Double> scores = new LinkedHashMap<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                String id = node.path("id").asText(null);
                if (id != null && !id.isBlank() && node.hasNonNull("score")) {
                    scores.put(id, node.path("score").asDouble());
                }
            }
        }
        return scores;
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").strip();
        return normalized.length() > maxContentChars ? normalized.substring(0, maxContentChars) : normalized;
    }
}
