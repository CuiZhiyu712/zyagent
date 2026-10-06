package com.zyagent.modules.knowledgebase;

import com.zyagent.modules.job.TestAssertions;

import java.util.List;

public class DocumentSearchResponseTest {
    public static void run() {
        DocumentSearchHit hit = new DocumentSearchHit(
            "doc-1",
            "resume-demo.md",
            KnowledgeType.RESUME,
            2,
            "熟悉 Java、Spring Boot、Redis 和 MySQL。",
            0.87,
            "doc-1-2"
        );
        DocumentSearchResponse response = new DocumentSearchResponse("milvus", List.of(hit));

        TestAssertions.equals("milvus", response.searchMode(), "search mode");
        TestAssertions.equals("resume-demo.md", response.hits().get(0).filename(), "hit filename");
        TestAssertions.equals(KnowledgeType.RESUME, response.hits().get(0).knowledgeType(), "hit type");
        TestAssertions.equals(0.87, response.hits().get(0).score(), "hit score");
        TestAssertions.equals("doc-1-2", response.hits().get(0).vectorId(), "hit vector id");
    }
}
