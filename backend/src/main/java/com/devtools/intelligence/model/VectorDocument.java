package com.devtools.intelligence.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single entry in the in-memory vector store: the embedded text,
 * its vector, and enough metadata to filter/display search results
 * without re-fetching the original ticket.
 *
 * This is the in-memory equivalent of a row in a pgvector table -
 * same fields, same purpose, just held in a List<VectorDocument>
 * instead of PostgreSQL.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorDocument {

    private String ticketId;
    private String embeddedText;     // the exact text that was embedded (summary + title)
    private float[] embedding;       // the embedding vector from OpenAI

    // Metadata - lets the chat UI show provenance and supports filtering
    private String toolName;
    private String category;
    private String severity;
    private String painPoint;
    private boolean resolved;
    private String createdDate;
}
