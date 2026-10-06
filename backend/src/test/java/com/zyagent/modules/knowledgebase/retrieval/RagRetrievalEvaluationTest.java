package com.zyagent.modules.knowledgebase.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.modules.knowledgebase.DocumentSearchHit;
import com.zyagent.modules.knowledgebase.KnowledgeType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 融合/重排阶段的离线评测基线。
 *
 * <p>评测集 {@code docs/evaluation/rag-retrieval-cases.jsonl} 固化每路的候选排名 fixture，
 * 因此不需要 Milvus/MySQL 即可重跑，用于比较融合与重排前后的排名质量。
 * 阈值是当前 baseline，改动应在 README 中记录新数值而不是无声提升门槛。
 */
class RagRetrievalEvaluationTest {
    private static final int RECALL_K = 5;
    private static final int RANK_K = 10;
    private static final int RANK_CONSTANT = 60;
    private static final int FUSE_LIMIT = 30;
    private static final int RERANK_LIMIT = 8;

    @Test
    void fusionBaselineMeetsThresholds() throws Exception {
        List<Case> cases = loadCases();
        assertFalse(cases.isEmpty(), "评测集应包含用例");

        double recallSum = 0.0;
        double mrrSum = 0.0;
        double ndcgSum = 0.0;
        double coverageSum = 0.0;
        for (Case evalCase : cases) {
            List<RetrievalCandidate> fused = RrfFusion.merge(
                hits(evalCase.vector()), hits(evalCase.keyword()), RANK_CONSTANT, FUSE_LIMIT);
            List<String> ranked = new RrfFallbackReranker().rerank(evalCase.query(), fused, RERANK_LIMIT)
                .candidates().stream().map(RetrievalCandidate::key).toList();

            Set<String> recalled = new HashSet<>(evalCase.vector());
            recalled.addAll(evalCase.keyword());
            coverageSum += evalCase.relevant().stream().anyMatch(recalled::contains) ? 1.0 : 0.0;

            recallSum += RetrievalMetrics.recallAtK(ranked, evalCase.relevant(), RECALL_K);
            mrrSum += RetrievalMetrics.reciprocalRankAtK(ranked, evalCase.relevant(), RANK_K);
            ndcgSum += RetrievalMetrics.ndcgAtK(ranked, evalCase.relevant(), RANK_K);
        }

        int total = cases.size();
        double recall = recallSum / total;
        double mrr = mrrSum / total;
        double ndcg = ndcgSum / total;
        double coverage = coverageSum / total;
        System.out.printf(
            "RAG fusion baseline (n=%d): recall@%d=%.3f mrr@%d=%.3f ndcg@%d=%.3f two-channel-coverage=%.3f%n",
            total, RECALL_K, recall, RANK_K, mrr, RANK_K, ndcg, coverage);

        assertTrue(recall >= 0.85, "recall@" + RECALL_K + " below baseline: " + recall);
        assertTrue(mrr >= 0.65, "mrr@" + RANK_K + " below baseline: " + mrr);
        assertTrue(ndcg >= 0.72, "ndcg@" + RANK_K + " below baseline: " + ndcg);
        assertTrue(coverage >= 0.85, "two-channel coverage below baseline: " + coverage);
    }

    private static List<DocumentSearchHit> hits(List<String> keys) {
        List<DocumentSearchHit> hits = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String[] parts = keys.get(i).split(":");
            String documentId = parts[0];
            int chunkIndex = Integer.parseInt(parts[1]);
            hits.add(new DocumentSearchHit(documentId, documentId + ".md", KnowledgeType.STUDY, chunkIndex,
                "content-" + keys.get(i), Math.max(0.01, 1.0 - i * 0.01), keys.get(i)));
        }
        return hits;
    }

    private static List<Case> loadCases() throws Exception {
        Path path = resolveCasesPath();
        ObjectMapper mapper = new ObjectMapper();
        List<Case> cases = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node = mapper.readTree(line);
            cases.add(new Case(
                node.path("id").asText(),
                node.path("query").asText(),
                toSet(node.path("relevant")),
                toList(node.path("vector")),
                toList(node.path("keyword"))));
        }
        return cases;
    }

    private static Path resolveCasesPath() {
        for (Path candidate : List.of(
            Paths.get("..", "docs", "evaluation", "rag-retrieval-cases.jsonl"),
            Paths.get("docs", "evaluation", "rag-retrieval-cases.jsonl"))) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到 docs/evaluation/rag-retrieval-cases.jsonl");
    }

    private static Set<String> toSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private static List<String> toList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private record Case(String id, String query, Set<String> relevant, List<String> vector, List<String> keyword) {
    }
}
