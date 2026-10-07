package com.zyagent.document.retrieval;

import com.zyagent.document.DocumentSearchHit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 融合后的检索候选：保留每个召回渠道的原始排名与分数，便于解释融合结果。
 *
 * <p>去重键是 {@code documentId:chunkIndex}；同一 chunk 被多个渠道命中时累加 RRF 分数，
 * 并分别记录各渠道的 rank 与原始 score。
 */
public record RetrievalCandidate(
    String key,
    DocumentSearchHit hit,
    Map<String, ChannelScore> channelScores,
    double fusedScore
) {
    public record ChannelScore(String channel, int rank, double score) {
    }

    public static RetrievalCandidate first(String key, DocumentSearchHit hit, String channel, int rank, double rrfScore) {
        Map<String, ChannelScore> scores = Map.of(channel, new ChannelScore(channel, rank, hit.score()));
        return new RetrievalCandidate(key, hit, scores, rrfScore);
    }

    public RetrievalCandidate merge(String channel, int rank, double rawScore, double rrfScore) {
        if (channelScores.containsKey(channel)) {
            return this;
        }
        Map<String, ChannelScore> merged = new LinkedHashMap<>(channelScores);
        merged.put(channel, new ChannelScore(channel, rank, rawScore));
        return new RetrievalCandidate(key, hit, Collections.unmodifiableMap(merged), fusedScore + rrfScore);
    }

    public Set<String> channels() {
        return channelScores.keySet();
    }

    /** 用融合分数覆盖 hit 的 score，使最终引用按统一尺度可比较。 */
    public DocumentSearchHit toHit() {
        return new DocumentSearchHit(
            hit.documentId(), hit.filename(), hit.knowledgeType(), hit.chunkIndex(), hit.content(),
            fusedScore, hit.vectorId());
    }
}
