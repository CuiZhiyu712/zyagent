package com.zyagent.modules.knowledgebase.retrieval;

import com.zyagent.modules.knowledgebase.DocumentSearchHit;
import com.zyagent.modules.knowledgebase.KnowledgeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RrfFusionTest {
    @Test
    void mergesRanksAndDeduplicatesSameChunk() {
        List<RetrievalCandidate> result = RrfFusion.merge(
            List.of(hit("doc", 0, 0.92)), List.of(hit("doc", 0, 3.0), hit("other", 1, 2.0)), 60, 2);

        assertEquals(2, result.size());
        assertEquals("doc:0", result.get(0).key());
        assertEquals(2, result.get(0).channels().size());
    }

    @Test
    void keepsPerChannelRankAndRawScore() {
        List<RetrievalCandidate> result = RrfFusion.merge(
            List.of(hit("doc", 0, 0.92)), List.of(hit("doc", 0, 3.0)), 60, 10);

        RetrievalCandidate candidate = result.get(0);
        assertEquals(1, candidate.channelScores().get("vector").rank(), "vector rank preserved");
        assertEquals(0.92, candidate.channelScores().get("vector").score(), 1e-9, "vector raw score preserved");
        assertEquals(1, candidate.channelScores().get("keyword").rank(), "keyword rank preserved");
        assertEquals(3.0, candidate.channelScores().get("keyword").score(), 1e-9, "keyword raw score preserved");
        assertEquals(2.0 / 61.0, candidate.fusedScore(), 1e-9, "rrf scores accumulated");
    }

    @Test
    void ordersByFusedScoreAndRespectsLimit() {
        // B 同时出现在两路第 2 位，融合分应高于只在单路第 1 位的 A。
        List<RetrievalCandidate> result = RrfFusion.merge(
            List.of(hit("A", 0, 0.9), hit("B", 0, 0.8)),
            List.of(hit("C", 0, 5.0), hit("B", 0, 4.0)),
            60, 10);

        assertEquals("B:0", result.get(0).key(), "chunk hit by both channels ranks first");
        assertEquals(3, result.size(), "three distinct chunks survive fusion");

        List<RetrievalCandidate> limited = RrfFusion.merge(
            List.of(hit("A", 0, 0.9), hit("B", 0, 0.8)),
            List.of(hit("C", 0, 5.0), hit("B", 0, 4.0)),
            60, 2);
        assertEquals(2, limited.size(), "limit applied after sorting");
    }

    @Test
    void missingChannelContributesNothing() {
        List<RetrievalCandidate> result = RrfFusion.merge(
            List.of(), List.of(hit("a", 0, 1.0)), 60, 10);

        assertEquals(1, result.size());
        assertEquals(Set.of("keyword"), result.get(0).channels());
    }

    @Test
    void toHitCarriesFusedScore() {
        RetrievalCandidate candidate = RrfFusion.merge(
            List.of(hit("doc", 0, 0.92)), List.of(), 60, 10).get(0);

        assertEquals(1.0 / 61.0, candidate.toHit().score(), 1e-9);
        assertEquals("doc", candidate.toHit().documentId());
    }

    private DocumentSearchHit hit(String documentId, int index, double score) {
        return new DocumentSearchHit(documentId, documentId + ".md", KnowledgeType.STUDY, index,
            "content", score, documentId + "-" + index);
    }
}
