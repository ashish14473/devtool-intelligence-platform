package com.devtools.intelligence.service;

import com.devtools.intelligence.model.EnrichedTicket;
import com.devtools.intelligence.model.JiraTicket;
import com.devtools.intelligence.model.TicketEntity;
import com.devtools.intelligence.repository.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class IngestionOrchestrator {

    private final JsonIngestionService jsonIngestionService;
    private final EnrichmentService enrichmentService;
    private final EmbeddingService embeddingService;
    private final TicketRepository ticketRepository;
    private final PgVectorStore pgVectorStore;

    public IngestionOrchestrator(JsonIngestionService jsonIngestionService,
                                  EnrichmentService enrichmentService,
                                  EmbeddingService embeddingService,
                                  TicketRepository ticketRepository,
                                  PgVectorStore pgVectorStore) {
        this.jsonIngestionService = jsonIngestionService;
        this.enrichmentService = enrichmentService;
        this.embeddingService = embeddingService;
        this.ticketRepository = ticketRepository;
        this.pgVectorStore = pgVectorStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runIngestionPipeline() {
        long start = System.currentTimeMillis();
        log.info("=== Starting ingestion pipeline (JSON source, {} analytics fields) ===", 7);

        List<JiraTicket> tickets = jsonIngestionService.loadTickets();
        log.info("Loaded {} tickets from JSON files", tickets.size());

        int processed = 0, skippedUnresolved = 0, skippedExisting = 0, failed = 0;

        for (JiraTicket ticket : tickets) {
            if (!ticket.isResolved()) {
                skippedUnresolved++;
                continue;
            }
            if (ticketRepository.existsByTicketId(ticket.getTicketId())) {
                skippedExisting++;
                continue;
            }
            try {
                EnrichedTicket enriched = enrichmentService.enrich(ticket);
                log.info("Enriched {} -> tool={}, cat={}, sev={}, resType={}, sentiment={}, gap={}",
                        ticket.getTicketId(), enriched.getToolName(), enriched.getCategory(),
                        enriched.getSeverity(), enriched.getResolutionType(),
                        enriched.getSentimentScore(), enriched.getKnowledgeGapFlag());

                String textToEmbed = enriched.getSummary() + "\n" + ticket.getSummary();

                TicketEntity entity = TicketEntity.builder()
                        .ticketId(ticket.getTicketId())
                        .toolName(enriched.getToolName())
                        .category(enriched.getCategory())
                        .severity(enriched.getSeverity())
                        .painPoint(enriched.getPainPoint())
                        .summary(enriched.getSummary())
                        .status(ticket.getStatus())
                        .priority(ticket.getPriority())
                        .createdDate(ticket.getCreatedDate())
                        .resolvedDate(ticket.getResolvedDate())
                        .embeddedText(textToEmbed)
                        .resolutionType(enriched.getResolutionType())
                        .sentimentScore(enriched.getSentimentScore())
                        .frustrationFlag(Boolean.TRUE.equals(enriched.getFrustrationFlag()))
                        .recurrenceSignal(Boolean.TRUE.equals(enriched.getRecurrenceSignal()))
                        .rootCauseTool(enriched.getRootCauseTool())
                        .knowledgeGapFlag(Boolean.TRUE.equals(enriched.getKnowledgeGapFlag()))
                        .knowledgeGapDescription(enriched.getKnowledgeGapDescription())
                        .build();

                ticketRepository.save(entity);

                float[] vector = embeddingService.embed(textToEmbed);
                pgVectorStore.save(ticket.getTicketId(), vector);

                processed++;

            } catch (Exception e) {
                log.error("Failed to process ticket {}: {}", ticket.getTicketId(), e.getMessage());
                failed++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("=== Ingestion complete in {}ms: {} processed, {} skipped (existing), {} skipped (unresolved), {} failed ===",
                elapsed, processed, skippedExisting, skippedUnresolved, failed);
        log.info("Total in pgvector: {}", pgVectorStore.count());
    }
}
