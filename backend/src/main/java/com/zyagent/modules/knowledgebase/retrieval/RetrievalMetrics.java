package com.zyagent.modules.knowledgebase.retrieval;

import java.util.List;
import java.util.Set;

/**
 * 检索质量指标：Recall@K、MRR@K、nDCG@K。
 *
 * <p>用于离线评测集，比较融合/重排前后的排名变化。所有方法对空相关集返回 0，不抛异常。
 */
public final class RetrievalMetrics {
    private RetrievalMetrics() {
    }

    public static double recallAtK(List<String> ranked, Set<String> relevant, int k) {
        if (relevant.isEmpty()) {
            return 0.0;
        }
        int hits = 0;
        for (String key : topK(ranked, k)) {
            if (relevant.contains(key)) {
                hits++;
            }
        }
        return (double) hits / relevant.size();
    }

    public static double reciprocalRankAtK(List<String> ranked, Set<String> relevant, int k) {
        List<String> top = topK(ranked, k);
        for (int i = 0; i < top.size(); i++) {
            if (relevant.contains(top.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    public static double ndcgAtK(List<String> ranked, Set<String> relevant, int k) {
        List<String> top = topK(ranked, k);
        double dcg = 0.0;
        for (int i = 0; i < top.size(); i++) {
            if (relevant.contains(top.get(i))) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
            }
        }
        int idealHits = Math.min(relevant.size(), k);
        double idcg = 0.0;
        for (int i = 0; i < idealHits; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    private static List<String> topK(List<String> ranked, int k) {
        if (ranked == null || k <= 0) {
            return List.of();
        }
        return ranked.size() > k ? ranked.subList(0, k) : ranked;
    }
}
