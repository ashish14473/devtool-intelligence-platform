package com.devtools.intelligence.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Represents a single Jira ticket as read from the CSV source.
 * Mirrors the fields we'd pull from the real Jira REST API
 * (see ticket_id, summary, description, comments, status, priority,
 * labels, created_date, resolved_date, tool_name).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JiraTicket {

    private String ticketId;
    private String summary;
    private String description;
    private String comments;
    private String status;
    private String priority;
    private String labels;
    private LocalDate createdDate;
    private LocalDate resolvedDate;
    private String toolName;

    public boolean isResolved() {
        return status != null &&
                (status.equalsIgnoreCase("Done")
                        || status.equalsIgnoreCase("Closed")
                        || status.equalsIgnoreCase("Resolved"));
    }
}
