package com.devtools.intelligence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response body for POST /api/chat: the LLM-generated answer plus
 * the list of source tickets it was grounded in.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String answer;
    private List<SourceTicket> sources;
}
