package com.zyagent.modules.knowledgebase.retrieval;

import com.zyagent.modules.knowledgebase.DocumentSearchHit;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion：{@code score = Σ 1 / (k + rank)}。
 *
 * <p>按 {@code documentId:chunkIndex} 去重；同一 chunk 在多个渠道命中时累加分数，
 * 并保留各渠道的 rank 与原始 score 供解释。空渠道视为无贡献。
 */
public final class RrfFusion {
    public static final String VECTOR_CHANNEL = "vector";
    public static final String KEYWORD_CHANNEL = "keyword";
    public static final int DEFAULT_RANK_CONSTANT = 60;

    private RrfFusion() {
    }

    public static String chunkKey(DocumentSearchHit hit) {
        return hit.documentId() + ":" + hit.chunkIndex();
    }

    public static List<RetrievalCandidate> merge(
        List<DocumentSearchHit> vectorHits,
        List<DocumentSearchHit> keywordHits,
        int rankConstant,
        int limit
    ) {
        Map<String, RetrievalCandidate> candidates = new LinkedHashMap<>();
        add(candidates, vectorHits, VECTOR_CHANNEL, rankConstant);
        add(candidates, keywordHits, KEYWORD_CHANNEL, rankConstant);
        return candidates.values().stream()
            .sorted(Comparator.comparingDouble(RetrievalCandidate::fusedScore).reversed()
                .thenComparing(RetrievalCandidate::key))
            .limit(Math.max(0, limit))
            .toList();
    }

    private static void add(
        Map<String, RetrievalCandidate> candidates,
        List<DocumentSearchHit> hits,
        String channel,
        int rankConstant
    ) {
        if (hits == null) {
            return;
        }
        int k = Math.max(1, rankConstant);
        for (int index = 0; index < hits.size(); index++) {
            DocumentSearchHit hit = hits.get(index);
            String key = chunkKey(hit);
            int rank = index + 1;
            double rrfScore = 1.0 / (k + rank);
            RetrievalCandidate current = candidates.get(key);
            candidates.put(key, current == null
                ? RetrievalCandidate.first(key, hit, channel, rank, rrfScore)
                : current.merge(channel, rank, hit.score(), rrfScore));
        }
    }
}
