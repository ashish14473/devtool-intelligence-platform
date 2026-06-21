package com.devtools.intelligence.repository;

import com.devtools.intelligence.model.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the structured tickets table.
 * Handles CRUD plus the analytics queries powering the dashboard.
 */
@Repository
public interface TicketRepository extends JpaRepository<TicketEntity, Long> {

    boolean existsByTicketId(String ticketId);

    List<TicketEntity> findByToolNameOrderByCreatedDateDesc(String toolName);

    List<TicketEntity> findByCategoryOrderByCreatedDateDesc(String category);

    List<TicketEntity> findAllByOrderByCreatedDateDesc();

    // ── Analytics queries ─────────────────────────────────────────────────────

    /**
     * Returns ticket count grouped by tool name.
     * Result columns: toolName (String), count (Long).
     */
    @Query("SELECT t.toolName AS toolName, COUNT(t) AS count " +
           "FROM TicketEntity t " +
           "GROUP BY t.toolName " +
           "ORDER BY COUNT(t) DESC")
    List<ToolCountProjection> countByTool();

    /**
     * Returns ticket count grouped by category.
     */
    @Query("SELECT t.category AS category, COUNT(t) AS count " +
           "FROM TicketEntity t " +
           "GROUP BY t.category " +
           "ORDER BY COUNT(t) DESC")
    List<CategoryCountProjection> countByCategory();

    /**
     * Returns ticket count grouped by severity.
     */
    @Query("SELECT t.severity AS severity, COUNT(t) AS count " +
           "FROM TicketEntity t " +
           "GROUP BY t.severity " +
           "ORDER BY CASE t.severity " +
           "  WHEN 'critical' THEN 1 WHEN 'high' THEN 2 " +
           "  WHEN 'medium' THEN 3 WHEN 'low' THEN 4 ELSE 5 END")
    List<SeverityCountProjection> countBySeverity();

    /**
     * Returns count of tickets per tool per category — the heatmap data.
     */
    @Query("SELECT t.toolName AS toolName, t.category AS category, COUNT(t) AS count " +
           "FROM TicketEntity t " +
           "GROUP BY t.toolName, t.category " +
           "ORDER BY t.toolName, COUNT(t) DESC")
    List<ToolCategoryCountProjection> countByToolAndCategory();

    // ── Projection interfaces ──────────────────────────────────────────────────
    // Spring Data projects query results onto these interfaces without needing
    // extra DTO classes — they're read-only views of the aggregation results.

    interface ToolCountProjection {
        String getToolName();
        Long getCount();
    }

    interface CategoryCountProjection {
        String getCategory();
        Long getCount();
    }

    interface SeverityCountProjection {
        String getSeverity();
        Long getCount();
    }

    interface ToolCategoryCountProjection {
        String getToolName();
        String getCategory();
        Long getCount();
    }
}
