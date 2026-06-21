package com.devtools.intelligence.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "emerging_issues")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EmergingIssueEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "title", nullable = false, columnDefinition = "TEXT") private String title;
    @Column(name = "description", nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "ticket_ids", nullable = false, columnDefinition = "TEXT") private String ticketIds;
    @Column(name = "ticket_count", nullable = false) private Integer ticketCount;
    @Column(name = "tool_name", length = 100) private String toolName;
    @Column(name = "detected_date", nullable = false) private LocalDate detectedDate;
    @Column(name = "confidence", length = 10) private String confidence;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void pre() { createdAt = LocalDateTime.now(); }
}
