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

/**
 * Orchestrates the full ingestion pipeline on startup:
 *
 *   JSON files -> parse + preprocess -> LLM enrich -> SQL persist -> embed -> pgvector
 *
 * Data source switched from CSV to JSON (Jira API response format).
 * Everything downstream — enrichment, embedding, SQL storage, vector storage —
 * is unchanged because they all consume JiraTicket, which JsonIngestionService
 * still produces.
 *
 * Idempotent: skips tickets already in the DB, so restarting the app
 * does not re-process or re-bill for tickets already indexed.
 */
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
        log.info("=== Starting ingestion pipeline (JSON source) ===");

        List<JiraTicket> tickets = jsonIngestionService.loadTickets();
        log.info("Step 1/3: loaded {} tickets from JSON files", tickets.size());

        int newlyProcessed = 0, skippedUnresolved = 0, skippedExisting = 0, failed = 0;

        for (JiraTicket ticket : tickets) {

            // Only resolved tickets have a confirmed answer worth indexing
            if (!ticket.isResolved()) {
                log.debug("Skipping unresolved ticket {} (status: {})",
                        ticket.getTicketId(), ticket.getStatus());
                skippedUnresolved++;
                continue;
            }

            // Idempotency — skip if already persisted from a previous run
            if (ticketRepository.existsByTicketId(ticket.getTicketId())) {
                log.debug("Ticket {} already indexed, skipping", ticket.getTicketId());
                skippedExisting++;
                continue;
            }

            try {
                // Step 2: LLM enrichment — classify, summarise, extract pain point
                EnrichedTicket enriched = enrichmentService.enrich(ticket);
                log.info("Enriched {} -> tool={}, category={}, severity={}",
                        ticket.getTicketId(), enriched.getToolName(),
                        enriched.getCategory(), enriched.getSeverity());

                // Embed enriched summary + original title together
                String textToEmbed = enriched.getSummary() + "\n" + ticket.getSummary();

                // Step 3a: persist enriched fields to SQL tickets table
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
                        .build();
                ticketRepository.save(entity);

                // Step 3b: embed and persist to pgvector
                float[] vector = embeddingService.embed(textToEmbed);
                pgVectorStore.save(ticket.getTicketId(), vector);

                newlyProcessed++;

            } catch (Exception e) {
                log.error("Failed to process ticket {}: {}", ticket.getTicketId(), e.getMessage());
                failed++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("=== Ingestion complete in {}ms ===", elapsed);
        log.info("Results: {} new, {} skipped (existing), {} skipped (unresolved), {} failed",
                newlyProcessed, skippedExisting, skippedUnresolved, failed);
        log.info("Total in pgvector: {}", pgVectorStore.count());
    }
}
