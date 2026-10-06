package com.zyagent.modules.knowledgebase.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.infrastructure.config.ZyagentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按配置装配检索参数与 Reranker。
 *
 * <p>只有在 {@code zyagent.retrieval.rerank.enabled=true} 且配置了 endpoint 时才启用外部
 * cross-encoder；否则一律使用本地 {@link RrfFallbackReranker}，并在检索追踪中标注 {@code rrf_fallback}。
 */
@Configuration
public class RetrievalConfig {
    @Bean
    RetrievalSettings retrievalSettings(ZyagentProperties properties) {
        ZyagentProperties.Retrieval retrieval = properties == null ? null : properties.retrieval();
        if (retrieval == null) {
            return RetrievalSettings.defaults();
        }
        return new RetrievalSettings(
            retrieval.vectorTopK(), retrieval.keywordTopK(),
            retrieval.fuseLimit(), retrieval.rankConstant(), retrieval.rerankLimit());
    }

    @Bean
    Reranker reranker(ZyagentProperties properties, ObjectMapper objectMapper) {
        ZyagentProperties.Rerank rerank = properties == null || properties.retrieval() == null
            ? null
            : properties.retrieval().rerank();
        if (rerank != null && rerank.enabled() && rerank.endpoint() != null && !rerank.endpoint().isBlank()) {
            return new HttpCrossEncoderReranker(
                rerank.endpoint(), rerank.timeoutMs(), rerank.maxCandidates(), rerank.maxContentChars(), objectMapper);
        }
        return new RrfFallbackReranker();
    }
}
