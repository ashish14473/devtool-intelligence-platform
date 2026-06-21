package com.devtools.intelligence.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name = "pain_clusters")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PainClusterEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "cluster_label", nullable = false, columnDefinition = "TEXT") private String clusterLabel;
    @Column(name = "root_cause", nullable = false, columnDefinition = "TEXT") private String rootCause;
    @Column(name = "ticket_ids", nullable = false, columnDefinition = "TEXT") private String ticketIds;
    @Column(name = "ticket_count", nullable = false) private Integer ticketCount;
    @Column(name = "tools_affected", columnDefinition = "TEXT") private String toolsAffected;
    @Column(name = "first_seen") private LocalDate firstSeen;
    @Column(name = "last_seen") private LocalDate lastSeen;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void pre() { createdAt = LocalDateTime.now(); }
}
