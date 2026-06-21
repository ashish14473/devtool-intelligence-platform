package com.devtools.intelligence.service;

import com.devtools.intelligence.config.OpenAiProperties;
import com.devtools.intelligence.model.EnrichedTicket;
import com.devtools.intelligence.model.JiraTicket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI chat completions to enrich a raw Jira ticket.
 *
 * Single LLM call returns all fields in one JSON response:
 *   - Phase 1: toolName, category, painPoint, summary, severity
 *   - Phase 2: resolutionType, sentimentScore, frustrationFlag,
 *              recurrenceSignal, rootCauseTool, knowledgeGapFlag,
 *              knowledgeGapDescription
 *
 * No extra API calls per ticket — all analytics derived in one shot.
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

    public EnrichedTicket enrich(JiraTicket ticket) {

        String systemPrompt = """
                You are a developer tools support analyst. Analyse the given ticket and \
                return ONLY a JSON object — no markdown, no preamble, no code fences.

                Return exactly this shape:
                {
                  "toolName": string,
                  "category": one of ["auth","performance","config","integration","bug",\
                "onboarding","docs","migration","feature-request","pipeline-issue",\
                "access","cleanup","proxy-setup","plugin-conflict","credential-issue"],
                  "painPoint": string,
                  "summary": string,
                  "severity": one of ["low","medium","high","critical"],
                  "resolutionType": one of ["FIXED","WORKAROUND","UNANSWERED","ABANDONED"],
                  "sentimentScore": integer 1-5,
                  "frustrationFlag": boolean,
                  "recurrenceSignal": boolean,
                  "rootCauseTool": string or null,
                  "knowledgeGapFlag": boolean,
                  "knowledgeGapDescription": string or null
                }

                Field rules:
                - toolName: use the tool name from the ticket key prefix or infer from context
                - category: pick the single best-fitting category
                - painPoint: one plain-language sentence, no ticket IDs
                - summary: 3-5 factual sentences: problem, root cause, fix, workaround. \
                Strip names, dates, ticket IDs, URLs
                - severity: based on priority field and described business impact
                - resolutionType: FIXED = root cause permanently addressed; \
                WORKAROUND = temporary fix, likely to recur; \
                UNANSWERED = closed without clear resolution; \
                ABANDONED = reporter stopped responding
                - sentimentScore: 1 = very positive/satisfied, 5 = very frustrated/angry. \
                Read tone of comments especially the reporter's final comment
                - frustrationFlag: true if any comment shows frustration, resignation, \
                repeated complaints, or dissatisfaction with the resolution
                - recurrenceSignal: true if comments say "this keeps happening", \
                "third time this month", "workaround", or suggest root cause not fixed
                - rootCauseTool: if the root cause lies in a DIFFERENT tool than the one \
                the ticket was filed against, name that tool. Otherwise null
                - knowledgeGapFlag: true if a documentation article or onboarding guide \
                would have prevented this ticket from being raised
                - knowledgeGapDescription: if knowledgeGapFlag is true, one sentence \
                describing what article would prevent this class of ticket. Otherwise null
                """;

        String userPrompt = """
                Ticket ID: %s
                Tool (from key prefix): %s
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
        requestBody.put("temperature", 0.1);
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
        EnrichedTicket e = new EnrichedTicket();
        e.setToolName(ticket.getToolName());
        e.setCategory("bug");
        e.setPainPoint(ticket.getSummary());
        e.setSummary("Automatic enrichment failed. Raw summary: " + ticket.getSummary());
        e.setSeverity("medium");
        e.setResolutionType("UNANSWERED");
        e.setSentimentScore(3);
        e.setFrustrationFlag(false);
        e.setRecurrenceSignal(false);
        e.setRootCauseTool(null);
        e.setKnowledgeGapFlag(false);
        e.setKnowledgeGapDescription(null);
        return e;
    }
}
