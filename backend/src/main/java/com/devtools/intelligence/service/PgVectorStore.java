package com.devtools.intelligence.service;

import com.devtools.intelligence.model.VectorDocument;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces InMemoryVectorStore. Uses PostgreSQL + pgvector for durable,
 * indexed similarity search via the <=> cosine distance operator.
 *
 * Why JDBC directly rather than Spring Data JPA?
 * pgvector's custom 'vector' type and the <=> operator aren't natively
 * supported by Hibernate, so the cleanest path is a plain JdbcTemplate
 * for the two operations that touch embeddings (insert and search),
 * while JPA handles the structured tickets table normally.
 */
@Service
@Slf4j
public class PgVectorStore {

    private final JdbcTemplate jdbc;

    public PgVectorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Persists one embedding to ticket_embeddings. Called once per ticket
     * during ingestion. Uses INSERT ... ON CONFLICT DO UPDATE so the
     * ingestion pipeline is safely idempotent — re-running it updates
     * existing embeddings rather than duplicating them.
     */
    public void save(String ticketId, float[] embedding) {
        String sql = """
                INSERT INTO ticket_embeddings (ticket_id, embedding)
                VALUES (?, ?::vector)
                ON CONFLICT (ticket_id)
                DO UPDATE SET embedding = EXCLUDED.embedding,
                              created_at = NOW()
                """;

        jdbc.execute((Connection conn) -> {
            PGvector.addVectorType(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ticketId);
                ps.setObject(2, new PGvector(embedding));
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Finds the topK most similar tickets to the query embedding using
     * the HNSW index on ticket_embeddings. The <=> operator returns
     * cosine DISTANCE (0 = identical, 2 = opposite), so we convert it
     * to similarity (1 - distance) for the caller.
     *
     * The JOIN back to tickets pulls the metadata needed to build the
     * response — this is why ticket_id is a foreign key in both tables.
     *
     * @param queryEmbedding  embedded user question (same model as ingestion)
     * @param topK            number of results to return
     * @param toolFilter      optional tool name filter; null/blank = all tools
     */
    public List<ScoredDocument> search(float[] queryEmbedding, int topK, String toolFilter) {

        boolean hasFilter = toolFilter != null && !toolFilter.isBlank();

        String sql = """
                SELECT
                    t.ticket_id,
                    t.tool_name,
                    t.category,
                    t.severity,
                    t.pain_point,
                    t.embedded_text,
                    1 - (te.embedding <=> ?::vector) AS similarity
                FROM ticket_embeddings te
                JOIN tickets t ON te.ticket_id = t.ticket_id
                """ +
                (hasFilter ? "WHERE t.tool_name = ? \n" : "") +
                "ORDER BY te.embedding <=> ?::vector \n" +
                "LIMIT ?";

        List<ScoredDocument> results = new ArrayList<>();

        jdbc.execute((Connection conn) -> {
            PGvector.addVectorType(conn);
            PGvector vec = new PGvector(queryEmbedding);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int idx = 1;
                ps.setObject(idx++, vec);               // first ? for similarity calc
                if (hasFilter) {
                    ps.setString(idx++, toolFilter);     // optional WHERE clause
                }
                ps.setObject(idx++, vec);               // second ? for ORDER BY
                ps.setInt(idx, topK);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        VectorDocument doc = new VectorDocument(
                                rs.getString("ticket_id"),
                                rs.getString("embedded_text"),
                                null,                           // we don't need the vector back
                                rs.getString("tool_name"),
                                rs.getString("category"),
                                rs.getString("severity"),
                                rs.getString("pain_point"),
                                true,                           // only resolved tickets are stored
                                null
                        );
                        results.add(new ScoredDocument(doc, rs.getDouble("similarity")));
                    }
                }
            }
            return null;
        });

        return results;
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM ticket_embeddings", Long.class);
        return n == null ? 0 : n;
    }

    public record ScoredDocument(VectorDocument document, double score) {}
}
