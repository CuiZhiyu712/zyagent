package com.zyagent.modules.knowledgebase.retrieval;

/**
 * 检索链路的可调参数：每路召回候选数、融合上限、RRF 常数与最终重排数量。
 */
public record RetrievalSettings(
    int vectorTopK,
    int keywordTopK,
    int fuseLimit,
    int rankConstant,
    int rerankLimit
) {
    public RetrievalSettings {
        vectorTopK = Math.max(1, vectorTopK);
        keywordTopK = Math.max(1, keywordTopK);
        fuseLimit = Math.max(1, fuseLimit);
        rankConstant = Math.max(1, rankConstant);
        rerankLimit = Math.max(1, rerankLimit);
    }

    public static RetrievalSettings defaults() {
        return new RetrievalSettings(20, 20, 30, 60, 8);
    }
}
