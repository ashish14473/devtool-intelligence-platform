package com.devtools.intelligence.service;

import com.devtools.intelligence.config.JiraProperties;
import com.devtools.intelligence.config.OpenAiProperties;
import com.devtools.intelligence.dto.AgentWebhookPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core agent logic — triggered when a new Jira ticket is created.
 *
 * Flow:
 * 1. Extract ticket text (summary + description) from webhook payload
 * 2. Embed the ticket text using OpenAI
 * 3. Search pgvector for similar resolved tickets
 * 4. Gate on similarity threshold — stay silent if no good matches
 * 5. Generate a grounded answer via LLM using the retrieved context
 * 6. Post the answer as a comment on the ticket via JiraCommentService
 *
 * This is a RAG agent — it only answers from the knowledge base,
 * never from the LLM's general knowledge.
 */
@Service
@Slf4j
public class TicketAgentService {

    private static final int TOP_K = 5;

    private final EmbeddingService embeddingService;
    private final PgVectorStore pgVectorStore;
    private final JiraCommentService jiraCommentService;
    private final JiraProperties jiraProperties;
    private final OpenAiProperties openAiProperties;
    private final RestClient openAiRestClient;
    private final ObjectMapper objectMapper;

    public TicketAgentService(EmbeddingService embeddingService,
                               PgVectorStore pgVectorStore,
                               JiraCommentService jiraCommentService,
                               JiraProperties jiraProperties,
                               OpenAiProperties openAiProperties,
                               @Qualifier("openAiRestClient") RestClient openAiRestClient,
                               ObjectMapper objectMapper) {
        this.embeddingService = embeddingService;
        this.pgVectorStore = pgVectorStore;
        this.jiraCommentService = jiraCommentService;
        this.jiraProperties = jiraProperties;
        this.openAiProperties = openAiProperties;
        this.openAiRestClient = openAiRestClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Main entry point called by AgentController when a webhook fires.
     * Runs the full RAG pipeline and posts a comment if confident enough.
     */
    public void handleNewTicket(AgentWebhookPayload payload) {
        String issueKey = payload.getIssueKey();
        String summary = payload.getSummary();
        String description = payload.getDescription() != null
                ? payload.getDescription() : "";

        log.info("Agent processing new ticket: {} - {}", issueKey, summary);

        // Step 1: embed the ticket's summary + description
        String queryText = summary + "\n" + description;
        float[] queryVector;
        try {
            queryVector = embeddingService.embed(queryText);
        } catch (Exception e) {
            log.error("Embedding failed for ticket {}: {}", issueKey, e.getMessage());
            return;
        }

        // Step 2: search pgvector — no tool filter, search across all tools
        List<PgVectorStore.ScoredDocument> matches =
                pgVectorStore.search(queryVector, TOP_K, null);

        if (matches.isEmpty()) {
            log.info("No matches found in knowledge base for ticket {} — staying silent", issueKey);
            return;
        }

        double bestScore = matches.get(0).score();
        log.info("Best similarity score for {}: {}", issueKey, bestScore);

        // Step 3: gate on threshold — only respond if confident
        if (bestScore < jiraProperties.getSimilarityThreshold()) {
            log.info("Best score {:.3f} below threshold {:.3f} for ticket {} — staying silent",
                    bestScore, jiraProperties.getSimilarityThreshold(), issueKey);
            return;
        }

        // Step 4: generate grounded answer
        String context = buildContext(matches);
        String answer = generateAnswer(summary, description, context);

        if (answer == null || answer.isBlank()) {
            log.warn("Empty answer generated for ticket {} — not posting", issueKey);
            return;
        }

        // Step 5: collect source ticket IDs to cite in the comment
        List<String> sourceIds = matches.stream()
                .filter(m -> m.score() >= jiraProperties.getSimilarityThreshold())
                .map(m -> m.document().getTicketId())
                .collect(Collectors.toList());

        // Step 6: post comment on the Jira ticket
        try {
            jiraCommentService.postSuggestedAnswer(issueKey, answer, sourceIds);
            log.info("Agent successfully commented on ticket {} with {} sources",
                    issueKey, sourceIds.size());
        } catch (Exception e) {
            log.error("Agent failed to post comment on ticket {}: {}", issueKey, e.getMessage());
        }
    }

    private String buildContext(List<PgVectorStore.ScoredDocument> matches) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (PgVectorStore.ScoredDocument m : matches) {
            sb.append("[Source ").append(i++).append(" — ")
              .append(m.document().getTicketId())
              .append(", Tool: ").append(m.document().getToolName())
              .append(", Category: ").append(m.document().getCategory())
              .append(", Similarity: ").append(String.format("%.0f%%", m.score() * 100))
              .append("]\n")
              .append(m.document().getEmbeddedText())
              .append("\n\n");
        }
        return sb.toString();
    }

    private String generateAnswer(String summary, String description, String context) {

        String system = """
                You are a developer tools support assistant. A developer has just raised a \
                new support ticket. Your job is to search the knowledge base and provide a \
                helpful suggested answer based ONLY on the provided sources.

                Rules:
                - Answer directly and concisely in 3-6 sentences.
                - Cite source ticket IDs where relevant (e.g. "as resolved in SQ-1006").
                - If the sources contain a clear fix or workaround, lead with that.
                - If sources only partially match, say what IS known and flag that a human \
                will follow up.
                - Never fabricate fixes or ticket IDs not present in the sources.
                - Write in a helpful, professional tone — the developer is waiting for help.
                """;

        String user = """
                New ticket summary: %s

                Description: %s

                Knowledge base sources:
                %s
                """.formatted(summary, description, context);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openAiProperties.getChatModel());
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
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
            log.error("LLM answer generation failed: {}", e.getMessage());
            return null;
        }
    }
}
