package com.zyagent.modules.knowledgebase.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrievalMetricsTest {
    @Test
    void recallCountsRelevantInsideTopK() {
        assertEquals(1.0, RetrievalMetrics.recallAtK(List.of("a", "b", "c"), Set.of("b"), 5), 1e-9);
        assertEquals(0.0, RetrievalMetrics.recallAtK(List.of("a", "b", "c"), Set.of("z"), 5), 1e-9);
        assertEquals(0.5, RetrievalMetrics.recallAtK(List.of("a", "b", "c"), Set.of("a", "z"), 5), 1e-9);
        assertEquals(0.0, RetrievalMetrics.recallAtK(List.of("a"), Set.of(), 5), 1e-9);
    }

    @Test
    void reciprocalRankUsesFirstRelevantPosition() {
        assertEquals(1.0, RetrievalMetrics.reciprocalRankAtK(List.of("a", "b"), Set.of("a"), 10), 1e-9);
        assertEquals(0.5, RetrievalMetrics.reciprocalRankAtK(List.of("a", "b"), Set.of("b"), 10), 1e-9);
        assertEquals(0.0, RetrievalMetrics.reciprocalRankAtK(List.of("a", "b"), Set.of("z"), 10), 1e-9);
        assertEquals(0.0, RetrievalMetrics.reciprocalRankAtK(List.of("a", "b"), Set.of("b"), 1), 1e-9);
    }

    @Test
    void ndcgPenalizesLowerRelevantPositions() {
        assertEquals(1.0, RetrievalMetrics.ndcgAtK(List.of("a", "b"), Set.of("a"), 10), 1e-9);
        assertEquals(1.0 / (Math.log(3) / Math.log(2)), RetrievalMetrics.ndcgAtK(List.of("a", "b"), Set.of("b"), 10), 1e-9);
        assertEquals(0.0, RetrievalMetrics.ndcgAtK(List.of("a"), Set.of("z"), 10), 1e-9);
    }
}
