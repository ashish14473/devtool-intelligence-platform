import { useState, useEffect } from "react";
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell
} from "recharts";

const SEVERITY_COLOR = {
  critical: "#ef4444",
  high:     "#f59e0b",
  medium:   "#3b82f6",
  low:      "#10b981",
};

const CATEGORY_COLOR = {
  auth:           "#7c3aed",
  performance:    "#0891b2",
  config:         "#d97706",
  integration:    "#059669",
  bug:            "#dc2626",
  docs:           "#6b7280",
  "feature-request": "#8b5cf6",
};

const TEAL = "#0d9488";

export default function KnowledgeTab({ status, tools, apiBase }) {
  const [view, setView] = useState("analytics");   // "analytics" | "tickets"
  const [tickets, setTickets] = useState([]);
  const [analytics, setAnalytics] = useState({ byTool: [], byCategory: [], bySeverity: [] });
  const [loadingTickets, setLoadingTickets] = useState(false);
  const [loadingAnalytics, setLoadingAnalytics] = useState(false);
  const [toolFilter, setToolFilter] = useState("");
  const [selectedTicket, setSelectedTicket] = useState(null);

  // Fetch analytics whenever status becomes ready
  useEffect(() => {
    if (!status?.ready) return;
    let active = true;
    async function load() {
      setLoadingAnalytics(true);
      try {
        const [byTool, byCategory, bySeverity] = await Promise.all([
          fetch(`${apiBase}/analytics/by-tool`).then(r => r.json()),
          fetch(`${apiBase}/analytics/by-category`).then(r => r.json()),
          fetch(`${apiBase}/analytics/by-severity`).then(r => r.json()),
        ]);
        if (active) setAnalytics({ byTool, byCategory, bySeverity });
      } finally {
        if (active) setLoadingAnalytics(false);
      }
    }
    load();
    return () => { active = false; };
  }, [status?.ready, apiBase]);

  // Fetch tickets when switching to tickets view or filter changes
  useEffect(() => {
    if (!status?.ready || view !== "tickets") return;
    let active = true;
    async function load() {
      setLoadingTickets(true);
      try {
        const url = toolFilter
          ? `${apiBase}/tickets?tool=${encodeURIComponent(toolFilter)}`
          : `${apiBase}/tickets`;
        const data = await fetch(url).then(r => r.json());
        if (active) setTickets(data);
      } finally {
        if (active) setLoadingTickets(false);
      }
    }
    load();
    return () => { active = false; };
  }, [status?.ready, view, toolFilter, apiBase]);

  if (!status?.ready) {
    return (
      <div className="kb-empty">
        <div className="kb-empty-icon">⏳</div>
        <div>Waiting for ticket ingestion to finish…</div>
        <div className="kb-empty-sub">{status?.message}</div>
      </div>
    );
  }

  return (
    <div className="kb-layout">
      {/* Sub-nav */}
      <div className="kb-subnav">
        <button className={`sub-btn ${view === "analytics" ? "active" : ""}`}
                onClick={() => setView("analytics")}>Analytics</button>
        <button className={`sub-btn ${view === "tickets" ? "active" : ""}`}
                onClick={() => setView("tickets")}>Ticket Browser</button>
      </div>

      {view === "analytics" ? (
        <AnalyticsView analytics={analytics} loading={loadingAnalytics} />
      ) : (
        <TicketBrowser
          tickets={tickets}
          loading={loadingTickets}
          tools={tools}
          toolFilter={toolFilter}
          setToolFilter={setToolFilter}
          selectedTicket={selectedTicket}
          setSelectedTicket={setSelectedTicket}
        />
      )}
    </div>
  );
}

// ── Analytics view ────────────────────────────────────────────────────────────

function AnalyticsView({ analytics, loading }) {
  if (loading) return <div className="kb-loading">Loading analytics…</div>;

  return (
    <div className="analytics-grid">
      <div className="chart-card wide">
        <div className="chart-title">Tickets by tool</div>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={analytics.byTool} layout="vertical"
                    margin={{ left: 8, right: 24, top: 4, bottom: 4 }}>
            <XAxis type="number" tick={{ fontSize: 11 }} />
            <YAxis type="category" dataKey="tool" width={140}
                   tick={{ fontSize: 11 }} />
            <Tooltip formatter={(v) => [v, "tickets"]} />
            <Bar dataKey="count" radius={[0, 4, 4, 0]}>
              {analytics.byTool.map((_, i) => (
                <Cell key={i} fill={TEAL} opacity={1 - i * 0.1} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="chart-card">
        <div className="chart-title">By category</div>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={analytics.byCategory} layout="vertical"
                    margin={{ left: 8, right: 24, top: 4, bottom: 4 }}>
            <XAxis type="number" tick={{ fontSize: 11 }} />
            <YAxis type="category" dataKey="category" width={110}
                   tick={{ fontSize: 11 }} />
            <Tooltip formatter={(v) => [v, "tickets"]} />
            <Bar dataKey="count" radius={[0, 4, 4, 0]}>
              {analytics.byCategory.map((entry, i) => (
                <Cell key={i} fill={CATEGORY_COLOR[entry.category] ?? "#94a3b8"} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="chart-card">
        <div className="chart-title">By severity</div>
        <div className="severity-pills">
          {analytics.bySeverity.map(s => (
            <div key={s.severity} className="sev-pill"
                 style={{ borderColor: SEVERITY_COLOR[s.severity] ?? "#94a3b8" }}>
              <span className="sev-dot"
                    style={{ background: SEVERITY_COLOR[s.severity] ?? "#94a3b8" }} />
              <span className="sev-label">{s.severity}</span>
              <span className="sev-count">{s.count}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

// ── Ticket browser ────────────────────────────────────────────────────────────

function TicketBrowser({ tickets, loading, tools, toolFilter, setToolFilter, selectedTicket, setSelectedTicket }) {
  return (
    <div className="browser-layout">
      <div className="browser-toolbar">
        <span className="browser-count">{tickets.length} tickets</span>
        <select value={toolFilter} onChange={e => setToolFilter(e.target.value)}>
          <option value="">All tools</option>
          {tools.map(t => <option key={t} value={t}>{t}</option>)}
        </select>
      </div>

      {loading ? (
        <div className="kb-loading">Loading tickets…</div>
      ) : (
        <div className="browser-split">
          <div className="ticket-list">
            {tickets.map(t => (
              <div
                key={t.ticketId}
                className={`ticket-row ${selectedTicket?.ticketId === t.ticketId ? "selected" : ""}`}
                onClick={() => setSelectedTicket(selectedTicket?.ticketId === t.ticketId ? null : t)}
              >
                <div className="tr-top">
                  <span className="tr-id">{t.ticketId}</span>
                  <SevBadge severity={t.severity} />
                </div>
                <div className="tr-tool">{t.toolName} · {t.category}</div>
                <div className="tr-pain">{t.painPoint}</div>
              </div>
            ))}
            {tickets.length === 0 && (
              <div className="kb-empty-sub" style={{ padding: "2rem 1rem" }}>
                No tickets found
              </div>
            )}
          </div>

          {selectedTicket && (
            <TicketDetail ticket={selectedTicket} onClose={() => setSelectedTicket(null)} />
          )}
        </div>
      )}
    </div>
  );
}

function TicketDetail({ ticket, onClose }) {
  return (
    <div className="ticket-detail">
      <div className="detail-header">
        <div>
          <span className="detail-id">{ticket.ticketId}</span>
          <SevBadge severity={ticket.severity} />
        </div>
        <button className="detail-close" onClick={onClose}>✕</button>
      </div>
      <div className="detail-meta">
        <MetaChip label="Tool" value={ticket.toolName} />
        <MetaChip label="Category" value={ticket.category} />
        <MetaChip label="Priority" value={ticket.priority} />
        <MetaChip label="Status" value={ticket.status} />
      </div>
      {ticket.createdDate && (
        <div className="detail-dates">
          Created: {ticket.createdDate}
          {ticket.resolvedDate && ` · Resolved: ${ticket.resolvedDate}`}
        </div>
      )}
      <div className="detail-section">
        <div className="detail-section-label">Pain Point</div>
        <div className="detail-text">{ticket.painPoint}</div>
      </div>
      <div className="detail-section">
        <div className="detail-section-label">Summary</div>
        <div className="detail-text">{ticket.summary}</div>
      </div>
    </div>
  );
}

function SevBadge({ severity }) {
  return (
    <span className="sev-badge"
          style={{ background: (SEVERITY_COLOR[severity] ?? "#94a3b8") + "22",
                   color: SEVERITY_COLOR[severity] ?? "#94a3b8",
                   borderColor: (SEVERITY_COLOR[severity] ?? "#94a3b8") + "55" }}>
      {severity}
    </span>
  );
}

function MetaChip({ label, value }) {
  return value ? (
    <span className="meta-chip"><span className="meta-label">{label}</span> {value}</span>
  ) : null;
}
