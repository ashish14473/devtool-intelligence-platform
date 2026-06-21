package com.devtools.intelligence.service;

import com.devtools.intelligence.config.JiraProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Posts a comment on a Jira ticket via the Jira Cloud REST API v3.
 *
 * POST /rest/api/3/issue/{issueKey}/comment
 *
 * Comment body uses minimal ADF (same format as our sample data) —
 * Jira Cloud's v3 API requires ADF for comment bodies, not plain text.
 *
 * The comment is clearly labelled as AI-generated so developers know
 * the source and treat it as a suggestion rather than a definitive answer.
 */
@Service
@Slf4j
public class JiraCommentService {

    private final RestClient jiraRestClient;
    private final JiraProperties jiraProperties;
    private final ObjectMapper objectMapper;

    public JiraCommentService(@Qualifier("jiraRestClient") RestClient jiraRestClient,
                               JiraProperties jiraProperties,
                               ObjectMapper objectMapper) {
        this.jiraRestClient = jiraRestClient;
        this.jiraProperties = jiraProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Posts an AI-generated suggested answer as a comment on the given ticket.
     *
     * @param issueKey  e.g. "AITIA1-42"
     * @param answer    the LLM-generated answer text
     * @param sources   list of source ticket IDs that grounded the answer
     */
    public void postSuggestedAnswer(String issueKey, String answer, List<String> sources) {
        String commentBody = buildCommentText(answer, sources);
        Map<String, Object> adfBody = buildAdfBody(commentBody);

        try {
            String response = jiraRestClient.post()
                    .uri("/rest/api/3/issue/{issueKey}/comment", issueKey)
                    .body(Map.of("body", adfBody))
                    .retrieve()
                    .body(String.class);

            log.info("Posted AI comment on ticket {}", issueKey);
            log.debug("Jira comment response: {}", response);

        } catch (Exception e) {
            log.error("Failed to post comment on ticket {}: {}", issueKey, e.getMessage());
            throw new RuntimeException("Jira comment post failed for " + issueKey, e);
        }
    }

    /**
     * Builds the plain-text comment content — AI header + answer + sources footer.
     */
    private String buildCommentText(String answer, List<String> sources) {
        StringBuilder sb = new StringBuilder();

        sb.append("🤖 AI Suggested Answer\n\n");
        sb.append(answer);
        sb.append("\n\n");

        if (!sources.isEmpty()) {
            sb.append("---\n");
            sb.append("Based on similar resolved tickets: ");
            sb.append(String.join(", ", sources));
            sb.append("\n");
        }

        sb.append("\n");
        sb.append("This is an automated suggestion from the Developer Tools Knowledge Base. ");
        sb.append("Please verify before applying — a human engineer will follow up if needed.");

        return sb.toString();
    }

    /**
     * Wraps plain text into minimal ADF format required by Jira Cloud v3 API.
     * Splits on newlines to create separate paragraph nodes so the comment
     * renders with proper line breaks in the Jira UI.
     */
    private Map<String, Object> buildAdfBody(String text) {
        String[] lines = text.split("\n");

        List<Map<String, Object>> paragraphs = java.util.Arrays.stream(lines)
                .map(line -> {
                    if (line.isBlank()) {
                        // Empty paragraph for line spacing
                        return Map.<String, Object>of(
                                "type", "paragraph",
                                "content", List.of()
                        );
                    }
                    return Map.<String, Object>of(
                            "type", "paragraph",
                            "content", List.of(
                                    Map.of("type", "text", "text", line)
                            )
                    );
                })
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", "doc");
        doc.put("version", 1);
        doc.put("content", paragraphs);
        return doc;
    }
}
