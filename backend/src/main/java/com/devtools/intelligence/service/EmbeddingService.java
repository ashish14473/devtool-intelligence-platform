package com.devtools.intelligence.service;

import com.devtools.intelligence.config.OpenAiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calls OpenAI's embeddings API to turn text into a vector.
 * Used twice in this pipeline: once per ticket during ingestion
 * (embedding the enriched summary), and once per chat message
 * at query time (embedding the user's question for similarity search).
 *
 * Using the same model for both is essential - embeddings from
 * different models live in different vector spaces and aren't
 * comparable via cosine similarity.
 */
@Service
@Slf4j
public class EmbeddingService {

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public EmbeddingService(@Qualifier("openAiRestClient") RestClient openAiRestClient,
                             OpenAiProperties properties,
                             ObjectMapper objectMapper) {
        this.openAiRestClient = openAiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Embeds a single piece of text and returns the raw vector.
     * text-embedding-3-small returns 1536 dimensions by default,
     * matching openai.embedding-dimensions in application.yml.
     */
    public float[] embed(String text) {

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", properties.getEmbeddingModel());
        requestBody.put("input", text);

        try {
            String rawResponse = openAiRestClient.post()
                    .uri("/embeddings")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseEmbedding(rawResponse);

        } catch (Exception e) {
            log.error("Embedding failed for text starting with '{}...': {}",
                    text.substring(0, Math.min(50, text.length())), e.getMessage());
            throw new RuntimeException("Embedding call failed", e);
        }
    }

    private float[] parseEmbedding(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode vectorNode = root.path("data").path(0).path("embedding");

        if (!vectorNode.isArray()) {
            throw new IllegalStateException("Embedding response did not contain a vector array");
        }

        float[] vector = new float[vectorNode.size()];
        for (int i = 0; i < vectorNode.size(); i++) {
            vector[i] = vectorNode.get(i).floatValue();
        }
        return vector;
    }
}
