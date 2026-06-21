package com.devtools.intelligence.controller;

import com.devtools.intelligence.dto.TicketDto;
import com.devtools.intelligence.model.TicketEntity;
import com.devtools.intelligence.repository.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Ticket browser endpoints — the SQL-backed side of the knowledge base.
 * Supports listing all tickets, filtering by tool or category, and
 * fetching a single ticket for the detail view.
 */
@RestController
@RequestMapping("/api/tickets")
@Slf4j
public class TicketController {

    private final TicketRepository ticketRepository;

    public TicketController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /**
     * List all tickets, optionally filtered by tool or category.
     * Returns TicketDto projections — not the full entity with internals.
     */
    @GetMapping
    public List<TicketDto> list(
            @RequestParam(required = false) String tool,
            @RequestParam(required = false) String category) {

        List<TicketEntity> entities;

        if (tool != null && !tool.isBlank()) {
            entities = ticketRepository.findByToolNameOrderByCreatedDateDesc(tool);
        } else if (category != null && !category.isBlank()) {
            entities = ticketRepository.findByCategoryOrderByCreatedDateDesc(category);
        } else {
            entities = ticketRepository.findAllByOrderByCreatedDateDesc();
        }

        return entities.stream()
                .map(TicketDto::from)
                .collect(Collectors.toList());
    }

    /** Fetch a single ticket by its Jira ticket ID (e.g. CICD-1421). */
    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketDto> get(@PathVariable String ticketId) {
        return ticketRepository.findAll().stream()
                .filter(t -> t.getTicketId().equals(ticketId))
                .findFirst()
                .map(TicketDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
