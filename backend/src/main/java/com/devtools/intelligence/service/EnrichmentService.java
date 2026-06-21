package com.devtools.intelligence.service;

import com.devtools.intelligence.config.OpenAiProperties;
import com.devtools.intelligence.model.EnrichedTicket;
import com.devtools.intelligence.model.JiraTicket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI's chat completions API to turn a raw, noisy Jira ticket
 * into a clean, structured EnrichedTicket: tool name, category,
 * severity, a one-sentence pain point, and a factual summary.
 *
 * This is the Java equivalent of the Spring AI ChatClient +
 * BeanOutputConverter example shown earlier - same prompt, same JSON
 * contract, just driven through a plain RestClient call so it has no
 * dependency on Spring AI's release cycle.
 */
@Service
@Slf4j
public class EnrichmentService {

    private final RestClient openAiRestClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public EnrichmentService(@Qualifier("openAiRestClient") RestClient openAiRestClient,
                              OpenAiProperties properties,
                              ObjectMapper objectMapper) {
        this.openAiRestClient = openAiRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Enriches a single ticket via one chat completion call.
     * Uses OpenAI's "JSON mode" (response_format: json_object) so the
     * model is constrained to return valid JSON - this is what lets us
     * parse the response directly into EnrichedTicket without brittle
     * regex or markdown-fence stripping.
     */
    public EnrichedTicket enrich(JiraTicket ticket) {

        String systemPrompt = """
                You are a support ticket analyst for an internal developer tools team.
                Analyse the given ticket and return ONLY a JSON object - no markdown, \
                no preamble, no code fences - matching exactly this shape:

                {
                  "toolName": string,
                  "category": one of ["auth","performance","config","integration","bug","docs","feature-request"],
                  "painPoint": string,
                  "summary": string,
                  "severity": one of ["low","medium","high","critical"]
                }

                Rules:
                - toolName: use the tool name given in the ticket if present, otherwise infer it from context.
                - category: pick the single best-fitting category from the list above.
                - painPoint: one plain-language sentence describing what the developer experienced. No ticket IDs, no jargon.
                - summary: 3-5 factual sentences covering problem, root cause (if known), fix (if known), and workaround (if any). \
                Strip names, dates, ticket IDs and URLs. Do not speculate beyond what is stated.
                - severity: base this on the ticket's priority field and the apparent impact described.
                """;

        String userPrompt = """
                Ticket ID: %s
                Tool name (from Jira field): %s
                Priority: %s
                Status: %s
                Labels: %s

                Summary: %s

                Description: %s

                Comments: %s
                """.formatted(
                ticket.getTicketId(),
                ticket.getToolName(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getLabels(),
                ticket.getSummary(),
                ticket.getDescription(),
                ticket.getComments()
        );

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", properties.getChatModel());
        requestBody.put("temperature", 0.1); // low temperature - we want consistent, factual extraction
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        try {
            String rawResponse = openAiRestClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseEnrichedTicket(rawResponse, ticket.getTicketId());

        } catch (Exception e) {
            log.error("Enrichment failed for ticket {}: {}", ticket.getTicketId(), e.getMessage());
            // Fail gracefully with a fallback rather than crashing the whole
            // ingestion run over one bad ticket - this mirrors how the real
            // pipeline should behave under partial failure.
            return fallbackEnrichment(ticket);
        }
    }

    private EnrichedTicket parseEnrichedTicket(String rawResponse, String ticketId) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        String content = root.path("choices").path(0).path("message").path("content").asText();

        if (content.isBlank()) {
            throw new IllegalStateException("Empty completion content for ticket " + ticketId);
        }

        return objectMapper.readValue(content, EnrichedTicket.class);
    }

    private EnrichedTicket fallbackEnrichment(JiraTicket ticket) {
        // A conservative, clearly-marked fallback so a single API failure
        // (rate limit, transient network issue) doesn't drop the ticket
        // from the knowledge base entirely - it just gets a lower-quality
        // entry that's still searchable on tool name and raw summary.
        return new EnrichedTicket(
                ticket.getToolName(),
                "bug",
                ticket.getSummary(),
                "Automatic enrichment failed for this ticket; showing raw summary: " + ticket.getSummary(),
                "medium"
        );
    }
}
