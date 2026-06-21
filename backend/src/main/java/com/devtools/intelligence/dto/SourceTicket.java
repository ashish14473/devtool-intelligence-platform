package com.devtools.intelligence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One retrieved ticket shown as a citation alongside a chat answer.
 * The React UI renders these as small "source cards" under the
 * assistant's message - this is what gives the RAG response an
 * audit trail back to real tickets.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SourceTicket {

    private String ticketId;
    private String toolName;
    private String category;
    private String painPoint;
    private double similarityScore;
}
