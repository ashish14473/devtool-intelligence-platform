package com.devtools.intelligence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalised payload extracted from a Jira webhook event.
 * The raw Jira webhook JSON is parsed in AgentController and mapped
 * to this clean DTO before being passed to TicketAgentService.
 *
 * Raw Jira webhook shape (jira:issue_created event):
 * {
 *   "webhookEvent": "jira:issue_created",
 *   "issue": {
 *     "key": "AITIA1-5",
 *     "fields": {
 *       "summary": "...",
 *       "description": "...",  <- plain string in our setup
 *       "issuetype": { "name": "Bug" },
 *       "priority": { "name": "High" },
 *       "status": { "name": "Open" }
 *     }
 *   }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentWebhookPayload {

    private String issueKey;
    private String summary;
    private String description;
    private String issueType;
    private String priority;
    private String projectKey;
}
