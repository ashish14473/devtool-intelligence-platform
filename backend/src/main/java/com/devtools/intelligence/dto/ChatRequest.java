package com.devtools.intelligence.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Incoming request body for POST /api/chat.
 * toolFilter is optional - null or blank means "search across all tools".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "question must not be blank")
    private String question;

    private String toolFilter;
}
