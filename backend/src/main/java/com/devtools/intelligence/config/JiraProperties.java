package com.devtools.intelligence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the 'jira.*' properties from application.yml.
 * Centralises all Jira connection details in one place —
 * base URL, credentials, and the project key the agent monitors.
 */
@Configuration
@ConfigurationProperties(prefix = "jira")
@Data
public class JiraProperties {

    /** e.g. https://aitian888.atlassian.net */
    private String baseUrl;

    /** Email address of the account that owns the API token */
    private String email;

    /** Jira API token — paste your new token here after revoking the old one */
    private String apiToken;

    /** Project key the agent listens to — e.g. AITIA1 */
    private String projectKey;

    /**
     * Minimum cosine similarity score (0-1) required before the agent
     * posts a comment. Below this threshold the agent stays silent.
     * 0.75 is a sensible default — tune based on observed answer quality.
     */
    private double similarityThreshold = 0.75;
}
