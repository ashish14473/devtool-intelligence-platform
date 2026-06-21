package com.devtools.intelligence.controller;

import com.devtools.intelligence.repository.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serves aggregated analytics data for the frontend dashboard charts.
 * All queries are pure SQL GROUP BY — no LLM calls, fast and cacheable.
 */
@RestController
@RequestMapping("/api/analytics")
@Slf4j
public class AnalyticsController {

    private final TicketRepository ticketRepository;

    public AnalyticsController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    /** Ticket counts per tool — drives the horizontal bar chart. */
    @GetMapping("/by-tool")
    public List<Map<String, Object>> byTool() {
        return ticketRepository.countByTool().stream()
                .map(p -> Map.<String, Object>of(
                        "tool", p.getToolName(),
                        "count", p.getCount()))
                .collect(Collectors.toList());
    }

    /** Ticket counts per category — drives the category breakdown. */
    @GetMapping("/by-category")
    public List<Map<String, Object>> byCategory() {
        return ticketRepository.countByCategory().stream()
                .map(p -> Map.<String, Object>of(
                        "category", p.getCategory(),
                        "count", p.getCount()))
                .collect(Collectors.toList());
    }

    /** Ticket counts per severity — drives the severity badge row. */
    @GetMapping("/by-severity")
    public List<Map<String, Object>> bySeverity() {
        return ticketRepository.countBySeverity().stream()
                .map(p -> Map.<String, Object>of(
                        "severity", p.getSeverity(),
                        "count", p.getCount()))
                .collect(Collectors.toList());
    }

    /**
     * Heatmap data: ticket counts per (tool, category) pair.
     * The frontend turns this flat list into a 2D colour matrix.
     */
    @GetMapping("/heatmap")
    public List<Map<String, Object>> heatmap() {
        return ticketRepository.countByToolAndCategory().stream()
                .map(p -> Map.<String, Object>of(
                        "tool", p.getToolName(),
                        "category", p.getCategory(),
                        "count", p.getCount()))
                .collect(Collectors.toList());
    }
}
