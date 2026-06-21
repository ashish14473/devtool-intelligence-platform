package com.devtools.intelligence.service;

import com.devtools.intelligence.config.OpenAiProperties;
import com.devtools.intelligence.model.DocumentationGapEntity;
import com.devtools.intelligence.model.TicketEntity;
import com.devtools.intelligence.repository.DocumentationGapRepository;
import com.devtools.intelligence.repository.TicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Weekly knowledge gap detection service.
 *
 * Finds tickets where the same question has been asked independently
 * by different developers — a clear signal that documentation is missing.
 *
 * Strategy:
 * 1. Filter tickets flagged as knowledge_gap_flag=true during enrichment
 * 2. Group them by category and tool
 * 3. For groups with 2+ tickets: ask LLM to generate a documentation article
 *    title, outline, and estimated tickets it would have prevented
 * 4. Store suggestions in documentation_gaps table
 *
 * Runs every Sunday at 3am.
 */
@Service
@Slf4j
public class KnowledgeGapService {

    private final TicketRepository ticketRepository;
    private final DocumentationGapRepository documentationGapRepository;
    private final RestClient openAiRestClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public KnowledgeGapService(TicketRepository ticketRepository,
                                DocumentationGapRepository documentationGapRepository,
                                @Qualifier("openAiRestClient") RestClient openAiRestClient,
                                OpenAiProperties openAiProperties,
                                ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.documentationGapRepository = documentationGapRepository;
        this.openAiRestClient = openAiRestClient;
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
    }

    //@Scheduled(initialDelay = 120000, fixedDelay = 3600000) // Every Sunday at 3am
    public void detectKnowledgeGaps() {
        log.info("=== Knowledge gap detection job started ===");

        // Get all tickets flagged as potential knowledge gaps
        List<TicketEntity> gapTickets = ticketRepository.findByKnowledgeGapFlagTrue();

        if (gapTickets.isEmpty()) {
            log.info("No knowledge gap tickets found");
            return;
        }

        log.info("Found {} knowledge gap tickets, grouping...", gapTickets.size());

        // Group by tool + category
        Map<String, List<TicketEntity>> grouped = gapTickets.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getToolName() + "|" + t.getCategory()
                ));

        // Clear old gaps and regenerate
        documentationGapRepository.deleteAll();

        for (Map.Entry<String, List<TicketEntity>> entry : grouped.entrySet()) {
            List<TicketEntity> group = entry.getValue();
            String[] parts = entry.getKey().split("\\|");
            String toolName = parts[0];
            String category = parts.length > 1 ? parts[1] : "";

            // Only worth flagging if 2+ tickets ask the same thing
            if (group.size() < 2) continue;

            DocumentationGapInsight insight = generateGapInsight(group, toolName, category);
            if (insight == null) continue;

            List<String> ticketIds = group.stream()
                    .map(TicketEntity::getTicketId)
                    .collect(Collectors.toList());

            DocumentationGapEntity gap = DocumentationGapEntity.builder()
                    .suggestedTitle(insight.title())
                    .suggestedOutline(insight.outline())
                    .triggeringTicketIds(String.join(",", ticketIds))
                    .ticketsPrevented(group.size())
                    .category(category)
                    .toolName(toolName)
                    .priorityScore(group.size() * 10)  // simple score: more tickets = higher priority
                    .build();

            documentationGapRepository.save(gap);
            log.info("Saved knowledge gap: '{}' ({} tickets)", insight.title(), group.size());
        }

        log.info("=== Knowledge gap detection complete ===");
    }

    private DocumentationGapInsight generateGapInsight(List<TicketEntity> tickets,
                                                        String toolName, String category) {
        String ticketSummaries = tickets.stream()
                .map(t -> "- " + t.getPainPoint())
                .collect(Collectors.joining("\n"));

        String prompt = """
                Multiple developers independently asked the same question about %s (%s category). \
                This is a documentation gap. Return ONLY a JSON object:

                {
                  "title": "suggested wiki/documentation article title",
                  "outline": "3-5 bullet points describing what the article should cover"
                }

                Tickets that would have been prevented:
                %s
                """.formatted(toolName, category, ticketSummaries);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openAiProperties.getChatModel());
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        try {
            String raw = openAiRestClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode result = objectMapper.readTree(content);
            return new DocumentationGapInsight(
                    result.path("title").asText(""),
                    result.path("outline").asText("")
            );
        } catch (Exception e) {
            log.error("Gap insight generation failed: {}", e.getMessage());
            return null;
        }
    }

    record DocumentationGapInsight(String title, String outline) {}
}
