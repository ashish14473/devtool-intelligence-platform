package com.devtools.intelligence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The output of the LLM enrichment step.
 * All fields are populated in a single LLM call — no extra API calls.
 *
 * Phase 1 fields: toolName, category, painPoint, summary, severity
 * Phase 2 analytics fields: resolutionType, sentimentScore,
 *   frustrationFlag, recurrenceSignal, rootCauseTool,
 *   knowledgeGapFlag, knowledgeGapDescription
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnrichedTicket {

    // ── Phase 1 — existing fields ─────────────────────────────────────────────

    @JsonProperty("toolName")
    private String toolName;

    @JsonProperty("category")
    private String category;

    @JsonProperty("painPoint")
    private String painPoint;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("severity")
    private String severity;

    // ── Phase 2 — analytics fields ────────────────────────────────────────────

    /**
     * How was the ticket actually resolved?
     * FIXED | WORKAROUND | UNANSWERED | ABANDONED
     */
    @JsonProperty("resolutionType")
    private String resolutionType;

    /**
     * Developer sentiment score based on comment thread tone.
     * 1 = very positive/satisfied, 5 = very frustrated/angry
     */
    @JsonProperty("sentimentScore")
    private Integer sentimentScore;

    /**
     * True if comment thread shows clear signs of developer frustration,
     * dissatisfaction, or if final comment suggests the issue is not
     * truly resolved from the developer's perspective.
     */
    @JsonProperty("frustrationFlag")
    private Boolean frustrationFlag;

    /**
     * True if comments suggest this problem is likely to recur —
     * e.g. workarounds, "this keeps happening", root cause not addressed.
     */
    @JsonProperty("recurrenceSignal")
    private Boolean recurrenceSignal;

    /**
     * The tool where the root cause actually lies, which may differ
     * from the tool the ticket was filed against. Null if same tool.
     * E.g. a Jenkins ticket whose root cause is Nexus credential expiry.
     */
    @JsonProperty("rootCauseTool")
    private String rootCauseTool;

    /**
     * True if this ticket represents a documentation or knowledge gap —
     * i.e. a proper article or guide would have prevented this ticket.
     */
    @JsonProperty("knowledgeGapFlag")
    private Boolean knowledgeGapFlag;

    /**
     * If knowledgeGapFlag is true, a one-sentence description of what
     * documentation article would prevent this class of ticket.
     */
    @JsonProperty("knowledgeGapDescription")
    private String knowledgeGapDescription;
}
