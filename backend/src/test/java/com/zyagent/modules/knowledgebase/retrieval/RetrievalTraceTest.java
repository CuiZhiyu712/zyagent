package com.zyagent.modules.knowledgebase.retrieval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalTraceTest {
    @Test
    void buildsCompactSearchModeFromChannelStatuses() {
        RetrievalTrace trace = RetrievalTrace.builder(60, 30)
            .vector(RetrievalTrace.OK, 12L, 20)
            .keyword(RetrievalTrace.FAILED, 4L, 0)
            .fused(18)
            .rerank("rrf_fallback", RetrievalTrace.OK, 1L, 8)
            .build();

        assertEquals("hybrid:ok+failed;rerank=rrf_fallback", trace.searchMode());
        assertEquals(20, trace.vectorCandidates());
        assertEquals(18, trace.fusedCandidates());
    }

    @Test
    void memoryFallbackMarksBothChannelsUnavailable() {
        RetrievalTrace trace = RetrievalTrace.memoryFallback("Milvus 与 MySQL 均不可用");

        assertEquals(RetrievalTrace.UNAVAILABLE, trace.vectorStatus());
        assertEquals(RetrievalTrace.UNAVAILABLE, trace.keywordStatus());
        assertTrue(trace.searchMode().contains("rerank=none"));
        assertEquals("Milvus 与 MySQL 均不可用", trace.note());
    }
}
