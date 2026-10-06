package com.zyagent.modules.knowledgebase.retrieval;

import java.util.List;

/**
 * 二阶段重排端口。实现可以是本地 RRF 顺序兜底，也可以是外部 cross-encoder 服务。
 */
public interface Reranker {
    RerankOutcome rerank(String query, List<RetrievalCandidate> candidates, int limit);
}
