package com.zyagent.modules.knowledgebase.retrieval;

import java.util.List;

/**
 * 一次检索的可观测追踪：每路召回的状态/耗时/候选数、融合参数、重排状态与最终引用。
 *
 * <p>失败与降级都如实记录，便于前端诊断；不把降级结果伪装成成功。
 */
public record RetrievalTrace(
    String vectorStatus,
    long vectorLatencyMs,
    int vectorCandidates,
    String keywordStatus,
    long keywordLatencyMs,
    int keywordCandidates,
    int fusedCandidates,
    int rankConstant,
    int fuseLimit,
    String rerankMode,
    String rerankStatus,
    long rerankLatencyMs,
    int rerankLimit,
    List<String> finalRefs,
    String note
) {
    public static final String OK = "ok";
    public static final String FAILED = "failed";
    public static final String UNAVAILABLE = "unavailable";
    public static final String SKIPPED = "skipped";

    public RetrievalTrace {
        finalRefs = finalRefs == null ? List.of() : List.copyOf(finalRefs);
    }

    /** 供 {@code DocumentSearchResponse.searchMode} 使用的紧凑模式串，保持前端兼容。 */
    public String searchMode() {
        return "hybrid:" + vectorStatus + "+" + keywordStatus + ";rerank=" + rerankMode;
    }

    public static RetrievalTrace noResults(int rankConstant, int fuseLimit) {
        return new Builder(rankConstant, fuseLimit)
            .vector(OK, 0L, 0)
            .keyword(OK, 0L, 0)
            .fused(0)
            .rerank("none", SKIPPED, 0L, 0)
            .note("无召回结果")
            .build();
    }

    public static RetrievalTrace memoryFallback(String note) {
        return new Builder(RrfFusion.DEFAULT_RANK_CONSTANT, 0)
            .vector(UNAVAILABLE, 0L, 0)
            .keyword(UNAVAILABLE, 0L, 0)
            .fused(0)
            .rerank("none", SKIPPED, 0L, 0)
            .note(note)
            .build();
    }

    public static Builder builder(int rankConstant, int fuseLimit) {
        return new Builder(rankConstant, fuseLimit);
    }

    /** 可变构建器，避免十几参构造调用易错。 */
    public static final class Builder {
        private String vectorStatus = UNAVAILABLE;
        private long vectorLatencyMs;
        private int vectorCandidates;
        private String keywordStatus = UNAVAILABLE;
        private long keywordLatencyMs;
        private int keywordCandidates;
        private int fusedCandidates;
        private final int rankConstant;
        private final int fuseLimit;
        private String rerankMode = "none";
        private String rerankStatus = SKIPPED;
        private long rerankLatencyMs;
        private int rerankLimit;
        private List<String> finalRefs = List.of();
        private String note = "";

        private Builder(int rankConstant, int fuseLimit) {
            this.rankConstant = rankConstant;
            this.fuseLimit = fuseLimit;
        }

        public Builder vector(String status, long latencyMs, int candidates) {
            this.vectorStatus = status;
            this.vectorLatencyMs = latencyMs;
            this.vectorCandidates = candidates;
            return this;
        }

        public Builder keyword(String status, long latencyMs, int candidates) {
            this.keywordStatus = status;
            this.keywordLatencyMs = latencyMs;
            this.keywordCandidates = candidates;
            return this;
        }

        public Builder fused(int fusedCandidates) {
            this.fusedCandidates = fusedCandidates;
            return this;
        }

        public Builder rerank(String mode, String status, long latencyMs, int limit) {
            this.rerankMode = mode;
            this.rerankStatus = status;
            this.rerankLatencyMs = latencyMs;
            this.rerankLimit = limit;
            return this;
        }

        public Builder finalRefs(List<String> finalRefs) {
            this.finalRefs = finalRefs;
            return this;
        }

        public Builder note(String note) {
            this.note = note;
            return this;
        }

        public RetrievalTrace build() {
            return new RetrievalTrace(vectorStatus, vectorLatencyMs, vectorCandidates,
                keywordStatus, keywordLatencyMs, keywordCandidates, fusedCandidates,
                rankConstant, fuseLimit, rerankMode, rerankStatus, rerankLatencyMs, rerankLimit,
                finalRefs, note);
        }
    }
}
