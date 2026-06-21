package com.devtools.intelligence.controller;

import com.devtools.intelligence.dto.StatusResponse;
import com.devtools.intelligence.repository.TicketRepository;
import com.devtools.intelligence.service.PgVectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class StatusController {

    private final TicketRepository ticketRepository;
    private final PgVectorStore pgVectorStore;

    public StatusController(TicketRepository ticketRepository, PgVectorStore pgVectorStore) {
        this.ticketRepository = ticketRepository;
        this.pgVectorStore = pgVectorStore;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        long count = pgVectorStore.count();
        boolean ready = count > 0;
        return new StatusResponse(ready, (int) count,
                ready ? count + " tickets indexed in pgvector"
                      : "Ingestion in progress — check backend logs");
    }

    @GetMapping("/tools")
    public List<String> tools() {
        return ticketRepository.findAllByOrderByCreatedDateDesc().stream()
                .map(t -> t.getToolName())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
