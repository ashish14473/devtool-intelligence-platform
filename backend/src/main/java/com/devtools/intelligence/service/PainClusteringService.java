package com.devtools.intelligence.service;

import com.devtools.intelligence.config.OpenAiProperties;
import com.devtools.intelligence.model.TicketEntity;
import com.devtools.intelligence.repository.PainClusterRepository;
import com.devtools.intelligence.repository.TicketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Weekly pain pattern clustering service.
 *
 * Algorithm:
 * 1. Pull all embeddings from pgvector via JDBC
 * 2. Run DBSCAN (density-based clustering) to group semantically similar tickets
 * 3. For each cluster with 2+ tickets: ask LLM to name it and describe the root cause
 * 4. Store named clusters in pain_clusters table
 *
 * DBSCAN is used rather than K-means because:
 * - You don't need to know the number of clusters in advance
 * - It handles noise (singleton outlier tickets) gracefully
 * - It finds clusters of arbitrary shape in vector space
 *
 * Runs every Sunday at 2am.
 */
@Service
@Slf4j
public class PainClusteringService {

    private static final double EPS = 0.25;    // cosine distance threshold for same cluster
    private static final int MIN_PTS = 2;       // minimum tickets to form a cluster

    private final JdbcTemplate jdbc;
    private final TicketRepository ticketRepository;
    private final PainClusterRepository painClusterRepository;
    private final RestClient openAiRestClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public PainClusteringService(JdbcTemplate jdbc,
                                  TicketRepository ticketRepository,
                                  PainClusterRepository painClusterRepository,
                                  @Qualifier("openAiRestClient") RestClient openAiRestClient,
                                  OpenAiProperties openAiProperties,
                                  ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.ticketRepository = ticketRepository;
        this.painClusterRepository = painClusterRepository;
        this.openAiRestClient = openAiRestClient;
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
    }

    //@Scheduled(initialDelay = 120000, fixedDelay = 3600000)
  // Every Sunday at 2am
    public void runClustering() {
        log.info("=== Pain clustering job started ===");

        // 1. Pull all ticket embeddings and IDs from pgvector
        List<TicketEmbedding> embeddings = loadEmbeddings();
        if (embeddings.size() < MIN_PTS) {
            log.info("Not enough tickets for clustering ({}), skipping", embeddings.size());
            return;
        }

        log.info("Running DBSCAN on {} ticket embeddings", embeddings.size());

        // 2. Run DBSCAN
        Map<Integer, List<String>> clusters = dbscan(embeddings);

        log.info("Found {} clusters from DBSCAN", clusters.size());

        // 3. Clear old clusters and save new ones
        painClusterRepository.deleteAll();

        for (Map.Entry<Integer, List<String>> entry : clusters.entrySet()) {
            List<String> ticketIds = entry.getValue();
            if (ticketIds.size() < MIN_PTS) continue;

            // Fetch ticket details for context
            List<TicketEntity> tickets = ticketIds.stream()
                    .map(id -> ticketRepository.findByTicketId(id))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());

            // Ask LLM to name and describe the cluster
            ClusterInsight insight = nameCluster(tickets);
            if (insight == null) continue;

            Set<String> tools = tickets.stream()
                    .map(TicketEntity::getToolName)
                    .collect(Collectors.toSet());

            LocalDate earliest = tickets.stream()
                    .map(TicketEntity::getCreatedDate)
                    .filter(Objects::nonNull)
                    .min(Comparator.naturalOrder())
                    .orElse(null);

            LocalDate latest = tickets.stream()
                    .map(TicketEntity::getCreatedDate)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            com.devtools.intelligence.model.PainClusterEntity cluster =
                    com.devtools.intelligence.model.PainClusterEntity.builder()
                            .clusterLabel(insight.label())
                            .rootCause(insight.rootCause())
                            .ticketIds(String.join(",", ticketIds))
                            .ticketCount(ticketIds.size())
                            .toolsAffected(String.join(",", tools))
                            .firstSeen(earliest)
                            .lastSeen(latest)
                            .build();

            painClusterRepository.save(cluster);
            log.info("Saved cluster '{}' with {} tickets", insight.label(), ticketIds.size());
        }

        log.info("=== Pain clustering job complete ===");
    }

    /** Pull ticket IDs and their embedding vectors from pgvector */
    private List<TicketEmbedding> loadEmbeddings() {
        return jdbc.query(
                "SELECT te.ticket_id, te.embedding::float4[] " +
                "FROM ticket_embeddings te " +
                "JOIN tickets t ON te.ticket_id = t.ticket_id",
                (rs, row) -> {
                    String ticketId = rs.getString("ticket_id");
                    float[] vec = parseVector(rs.getString("embedding"));
                    return new TicketEmbedding(ticketId, vec);
                }
        );
    }

    /** Parse PostgreSQL vector string e.g. "[0.1,0.2,...]" into float[] */
    private float[] parseVector(String vectorStr) {
        if (vectorStr == null) return new float[0];
        String cleaned = vectorStr.replaceAll("[\\[\\]{}\\s]", "");
        String[] parts = cleaned.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }

    /**
     * DBSCAN implementation using cosine distance.
     * Returns a map of cluster ID -> list of ticket IDs.
     * Cluster ID -1 = noise (unclustered singletons).
     */
    private Map<Integer, List<String>> dbscan(List<TicketEmbedding> points) {
        int n = points.size();
        int[] labels = new int[n];
        Arrays.fill(labels, -1);  // -1 = unvisited

        int clusterId = 0;

        for (int i = 0; i < n; i++) {
            if (labels[i] != -1) continue;  // already visited

            List<Integer> neighbours = rangeQuery(points, i, EPS);

            if (neighbours.size() < MIN_PTS) {
                labels[i] = 0;  // noise
                continue;
            }

            clusterId++;
            labels[i] = clusterId;

            Queue<Integer> seeds = new LinkedList<>(neighbours);
            while (!seeds.isEmpty()) {
                int q = seeds.poll();
                if (labels[q] == 0) labels[q] = clusterId;  // was noise, now border
                if (labels[q] != -1) continue;               // already in a cluster

                labels[q] = clusterId;
                List<Integer> qNeighbours = rangeQuery(points, q, EPS);
                if (qNeighbours.size() >= MIN_PTS) {
                    seeds.addAll(qNeighbours);
                }
            }
        }

        // Group by cluster ID
        Map<Integer, List<String>> clusters = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (labels[i] > 0) {
                clusters.computeIfAbsent(labels[i], k -> new ArrayList<>())
                        .add(points.get(i).ticketId());
            }
        }
        return clusters;
    }

    private List<Integer> rangeQuery(List<TicketEmbedding> points, int idx, double eps) {
        List<Integer> result = new ArrayList<>();
        float[] target = points.get(idx).vector();
        for (int i = 0; i < points.size(); i++) {
            if (cosineDistance(target, points.get(i).vector()) <= eps) {
                result.add(i);
            }
        }
        return result;
    }

    private double cosineDistance(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return 2.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 2.0;
        return 1.0 - (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    /** Ask LLM to name the cluster and describe the shared root cause */
    private ClusterInsight nameCluster(List<TicketEntity> tickets) {
        String ticketSummaries = tickets.stream()
                .map(t -> "- [" + t.getTicketId() + "] " + t.getPainPoint())
                .collect(Collectors.joining("\n"));

        String prompt = """
                These developer support tickets are semantically similar and likely share an \
                underlying root cause or pattern. Analyse them and return ONLY a JSON object:

                {
                  "label": "short cluster name (5-8 words max)",
                  "rootCause": "one paragraph describing the shared root cause and why these tickets cluster together"
                }

                Tickets:
                %s
                """.formatted(ticketSummaries);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", openAiProperties.getChatModel());
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

        try {
            String raw = openAiRestClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode result = objectMapper.readTree(content);
            return new ClusterInsight(
                    result.path("label").asText("Unnamed cluster"),
                    result.path("rootCause").asText("")
            );
        } catch (Exception e) {
            log.error("Cluster naming failed: {}", e.getMessage());
            return null;
        }
    }

    record TicketEmbedding(String ticketId, float[] vector) {}
    record ClusterInsight(String label, String rootCause) {}
}
