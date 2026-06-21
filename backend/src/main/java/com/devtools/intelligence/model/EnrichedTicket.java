package com.devtools.intelligence.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The output of the LLM enrichment step. The LLM is prompted to return
 * exactly this JSON shape (see EnrichmentService for the prompt).
 *
 * This separation - raw ticket vs. enriched ticket - mirrors the real
 * pipeline design: enrichment is a distinct stage that can be re-run,
 * cached, or swapped to a different model without touching ingestion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrichedTicket {

    @JsonProperty("toolName")
    private String toolName;

    @JsonProperty("category")
    private String category; // auth | performance | config | integration | bug | docs | feature-request

    @JsonProperty("painPoint")
    private String painPoint; // one-sentence plain-language problem statement

    @JsonProperty("summary")
    private String summary; // 3-5 sentence factual summary: problem, root cause, fix, workaround

    @JsonProperty("severity")
    private String severity; // low | medium | high | critical
}
