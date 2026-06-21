package com.devtools.intelligence.service;

import com.devtools.intelligence.config.OpenAiProperties;
import com.devtools.intelligence.model.EmergingIssueEntity;
import com.devtools.intelligence.model.TicketEntity;
import com.devtools.intelligence.repository.EmergingIssueRepository;
import com.devtools.intelligence.repository.TicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Daily emerging issue detection service.
 *
 * Detects new problem patterns in the last 7 days that have no precedent
 * in historical tickets (30+ days old). These are the early warning signals
 * leadership needs before a new issue becomes a volume spike.
 *
 * Algorithm:
 * 1. Get recent tickets (last 7 days) from the DB
 * 2. For each recent ticket, search pgvector for similar historical tickets
 * 3. If a recent ticket's nearest neighbours are ALL also recent (< 7 days old)
 *    = no historical precedent = potential emerging issue
 * 4. Group emerging tickets by similarity
 * 5. If 2+ tickets cluster together with no history: LLM generates a summary
 * 6. Store in emerging_issues table
 *
 * Runs daily at 6am.
 */
@Service
@Slf4j
public class EmergingIssueService {

    private static final int LOOKBACK_DAYS = 7;
    private static final int HISTORY_DAYS = 30;
    private static final double SIMILARITY_THRESHOLD = 0.80;

    private final JdbcTemplate jdbc;
    private final TicketRepository ticketRepository;
    private final EmergingIssueRepository emergingIssueRepository;
    private final PgVectorStore pgVectorStore;
    private final RestClient openAiRestClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public EmergingIssueService(JdbcTemplate jdbc,
                                 TicketRepository ticketRepository,
                                 EmergingIssueRepository emergingIssueRepository,
                                 PgVectorStore pgVectorStore,
                                 @Qualifier("openAiRestClient") RestClient openAiRestClient,
                                 OpenAiProperties openAiProperties,
                                 ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.ticketRepository = ticketRepository;
        this.emergingIssueRepository = emergingIssueRepository;
        this.pgVectorStore = pgVectorStore;
        this.openAiRestClient = openAiRestClient;
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(initialDelay = 120000, fixedDelay = 3600000)  // Every day at 6am
    public void detectEmergingIssues() {
        log.info("=== Emerging issue detection started ===");

        LocalDate cutoff = LocalDate.now().minusDays(LOOKBACK_DAYS);
        LocalDate historyCutoff = LocalDate.now().minusDays(HISTORY_DAYS);

        // Get recent tickets
        List<TicketEntity> recentTickets = ticketRepository.findByCreatedDateAfter(cutoff);
        if (recentTickets.isEmpty()) {
            log.info("No recent tickets found, skipping");
            return;
        }

        log.info("Checking {} recent tickets for emerging patterns", recentTickets.size());

        // Get IDs of all recent tickets for the "no history" check
        Set<String> recentTicketIds = recentTickets.stream()
                .map(TicketEntity::getTicketId)
                .collect(Collectors.toSet());

        // Find tickets with no historical precedent
        List<TicketEntity> noHistoryTickets = new ArrayList<>();

        for (TicketEntity recent : recentTickets) {
            // Get the embedding for this ticket
            float[] embedding = getEmbedding(recent.getTicketId());
            if (embedding == null) continue;

            // Search for similar tickets
            List<PgVectorStore.ScoredDocument> similar =
                    pgVectorStore.search(embedding, 5, null);

            // Check if all similar tickets are also recent
            boolean hasHistoricalMatch = similar.stream()
                    .filter(m -> m.score() >= SIMILARITY_THRESHOLD)
                    .anyMatch(m -> !recentTicketIds.contains(m.document().getTicketId()));

            if (!hasHistoricalMatch) {
                log.debug("Ticket {} has no historical match — potential emerging issue",
                        recent.getTicketId());
                noHistoryTickets.add(recent);
            }
        }

        if (noHistoryTickets.size() < 2) {
            log.info("Not enough novel tickets to form emerging issue pattern");
            return;
        }

        // Group novel tickets by tool
        Map<String, List<TicketEntity>> byTool = noHistoryTickets.stream()
                .collect(Collectors.groupingBy(TicketEntity::getToolName));

        // Clear today's emerging issues and regenerate
        emergingIssueRepository.deleteByDetectedDate(LocalDate.now());

        for (Map.Entry<String, List<TicketEntity>> entry : byTool.entrySet()) {
            List<TicketEntity> group = entry.getValue();
            if (group.size() < 2) continue;

            String toolName = entry.getKey();
            EmergingIssueInsight insight = generateInsight(group, toolName);
            if (insight == null) continue;

            String confidence = group.size() >= 4 ? "HIGH"
                    : group.size() >= 2 ? "MEDIUM" : "LOW";

            EmergingIssueEntity issue = EmergingIssueEntity.builder()
                    .title(insight.title())
                    .description(insight.description())
                    .ticketIds(group.stream()
                            .map(TicketEntity::getTicketId)
                            .collect(Collectors.joining(",")))
                    .ticketCount(group.size())
                    .toolName(toolName)
                    .detectedDate(LocalDate.now())
                    .confidence(confidence)
                    .build();

            emergingIssueRepository.save(issue);
            log.info("Saved emerging issue: '{}' ({}, {} tickets)",
                    insight.title(), confidence, group.size());
        }

        log.info("=== Emerging issue detection complete ===");
    }

    private float[] getEmbedding(String ticketId) {
        try {
            return jdbc.queryForObject(
                    "SELECT embedding::float4[]::text FROM ticket_embeddings WHERE ticket_id = ?",
                    (rs, row) -> parseVector(rs.getString(1)),
                    ticketId
            );
        } catch (Exception e) {
            log.warn("Could not fetch embedding for ticket {}", ticketId);
            return null;
        }
    }

    private float[] parseVector(String vectorStr) {
        if (vectorStr == null) return null;
        String cleaned = vectorStr.replaceAll("[\\[\\]{}\\s]", "");
        String[] parts = cleaned.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = Float.parseFloat(parts[i].trim()); }
            catch (NumberFormatException e) { result[i] = 0f; }
        }
        return result;
    }

    private EmergingIssueInsight generateInsight(List<TicketEntity> tickets, String toolName) {
        String summaries = tickets.stream()
                .map(t -> "- " + t.getPainPoint())
                .collect(Collectors.joining("\n"));

        String prompt = """
                These developer support tickets for %s all appeared in the last 7 days \
                and have NO similar tickets in the previous 30 days — suggesting a NEW \
                emerging problem. Return ONLY a JSON object:

                {
                  "title": "concise emerging issue title (max 10 words)",
                  "description": "2-3 sentences: what the new pattern is, what likely triggered it, \
                and what leadership should know about it"
                }

                New tickets:
                %s
                """.formatted(toolName, summaries);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openAiProperties.getChatModel());
        body.put("temperature", 0.3);
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
            return new EmergingIssueInsight(
                    result.path("title").asText(""),
                    result.path("description").asText("")
            );
        } catch (Exception e) {
            log.error("Emerging issue insight generation failed: {}", e.getMessage());
            return null;
        }
    }

    record EmergingIssueInsight(String title, String description) {}
}
