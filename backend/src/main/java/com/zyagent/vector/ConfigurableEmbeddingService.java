package com.zyagent.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.config.ZyagentProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Primary
@Service
public class ConfigurableEmbeddingService implements EmbeddingService {
    private final ZyagentProperties.Embedding properties;
    private final HashEmbeddingService fallback;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ConfigurableEmbeddingService(ZyagentProperties properties, HashEmbeddingService fallback, ObjectMapper objectMapper) {
        this.properties = properties.embedding();
        this.fallback = fallback;
        this.objectMapper = objectMapper;
    }

    @Override
    public int dimension() {
        if (isExternalConfigured() && properties.dimension() > 0) {
            return properties.dimension();
        }
        return fallback.dimension();
    }

    @Override
    public List<Float> embed(String text) {
        if (!isExternalConfigured()) {
            return fallback.embed(text);
        }
        try {
            return callOpenAiCompatibleEmbedding(text);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return fallback.embed(text);
        } catch (RuntimeException | IOException ex) {
            return fallback.embed(text);
        }
    }

    private boolean isExternalConfigured() {
        return properties != null
            && properties.apiKey() != null
            && !properties.apiKey().isBlank()
            && properties.baseUrl() != null
            && !properties.baseUrl().isBlank()
            && !"hash".equalsIgnoreCase(properties.provider());
    }

    private List<Float> callOpenAiCompatibleEmbedding(String text) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of(
            "model", properties.model(),
            "input", text == null ? "" : text
        ));
        HttpRequest request = HttpRequest.newBuilder(URI.create(normalizeEmbeddingUrl(properties.baseUrl())))
            .header("Authorization", "Bearer " + properties.apiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Embedding request failed: " + response.statusCode());
        }
        JsonNode embedding = objectMapper.readTree(response.body()).path("data").path(0).path("embedding");
        if (!embedding.isArray() || embedding.isEmpty()) {
            throw new IllegalStateException("Embedding response missing data[0].embedding");
        }
        List<Float> values = new ArrayList<>(embedding.size());
        for (JsonNode item : embedding) {
            values.add((float) item.asDouble());
        }
        return values;
    }

    private String normalizeEmbeddingUrl(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (trimmed.endsWith("/embeddings")) {
            return trimmed;
        }
        return trimmed + "/embeddings";
    }
}
