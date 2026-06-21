package com.devtools.intelligence.service;

import com.devtools.intelligence.model.JiraTicket;
import com.devtools.intelligence.model.JiraTicketJson;
import com.devtools.intelligence.model.JiraTicketJson.Fields;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads all .json ticket files from the configured data directory and
 * maps them into JiraTicket objects — the same type the rest of the
 * pipeline (enrichment, embedding, orchestrator) already understands.
 *
 * This replaces CsvIngestionService as the data source while keeping
 * the downstream pipeline unchanged. The JSON files mirror real Jira
 * API responses: one file = one ticket, with both the issue fields and
 * the comment thread inside.
 *
 * Tool name is derived from the ticket key prefix (SQ = SonarQube,
 * GL = GitLab) since the JSON files don't have an explicit tool field.
 */
@Service
@Slf4j
public class JsonIngestionService {

    private final ObjectMapper objectMapper;
    private final CommentPreprocessor commentPreprocessor;
    private final String dataLocation;

    private static final DateTimeFormatter JIRA_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public JsonIngestionService(ObjectMapper objectMapper,
                                 CommentPreprocessor commentPreprocessor,
                                 @Value("${ingestion.json-data-path}") String dataLocation) {
        this.objectMapper = objectMapper;
        this.commentPreprocessor = commentPreprocessor;
        this.dataLocation = dataLocation;
    }

    /**
     * Loads all .json files from the configured directory, parses each one,
     * and returns the list of JiraTicket objects ready for the pipeline.
     */
    public List<JiraTicket> loadTickets() {
        List<JiraTicket> tickets = new ArrayList<>();

        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(dataLocation + "/*.json");

            log.info("Found {} JSON ticket files in {}", resources.length, dataLocation);

            for (Resource resource : resources) {
                try {
                    JiraTicketJson raw = objectMapper.readValue(
                            resource.getInputStream(), JiraTicketJson.class);

                    JiraTicket ticket = map(raw);
                    if (ticket != null) {
                        tickets.add(ticket);
                        log.debug("Loaded ticket {} from {}", ticket.getTicketId(), resource.getFilename());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse ticket file {}: {}", resource.getFilename(), e.getMessage());
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON ticket files from " + dataLocation, e);
        }

        log.info("Loaded {} tickets from JSON files", tickets.size());
        return tickets;
    }

    /**
     * Maps a raw JiraTicketJson (parsed from file) into a JiraTicket.
     * Comment thread is preprocessed — bot comments filtered, ADF unwrapped,
     * then consolidated into a single string for the enrichment prompt.
     */
    private JiraTicket map(JiraTicketJson raw) {
        if (raw == null || raw.getIssue() == null) return null;

        JiraTicketJson.IssueWrapper issue = raw.getIssue();
        Fields fields = issue.getFields();

        if (fields == null) {
            log.warn("Ticket {} has no fields, skipping", issue.getKey());
            return null;
        }

        // Preprocess comment thread — filter bots, extract ADF text, consolidate
        String consolidatedComments = "";
        if (raw.getComments() != null && raw.getComments().getComments() != null) {
            consolidatedComments = commentPreprocessor.process(raw.getComments().getComments());
        }

        // Tool name inferred from ticket key prefix
        String toolName = inferToolName(issue.getKey());

        // Labels as comma-separated string (same format as the old CSV)
        String labels = fields.getLabels() != null
                ? String.join(",", fields.getLabels())
                : "";

        return new JiraTicket(
                issue.getKey(),
                fields.getSummary() != null ? fields.getSummary() : "",
                fields.getDescription() != null ? fields.getDescription() : "",
                consolidatedComments,
                fields.getStatus() != null ? fields.getStatus() : "",
                fields.getPriority() != null ? fields.getPriority() : "",
                labels,
                parseDate(fields.getCreated()),
                parseDate(fields.getResolutiondate()),
                toolName
        );
    }

    /**
     * Derives a human-readable tool name from the Jira key prefix.
     * SQ-xxxx = SonarQube, GL-xxxx = GitLab.
     * Extend this map as new tools are added.
     */
    private String inferToolName(String key) {
        if (key == null) return "Unknown";
        String prefix = key.split("-")[0].toUpperCase();
        return switch (prefix) {
            case "SQ" -> "SonarQube";
            case "GL" -> "GitLab";
            case "CICD" -> "CI/CD Pipeline";
            case "IDE" -> "IDE Plugin";
            case "CR" -> "Code Review Tool";
            case "ART" -> "Artifact Registry";
            case "SEC" -> "Secrets Manager";
            case "JK" -> "Jenkins";
            case "CF" -> "Confluence";
            case "NX" -> "Nexus";
            default -> prefix;
        };
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            // Jira timestamps: "2025-10-05T09:15:00.000+0000"
            return LocalDate.parse(value, JIRA_DATE_FORMAT);
        } catch (Exception e) {
            try {
                // Fallback: plain date "2025-10-05"
                return LocalDate.parse(value.substring(0, 10));
            } catch (Exception ex) {
                log.warn("Could not parse date '{}', leaving null", value);
                return null;
            }
        }
    }
}
