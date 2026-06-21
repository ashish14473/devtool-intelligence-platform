package com.devtools.intelligence.service;

import com.devtools.intelligence.config.OpenAiProperties;
import com.devtools.intelligence.dto.ChatResponse;
import com.devtools.intelligence.dto.SourceTicket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG flow backed by pgvector:
 * 1. Embed the user question (same model used at ingestion time)
 * 2. Retrieve top-K similar tickets from pgvector via cosine similarity
 * 3. Build a grounded prompt from the retrieved context
 * 4. Call OpenAI chat completions — LLM answers using only retrieved tickets
 */
@Service
@Slf4j
public class ChatService {

    private static final int TOP_K = 5;

    private final EmbeddingService embeddingService;
    private final PgVectorStore pgVectorStore;
    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public ChatService(EmbeddingService embeddingService,
                       PgVectorStore pgVectorStore,
                       @Qualifier("openAiRestClient") RestClient openAiRestClient,
                       OpenAiProperties properties,
                       ObjectMapper objectMapper) {
        this.embeddingService = embeddingService;
        this.pgVectorStore = pgVectorStore;
        this.openAiRestClient = openAiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ChatResponse answer(String question, String toolFilter) {

        float[] queryVector = embeddingService.embed(question);

        List<PgVectorStore.ScoredDocument> matches =
                pgVectorStore.search(queryVector, TOP_K, toolFilter);

        if (matches.isEmpty()) {
            return new ChatResponse(
                    "No relevant resolved tickets found"
                            + (toolFilter != null && !toolFilter.isBlank() ? " for " + toolFilter : "")
                            + ". Try a different question or check that ingestion completed.",
                    List.of()
            );
        }

        String context = buildContext(matches);
        String answer = generateAnswer(question, context);

        List<SourceTicket> sources = matches.stream()
                .map(m -> new SourceTicket(
                        m.document().getTicketId(),
                        m.document().getToolName(),
                        m.document().getCategory(),
                        m.document().getPainPoint(),
                        Math.round(m.score() * 1000) / 1000.0
                ))
                .collect(Collectors.toList());

        return new ChatResponse(answer, sources);
    }

    private String buildContext(List<PgVectorStore.ScoredDocument> matches) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (PgVectorStore.ScoredDocument m : matches) {
            sb.append("[Source ").append(i++).append(" — Ticket ").append(m.document().getTicketId())
              .append(", Tool: ").append(m.document().getToolName())
              .append(", Category: ").append(m.document().getCategory())
              .append("]\n")
              .append(m.document().getEmbeddedText())
              .append("\n\n");
        }
        return sb.toString();
    }

    private String generateAnswer(String question, String context) {

        String system = """
                You are a developer tools support assistant. Answer using ONLY the provided \
                sources. Rules:
                - If sources have a clear answer: give it in 3–6 sentences.
                - Reference ticket IDs where relevant (e.g. "as seen in CICD-1421").
                - If sources only partially help: share what is known, say the rest is unclear.
                - Never fabricate ticket IDs, fixes, or root causes not in the sources.
                """;

        String user = "Question: " + question + "\n\nSources:\n" + context;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getChatModel());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user",   "content", user)
        ));

        try {
            String raw = openAiRestClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            return root.path("choices").path(0).path("message").path("content").asText();

        } catch (Exception e) {
            log.error("Chat generation failed: {}", e.getMessage());
            return "Answer generation failed. Retrieved sources are still shown below.";
        }
    }
}
