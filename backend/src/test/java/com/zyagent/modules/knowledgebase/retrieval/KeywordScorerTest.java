package com.zyagent.modules.knowledgebase.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordScorerTest {
    @Test
    void tokenizesTechnicalIdentifiersAndChineseBigrams() {
        List<String> tokens = KeywordScorer.tokenize("Redis 缓存一致性和 Spring Boot 的集成");

        assertTrue(tokens.contains("redis"), "ascii identifier kept");
        assertTrue(tokens.contains("spring"), "multi-word identifier split");
        assertTrue(tokens.contains("boot"), "multi-word identifier split");
        assertTrue(tokens.contains("缓存"), "chinese bigram produced");
        assertTrue(tokens.contains("一致"), "chinese bigram produced");
    }

    @Test
    void ignoresStopwordsAndSingleShortAscii() {
        List<String> tokens = KeywordScorer.tokenize("the a how");

        assertTrue(tokens.isEmpty(), "stopwords and short tokens dropped");
    }

    @Test
    void emptyQueryProducesNoTokens() {
        assertTrue(KeywordScorer.tokenize("   ").isEmpty());
        assertTrue(KeywordScorer.tokenize(null).isEmpty());
    }

    @Test
    void coverageIsFractionOfMatchedTokens() {
        List<String> tokens = List.of("redis", "缓存", "mysql");

        assertEquals(2.0 / 3.0, KeywordScorer.coverage(tokens, "Redis 缓存一致性方案"), 1e-9);
        assertEquals(0.0, KeywordScorer.coverage(tokens, "无关内容"), 1e-9);
        assertEquals(0.0, KeywordScorer.coverage(List.of(), "任意内容"), 1e-9);
    }

    @Test
    void escapesLikeWildcards() {
        assertEquals("a\\%b\\_c", KeywordScorer.escapeLike("a%b_c"), "wildcards escaped literally");
    }
}
