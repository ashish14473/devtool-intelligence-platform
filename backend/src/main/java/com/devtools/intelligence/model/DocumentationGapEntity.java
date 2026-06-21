package com.devtools.intelligence.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "documentation_gaps")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentationGapEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "suggested_title", nullable = false, columnDefinition = "TEXT") private String suggestedTitle;
    @Column(name = "suggested_outline", nullable = false, columnDefinition = "TEXT") private String suggestedOutline;
    @Column(name = "triggering_ticket_ids", nullable = false, columnDefinition = "TEXT") private String triggeringTicketIds;
    @Column(name = "tickets_prevented", nullable = false) private Integer ticketsPrevented;
    @Column(name = "category", length = 50) private String category;
    @Column(name = "tool_name", length = 100) private String toolName;
    @Column(name = "priority_score") private Integer priorityScore;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void pre() { createdAt = LocalDateTime.now(); }
}
