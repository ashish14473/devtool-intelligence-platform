package com.devtools.intelligence.dto;

import com.devtools.intelligence.model.TicketEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Read-only DTO for ticket browser and detail views.
 * Constructed from TicketEntity via the static factory — keeps the
 * entity's JPA internals (id, createdAt) out of the API response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketDto {

    private String ticketId;
    private String toolName;
    private String category;
    private String severity;
    private String painPoint;
    private String summary;
    private String status;
    private String priority;
    private LocalDate createdDate;
    private LocalDate resolvedDate;

    public static TicketDto from(TicketEntity e) {
        return new TicketDto(
                e.getTicketId(),
                e.getToolName(),
                e.getCategory(),
                e.getSeverity(),
                e.getPainPoint(),
                e.getSummary(),
                e.getStatus(),
                e.getPriority(),
                e.getCreatedDate(),
                e.getResolvedDate()
        );
    }
}
