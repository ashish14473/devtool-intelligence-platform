package com.devtools.intelligence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents the combined payload from two Jira API calls per ticket:
 *
 *   GET /rest/api/3/issue/{key}?fields=summary,description,...
 *   GET /rest/api/3/issue/{key}/comment
 *
 * Each JSON file in data/sonarqube/ contains both responses nested
 * under "issue" and "comments" keys. This model mirrors that structure
 * exactly so Jackson can deserialise it without custom logic.
 *
 * Fields are annotated with @JsonIgnoreProperties(ignoreUnknown = true)
 * at every level — real Jira API responses contain many more fields
 * than we care about, and we don't want deserialization to fail when
 * unknown fields are present.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraTicketJson {

    @JsonProperty("issue")
    private IssueWrapper issue;

    @JsonProperty("comments")
    private CommentsWrapper comments;

    // ── Issue wrapper ─────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueWrapper {

        @JsonProperty("key")
        private String key;

        @JsonProperty("fields")
        private Fields fields;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {

        @JsonProperty("summary")
        private String summary;

        /**
         * Plain string — matches the agreed description API format:
         * { "key": "...", "fields": { "description": "plain text" } }
         */
        @JsonProperty("description")
        private String description;

        @JsonProperty("status")
        private String status;

        @JsonProperty("priority")
        private String priority;

        @JsonProperty("assignee")
        private Author assignee;

        @JsonProperty("reporter")
        private Author reporter;

        @JsonProperty("labels")
        private List<String> labels;

        @JsonProperty("created")
        private String created;

        @JsonProperty("resolutiondate")
        private String resolutiondate;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {

        @JsonProperty("displayName")
        private String displayName;

        @JsonProperty("emailAddress")
        private String emailAddress;
    }

    // ── Comments wrapper ──────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommentsWrapper {

        @JsonProperty("comments")
        private List<Comment> comments;

        @JsonProperty("total")
        private int total;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Comment {

        @JsonProperty("id")
        private String id;

        @JsonProperty("author")
        private Author author;

        /**
         * Minimal ADF body — shape agreed for sample data:
         * { "type": "doc", "version": 1, "content": [
         *     { "type": "paragraph", "content": [
         *         { "type": "text", "text": "..." }
         *     ]}
         * ]}
         * AdfExtractor pulls the plain text from this structure.
         */
        @JsonProperty("body")
        private AdfNode body;

        @JsonProperty("created")
        private String created;

        @JsonProperty("updated")
        private String updated;
    }

    // ── ADF node (Atlassian Document Format — minimal shape) ──────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AdfNode {

        @JsonProperty("type")
        private String type;

        @JsonProperty("version")
        private Integer version;

        @JsonProperty("text")
        private String text;

        @JsonProperty("content")
        private List<AdfNode> content;
    }
}
