package com.devtools.intelligence.controller;

import com.devtools.intelligence.config.JiraProperties;
import com.devtools.intelligence.dto.AgentWebhookPayload;
import com.devtools.intelligence.service.TicketAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Receives Jira webhook events and triggers the RAG agent.
 *
 * Jira webhook setup (do this in your Jira project):
 *   Project Settings > Automation > New rule > "Issue created" trigger
 *   OR
 *   Jira Settings (admin) > System > Webhooks > Create webhook
 *     URL: https://your-backend-url/api/agent/webhook
 *     Events: Issue > created
 *     Filter: project = AITIA1
 *
 * For local development, use ngrok to expose your local backend:
 *   ngrok http 8080
 *   Then use the ngrok URL in the Jira webhook config.
 *
 * The agent runs asynchronously — the webhook returns 200 immediately
 * while the RAG pipeline runs in the background. This is important
 * because Jira webhooks time out after 10 seconds and LLM calls can
 * take longer than that.
 */
@RestController
@RequestMapping("/api/agent")
@Slf4j
public class AgentController {

    private final TicketAgentService ticketAgentService;
    private final JiraProperties jiraProperties;
    private final ObjectMapper objectMapper;

    public AgentController(TicketAgentService ticketAgentService,
                            JiraProperties jiraProperties,
                            ObjectMapper objectMapper) {
        this.ticketAgentService = ticketAgentService;
        this.jiraProperties = jiraProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Jira webhook endpoint. Jira sends a POST with JSON body on every
     * configured event. We parse just the fields we need and hand off
     * to the agent service.
     *
     * Returns 200 immediately — agent processing is async so Jira
     * doesn't time out waiting for the LLM call to complete.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestBody String rawPayload) {

        log.debug("Webhook received, raw payload length: {}", rawPayload.length());

        try {
            JsonNode root = objectMapper.readTree(rawPayload);

            String webhookEvent = root.path("webhookEvent").asText();
            log.info("Webhook event: {}", webhookEvent);

            // Only process ticket creation events
            if (!"jira:issue_created".equals(webhookEvent)) {
                log.debug("Ignoring non-creation event: {}", webhookEvent);
                return ResponseEntity.ok(Map.of("status", "ignored", "reason", "not a creation event"));
            }

            JsonNode issue = root.path("issue");
            if (issue.isMissingNode()) {
                log.warn("Webhook payload missing 'issue' node");
                return ResponseEntity.ok(Map.of("status", "ignored", "reason", "missing issue node"));
            }

            String issueKey = issue.path("key").asText();
            JsonNode fields = issue.path("fields");

            // Only process tickets from the configured project
            String projectKey = issueKey.contains("-")
                    ? issueKey.split("-")[0]
                    : "";

            if (!jiraProperties.getProjectKey().equalsIgnoreCase(projectKey)) {
                log.info("Ignoring ticket {} — not in monitored project {}",
                        issueKey, jiraProperties.getProjectKey());
                return ResponseEntity.ok(Map.of("status", "ignored", "reason", "wrong project"));
            }

            // Extract description — plain string in our Jira setup
            String description = extractDescription(fields.path("description"));

            AgentWebhookPayload payload = new AgentWebhookPayload(
                    issueKey,
                    fields.path("summary").asText(""),
                    description,
                    fields.path("issuetype").path("name").asText(""),
                    fields.path("priority").path("name").asText(""),
                    projectKey
            );

            log.info("Agent triggered for new ticket: {} - {}", issueKey, payload.getSummary());

            // Run agent asynchronously — return 200 to Jira immediately
            Thread.ofVirtual().start(() -> {
                try {
                    ticketAgentService.handleNewTicket(payload);
                } catch (Exception e) {
                    log.error("Agent processing failed for ticket {}: {}",
                            payload.getIssueKey(), e.getMessage());
                }
            });

            return ResponseEntity.ok(Map.of("status", "accepted", "ticket", issueKey));

        } catch (Exception e) {
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            // Still return 200 — returning 4xx/5xx would cause Jira to retry
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Manual trigger endpoint — useful for testing the agent without
     * needing a real Jira webhook. POST to this with a ticket key and
     * the agent will process it as if the webhook fired.
     *
     * Example: POST /api/agent/trigger
     * Body: { "issueKey": "AITIA1-5", "summary": "...", "description": "..." }
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> manualTrigger(
            @RequestBody AgentWebhookPayload payload) {

        log.info("Manual agent trigger for ticket: {}", payload.getIssueKey());

        Thread.ofVirtual().start(() -> {
            try {
                ticketAgentService.handleNewTicket(payload);
            } catch (Exception e) {
                log.error("Manual agent trigger failed for {}: {}",
                        payload.getIssueKey(), e.getMessage());
            }
        });

        return ResponseEntity.ok(Map.of(
                "status", "triggered",
                "ticket", payload.getIssueKey(),
                "message", "Agent processing started asynchronously"
        ));
    }

    /**
     * Handles both plain string descriptions and nested ADF descriptions.
     * Jira Cloud v3 returns description as ADF JSON, but our setup
     * normalises it to plain string. This handles both gracefully.
     */
    private String extractDescription(JsonNode descNode) {
        if (descNode.isMissingNode() || descNode.isNull()) {
            return "";
        }
        // Plain string description (our normalised format)
        if (descNode.isTextual()) {
            return descNode.asText();
        }
        // ADF format — extract text from content nodes recursively
        if (descNode.isObject()) {
            return extractAdfText(descNode);
        }
        return descNode.asText("");
    }

    private String extractAdfText(JsonNode node) {
        StringBuilder sb = new StringBuilder();
        if (node.has("text")) {
            sb.append(node.path("text").asText());
        }
        JsonNode content = node.path("content");
        if (content.isArray()) {
            content.forEach(child -> sb.append(extractAdfText(child)));
        }
        return sb.toString();
    }
}
