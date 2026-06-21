package com.devtools.intelligence.controller;

import com.devtools.intelligence.dto.TicketDto;
import com.devtools.intelligence.model.TicketEntity;
import com.devtools.intelligence.repository.TicketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@Slf4j
public class AnalyticsController {

    private final TicketRepository ticketRepository;

    public AnalyticsController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    // ── Existing endpoints ────────────────────────────────────────────────────

    @GetMapping("/by-tool")
    public List<Map<String, Object>> byTool() {
        return ticketRepository.countByTool().stream()
                .map(p -> Map.<String, Object>of("tool", p.getToolName(), "count", p.getCount()))
                .collect(Collectors.toList());
    }

    @GetMapping("/by-category")
    public List<Map<String, Object>> byCategory() {
        return ticketRepository.countByCategory().stream()
                .map(p -> Map.<String, Object>of("category", p.getCategory(), "count", p.getCount()))
                .collect(Collectors.toList());
    }

    @GetMapping("/by-severity")
    public List<Map<String, Object>> bySeverity() {
        return ticketRepository.countBySeverity().stream()
                .map(p -> Map.<String, Object>of("severity", p.getSeverity(), "count", p.getCount()))
                .collect(Collectors.toList());
    }

    @GetMapping("/heatmap")
    public List<Map<String, Object>> heatmap() {
        return ticketRepository.countByToolAndCategory().stream()
                .map(p -> Map.<String, Object>of("tool", p.getToolName(), "category", p.getCategory(), "count", p.getCount()))
                .collect(Collectors.toList());
    }

    // ── Resolution quality ────────────────────────────────────────────────────

    @GetMapping("/resolution-quality")
    public Map<String, Object> resolutionQuality() {
        List<Map<String, Object>> byTool = ticketRepository.countByToolAndResolutionType().stream()
                .map(p -> Map.<String, Object>of(
                        "tool", p.getToolName(),
                        "resolutionType", p.getResolutionType(),
                        "count", p.getCount()))
                .collect(Collectors.toList());

        List<Map<String, Object>> summary = ticketRepository.resolutionSummary().stream()
                .map(p -> Map.<String, Object>of(
                        "resolutionType", p.getResolutionType(),
                        "count", p.getCount()))
                .collect(Collectors.toList());

        return Map.of("byTool", byTool, "summary", summary);
    }

    // ── Sentiment and frustration ─────────────────────────────────────────────

    @GetMapping("/sentiment")
    public Map<String, Object> sentiment() {
        List<Map<String, Object>> byTool = ticketRepository.sentimentByTool().stream()
                .map(p -> {
                    double avgSentiment = p.getAvgSentiment() != null ? p.getAvgSentiment() : 0;
                    long frustrated = p.getFrustratedCount() != null ? p.getFrustratedCount() : 0;
                    double frustrationRate = p.getCount() > 0
                            ? Math.round((double) frustrated / p.getCount() * 1000) / 10.0
                            : 0;
                    return Map.<String, Object>of(
                            "tool", p.getToolName(),
                            "avgSentiment", Math.round(avgSentiment * 10) / 10.0,
                            "count", p.getCount(),
                            "frustratedCount", frustrated,
                            "frustrationRate", frustrationRate);
                })
                .collect(Collectors.toList());

        List<TicketDto> frustratedTickets = ticketRepository.findFrustratedTickets().stream()
                .map(TicketDto::from).collect(Collectors.toList());

        List<TicketDto> recurringTickets = ticketRepository.findRecurringTickets().stream()
                .map(TicketDto::from).collect(Collectors.toList());

        return Map.of(
                "byTool", byTool,
                "frustratedTickets", frustratedTickets,
                "recurringTickets", recurringTickets
        );
    }

    // ── Cross-tool dependency ─────────────────────────────────────────────────

    @GetMapping("/cross-tool")
    public Map<String, Object> crossTool() {
        List<Map<String, Object>> dependencies = ticketRepository.crossToolDependencies().stream()
                .map(p -> Map.<String, Object>of(
                        "filedAgainst", p.getFiledAgainst(),
                        "rootCauseTool", p.getRootCauseTool(),
                        "count", p.getCount()))
                .collect(Collectors.toList());

        // Build a matrix of all affected tools
        List<String> tools = dependencies.stream()
                .map(d -> d.get("filedAgainst").toString())
                .distinct().sorted().collect(Collectors.toList());

        return Map.of("dependencies", dependencies, "tools", tools);
    }

    // ── Knowledge gaps ────────────────────────────────────────────────────────

    @GetMapping("/knowledge-gaps")
    public Map<String, Object> knowledgeGaps() {
        List<Map<String, Object>> gaps = ticketRepository.knowledgeGapSummary().stream()
                .map(p -> Map.<String, Object>of(
                        "description", p.getDescription(),
                        "toolName", p.getToolName(),
                        "category", p.getCategory(),
                        "ticketCount", p.getCount()))
                .collect(Collectors.toList());

        List<TicketDto> gapTickets = ticketRepository.findKnowledgeGapTickets().stream()
                .map(TicketDto::from).collect(Collectors.toList());

        long totalGapTickets = gapTickets.size();
        long totalTickets = ticketRepository.count();
        double gapRate = totalTickets > 0
                ? Math.round((double) totalGapTickets / totalTickets * 1000) / 10.0
                : 0;

        return Map.of(
                "gaps", gaps,
                "gapTickets", gapTickets,
                "totalGapTickets", totalGapTickets,
                "gapRate", gapRate
        );
    }

    // ── Combined dashboard summary ────────────────────────────────────────────

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        long total = ticketRepository.count();
        long frustrated = ticketRepository.findFrustratedTickets().size();
        long recurring = ticketRepository.findRecurringTickets().size();
        long knowledgeGaps = ticketRepository.findKnowledgeGapTickets().size();
        long crossToolCount = ticketRepository.crossToolDependencies().stream()
                .mapToLong(p -> p.getCount()).sum();

        return Map.of(
                "totalTickets", total,
                "frustratedTickets", frustrated,
                "frustratedRate", total > 0 ? Math.round((double) frustrated / total * 1000) / 10.0 : 0,
                "recurringTickets", recurring,
                "recurringRate", total > 0 ? Math.round((double) recurring / total * 1000) / 10.0 : 0,
                "knowledgeGapTickets", knowledgeGaps,
                "crossToolTickets", crossToolCount
        );
    }
}
