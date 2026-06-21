package com.devtools.intelligence.dto;

import com.devtools.intelligence.model.TicketEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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
    private String resolutionType;
    private Integer sentimentScore;
    private Boolean frustrationFlag;
    private Boolean recurrenceSignal;
    private String rootCauseTool;
    private Boolean knowledgeGapFlag;
    private String knowledgeGapDescription;

    public static TicketDto from(TicketEntity e) {
        return new TicketDto(
                e.getTicketId(), e.getToolName(), e.getCategory(),
                e.getSeverity(), e.getPainPoint(), e.getSummary(),
                e.getStatus(), e.getPriority(), e.getCreatedDate(), e.getResolvedDate(),
                e.getResolutionType(), e.getSentimentScore(), e.getFrustrationFlag(),
                e.getRecurrenceSignal(), e.getRootCauseTool(),
                e.getKnowledgeGapFlag(), e.getKnowledgeGapDescription()
        );
    }
}
