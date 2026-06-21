package com.devtools.intelligence.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JPA entity for the 'tickets' table — the structured SQL side of the
 * knowledge base. Stores enriched ticket fields so they can be queried,
 * filtered, and aggregated independently from the vector search.
 *
 * Kept separate from VectorDocument intentionally: this table is for
 * SQL analytics (GROUP BY tool_name, counts, filters), while the
 * ticket_embeddings table is for semantic similarity search. They
 * share ticket_id as a natural join key.
 */
@Entity
@Table(name = "tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, unique = true, length = 50)
    private String ticketId;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    @Column(name = "pain_point", nullable = false, columnDefinition = "TEXT")
    private String painPoint;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "priority", length = 20)
    private String priority;

    @Column(name = "created_date")
    private LocalDate createdDate;

    @Column(name = "resolved_date")
    private LocalDate resolvedDate;

    @Column(name = "embedded_text", columnDefinition = "TEXT")
    private String embeddedText;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
