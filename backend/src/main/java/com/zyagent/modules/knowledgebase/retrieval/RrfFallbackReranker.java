package com.zyagent.modules.knowledgebase.retrieval;

import java.util.List;

/**
 * 未启用外部 reranker 时的本地兜底：直接沿用 RRF 融合顺序，并明确标注 {@code rrf_fallback}。
 *
 * <p>它**不是** cross-encoder，不能把这一模式描述为重排已成功运行。
 */
public class RrfFallbackReranker implements Reranker {
    public static final String MODE = "rrf_fallback";

    @Override
    public RerankOutcome rerank(String query, List<RetrievalCandidate> candidates, int limit) {
        List<RetrievalCandidate> limited = candidates.stream().limit(Math.max(0, limit)).toList();
        return new RerankOutcome(MODE, RetrievalTrace.OK, limited, "未启用外部 reranker，沿用 RRF 顺序");
    }
}
