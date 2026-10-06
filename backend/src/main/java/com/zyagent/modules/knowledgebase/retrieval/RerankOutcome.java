package com.zyagent.modules.knowledgebase.retrieval;

import java.util.List;

/**
 * 一次重排的结果。
 *
 * <p>{@code mode} 表示**实际生效**的排序方式（{@code cross_encoder} 或 {@code rrf_fallback}），
 * {@code status} 表示本次尝试的结果。外部 cross-encoder 失败时会返回
 * {@code mode=rrf_fallback, status=failed}，如实反映“降级”而不是伪装成成功。
 */
public record RerankOutcome(
    String mode,
    String status,
    List<RetrievalCandidate> candidates,
    String note
) {
    public RerankOutcome {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        note = note == null ? "" : note;
    }
}
