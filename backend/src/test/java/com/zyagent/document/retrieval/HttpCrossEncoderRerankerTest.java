package com.zyagent.document.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.document.DocumentSearchHit;
import com.zyagent.document.KnowledgeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCrossEncoderRerankerTest {
    @Test
    void degradesToRrfOrderWhenEndpointUnreachable() {
        Reranker reranker = new HttpCrossEncoderReranker(
            "http://127.0.0.1:1/rerank", 300L, 10, 200, new ObjectMapper());
        List<RetrievalCandidate> candidates = RrfFusion.merge(List.of(hit("doc", 0)), List.of(), 60, 10);

        RerankOutcome outcome = reranker.rerank("query", candidates, 8);

        assertEquals(RrfFallbackReranker.MODE, outcome.mode(), "falls back to rrf order, not claimed as cross-encoder");
        assertEquals(RetrievalTrace.FAILED, outcome.status(), "attempt recorded as failed");
        assertEquals(1, outcome.candidates().size(), "rrf order preserved");
        assertTrue(outcome.note().contains("rerank 调用失败"), "diagnostic note recorded");
    }

    private DocumentSearchHit hit(String documentId, int index) {
        return new DocumentSearchHit(documentId, documentId + ".md", KnowledgeType.STUDY, index,
            "content", 0.5, documentId + "-" + index);
    }
}
