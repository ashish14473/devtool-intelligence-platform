package com.devtools.intelligence.repository;

import com.devtools.intelligence.model.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<TicketEntity, Long> {

    boolean existsByTicketId(String ticketId);
    List<TicketEntity> findByToolNameOrderByCreatedDateDesc(String toolName);
    List<TicketEntity> findByCategoryOrderByCreatedDateDesc(String category);
    List<TicketEntity> findAllByOrderByCreatedDateDesc();

    // ── Existing analytics ────────────────────────────────────────────────────
    @Query("SELECT t.toolName AS toolName, COUNT(t) AS count FROM TicketEntity t GROUP BY t.toolName ORDER BY COUNT(t) DESC")
    List<ToolCountProjection> countByTool();

    @Query("SELECT t.category AS category, COUNT(t) AS count FROM TicketEntity t GROUP BY t.category ORDER BY COUNT(t) DESC")
    List<CategoryCountProjection> countByCategory();

    @Query("SELECT t.severity AS severity, COUNT(t) AS count FROM TicketEntity t GROUP BY t.severity ORDER BY CASE t.severity WHEN 'critical' THEN 1 WHEN 'high' THEN 2 WHEN 'medium' THEN 3 WHEN 'low' THEN 4 ELSE 5 END")
    List<SeverityCountProjection> countBySeverity();

    @Query("SELECT t.toolName AS toolName, t.category AS category, COUNT(t) AS count FROM TicketEntity t GROUP BY t.toolName, t.category ORDER BY t.toolName, COUNT(t) DESC")
    List<ToolCategoryCountProjection> countByToolAndCategory();

    // ── Resolution quality ────────────────────────────────────────────────────
    @Query("SELECT t.toolName AS toolName, t.resolutionType AS resolutionType, COUNT(t) AS count FROM TicketEntity t WHERE t.resolutionType IS NOT NULL GROUP BY t.toolName, t.resolutionType ORDER BY t.toolName, COUNT(t) DESC")
    List<ResolutionTypeProjection> countByToolAndResolutionType();

    @Query("SELECT t.resolutionType AS resolutionType, COUNT(t) AS count FROM TicketEntity t WHERE t.resolutionType IS NOT NULL GROUP BY t.resolutionType ORDER BY COUNT(t) DESC")
    List<ResolutionSummaryProjection> resolutionSummary();

    // ── Sentiment and frustration ─────────────────────────────────────────────
    @Query("SELECT t.toolName AS toolName, AVG(t.sentimentScore) AS avgSentiment, COUNT(t) AS count, SUM(CASE WHEN t.frustrationFlag = true THEN 1 ELSE 0 END) AS frustratedCount FROM TicketEntity t WHERE t.sentimentScore IS NOT NULL GROUP BY t.toolName ORDER BY AVG(t.sentimentScore) DESC")
    List<SentimentProjection> sentimentByTool();

    @Query("SELECT t FROM TicketEntity t WHERE t.frustrationFlag = true ORDER BY t.createdDate DESC")
    List<TicketEntity> findFrustratedTickets();

    @Query("SELECT t FROM TicketEntity t WHERE t.recurrenceSignal = true ORDER BY t.createdDate DESC")
    List<TicketEntity> findRecurringTickets();

    // ── Cross-tool dependency ─────────────────────────────────────────────────
    @Query("SELECT t.toolName AS filedAgainst, t.rootCauseTool AS rootCauseTool, COUNT(t) AS count FROM TicketEntity t WHERE t.rootCauseTool IS NOT NULL AND t.rootCauseTool != t.toolName GROUP BY t.toolName, t.rootCauseTool ORDER BY COUNT(t) DESC")
    List<CrossToolProjection> crossToolDependencies();

    // ── Knowledge gaps ────────────────────────────────────────────────────────
    @Query("SELECT t FROM TicketEntity t WHERE t.knowledgeGapFlag = true ORDER BY t.toolName, t.category")
    List<TicketEntity> findKnowledgeGapTickets();

    @Query("SELECT t.knowledgeGapDescription AS description, t.toolName AS toolName, t.category AS category, COUNT(t) AS count FROM TicketEntity t WHERE t.knowledgeGapFlag = true AND t.knowledgeGapDescription IS NOT NULL GROUP BY t.knowledgeGapDescription, t.toolName, t.category ORDER BY COUNT(t) DESC")
    List<KnowledgeGapProjection> knowledgeGapSummary();

    // ── Projections ───────────────────────────────────────────────────────────
    interface ToolCountProjection { String getToolName(); Long getCount(); }
    interface CategoryCountProjection { String getCategory(); Long getCount(); }
    interface SeverityCountProjection { String getSeverity(); Long getCount(); }
    interface ToolCategoryCountProjection { String getToolName(); String getCategory(); Long getCount(); }
    interface ResolutionTypeProjection { String getToolName(); String getResolutionType(); Long getCount(); }
    interface ResolutionSummaryProjection { String getResolutionType(); Long getCount(); }
    interface SentimentProjection { String getToolName(); Double getAvgSentiment(); Long getCount(); Long getFrustratedCount(); }
    interface CrossToolProjection { String getFiledAgainst(); String getRootCauseTool(); Long getCount(); }
    interface KnowledgeGapProjection { String getDescription(); String getToolName(); String getCategory(); Long getCount(); }

    // ── Methods used by scheduled services ───────────────────────────────────

    /** Used by EmergingIssueService — tickets created after a given date */
    List<TicketEntity> findByCreatedDateAfter(java.time.LocalDate date);

    /** Used by EmergingIssueService — fetch a single ticket by its Jira key */
    java.util.Optional<TicketEntity> findByTicketId(String ticketId);

    /** Used by KnowledgeGapService — all tickets flagged as knowledge gaps */
    List<TicketEntity> findByKnowledgeGapFlagTrue();
}
