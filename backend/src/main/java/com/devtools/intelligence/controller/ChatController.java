package com.devtools.intelligence.controller;

import com.devtools.intelligence.dto.ChatRequest;
import com.devtools.intelligence.dto.ChatResponse;
import com.devtools.intelligence.service.ChatService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Exposes the knowledge-base chat as a single REST endpoint.
 * This is what the React frontend calls for every message the
 * user sends.
 */
@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        log.info("Chat question received: '{}' (toolFilter={})",
                request.getQuestion(), request.getToolFilter());
        return chatService.answer(request.getQuestion(), request.getToolFilter());
    }
}
