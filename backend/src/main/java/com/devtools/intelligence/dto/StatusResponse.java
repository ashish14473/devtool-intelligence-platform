package com.devtools.intelligence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for GET /api/status - lets the React UI show whether
 * ingestion has finished and how many tickets are searchable, instead
 * of the user wondering why their first question returns nothing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusResponse {

    private boolean ready;
    private int documentCount;
    private String message;
}
