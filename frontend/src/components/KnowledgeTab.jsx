import { useState, useEffect } from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell, PieChart, Pie, Legend } from "recharts";

const SEVERITY_COLOR = { critical:"#ef4444", high:"#f59e0b", medium:"#3b82f6", low:"#10b981" };
const CATEGORY_COLOR = { auth:"#7c3aed", performance:"#0891b2", config:"#d97706", integration:"#059669", bug:"#dc2626", onboarding:"#8b5cf6", docs:"#6b7280", migration:"#0e7490", "feature-request":"#9333ea", "pipeline-issue":"#b45309", access:"#be185d", cleanup:"#4d7c0f", "proxy-setup":"#0369a1", "plugin-conflict":"#b91c1c", "credential-issue":"#c2410c" };
const RESOLUTION_COLOR = { FIXED:"#10b981", WORKAROUND:"#f59e0b", UNANSWERED:"#ef4444", ABANDONED:"#94a3b8" };
const TEAL = "#0d9488";

export default function KnowledgeTab({ status, tools, apiBase }) {
  const [view, setView] = useState("analytics");
  const [tickets, setTickets] = useState([]);
  const [analytics, setAnalytics] = useState({ byTool:[], byCategory:[], bySeverity:[], heatmap:[] });
  const [resolutionData, setResolutionData] = useState({ byTool:[], summary:[] });
  const [sentimentData, setSentimentData] = useState({ byTool:[], frustratedTickets:[], recurringTickets:[] });
  const [crossToolData, setCrossToolData] = useState({ dependencies:[], tools:[] });
  const [knowledgeGapData, setKnowledgeGapData] = useState({ gaps:[], gapTickets:[], totalGapTickets:0, gapRate:0 });
  const [summary, setSummary] = useState({});
  const [loading, setLoading] = useState(false);
  const [toolFilter, setToolFilter] = useState("");
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [analyticsView, setAnalyticsView] = useState("overview");

  useEffect(() => {
    if (!status?.ready) return;
    let active = true;
    async function load() {
      setLoading(true);
      try {
        const [byTool, byCategory, bySeverity, heatmap, resolution, sentiment, crossTool, knowledgeGaps, sum] = await Promise.all([
          fetch(`${apiBase}/analytics/by-tool`).then(r=>r.json()),
          fetch(`${apiBase}/analytics/by-category`).then(r=>r.json()),
          fetch(`${apiBase}/analytics/by-severity`).then(r=>r.json()),
          fetch(`${apiBase}/analytics/heatmap`).then(r=>r.json()),
          fetch(`${apiBase}/analytics/resolution-quality`).then(r=>r.json()),
          fetch(`${apiBase}/analytics/sentiment`).then(r=>r.json()),
          fetch(`${apiBase}/analytics/cross-tool`).then(r=>r.json()),
          fetch(`${apiBase}/analytics/knowledge-gaps`).then(r=>r.json()),
          fetch(`${apiBase}/analytics/summary`).then(r=>r.json()),
        ]);
        if (!active) return;
        setAnalytics({ byTool, byCategory, bySeverity, heatmap });
        setResolutionData(resolution);
        setSentimentData(sentiment);
        setCrossToolData(crossTool);
        setKnowledgeGapData(knowledgeGaps);
        setSummary(sum);
      } finally { if (active) setLoading(false); }
    }
    load();
    return () => { active = false; };
  }, [status?.ready, apiBase]);

  useEffect(() => {
    if (!status?.ready || view !== "tickets") return;
    let active = true;
    async function load() {
      setLoading(true);
      try {
        const url = toolFilter ? `${apiBase}/tickets?tool=${encodeURIComponent(toolFilter)}` : `${apiBase}/tickets`;
        const data = await fetch(url).then(r=>r.json());
        if (active) setTickets(data);
      } finally { if (active) setLoading(false); }
    }
    load();
    return () => { active = false; };
  }, [status?.ready, view, toolFilter, apiBase]);

  if (!status?.ready) return (
    <div className="kb-empty">
      <div className="kb-empty-icon">⏳</div>
      <div>Waiting for ticket ingestion to finish…</div>
      <div className="kb-empty-sub">{status?.message}</div>
    </div>
  );

  return (
    <div className="kb-layout">
      <div className="kb-subnav">
        <button className={`sub-btn ${view==="analytics"?"active":""}`} onClick={()=>setView("analytics")}>Analytics</button>
        <button className={`sub-btn ${view==="tickets"?"active":""}`} onClick={()=>setView("tickets")}>Ticket Browser</button>
      </div>
      {view === "analytics"
        ? <AnalyticsView analytics={analytics} resolution={resolutionData} sentiment={sentimentData} crossTool={crossToolData} knowledgeGaps={knowledgeGapData} summary={summary} loading={loading} analyticsView={analyticsView} setAnalyticsView={setAnalyticsView} />
        : <TicketBrowser tickets={tickets} loading={loading} tools={tools} toolFilter={toolFilter} setToolFilter={setToolFilter} selectedTicket={selectedTicket} setSelectedTicket={setSelectedTicket} />}
    </div>
  );
}

// ── Analytics view ────────────────────────────────────────────────────────────
function AnalyticsView({ analytics, resolution, sentiment, crossTool, knowledgeGaps, summary, loading, analyticsView, setAnalyticsView }) {
  if (loading) return <div className="kb-loading">Loading analytics…</div>;

  const subViews = [
    { key:"overview", label:"Overview" },
    { key:"resolution", label:"Resolution Quality" },
    { key:"sentiment", label:"Sentiment & Frustration" },
    { key:"crosstool", label:"Cross-Tool Dependencies" },
    { key:"gaps", label:"Knowledge Gaps" },
  ];

  return (
    <div className="analytics-outer">
      <div className="analytics-subnav">
        {subViews.map(v => (
          <button key={v.key} className={`analytics-tab ${analyticsView===v.key?"active":""}`} onClick={()=>setAnalyticsView(v.key)}>{v.label}</button>
        ))}
      </div>
      <div className="analytics-content">
        {analyticsView === "overview" && <OverviewPanel analytics={analytics} summary={summary} />}
        {analyticsView === "resolution" && <ResolutionPanel data={resolution} />}
        {analyticsView === "sentiment" && <SentimentPanel data={sentiment} />}
        {analyticsView === "crosstool" && <CrossToolPanel data={crossTool} />}
        {analyticsView === "gaps" && <KnowledgeGapsPanel data={knowledgeGaps} />}
      </div>
    </div>
  );
}

function OverviewPanel({ analytics, summary }) {
  return (
    <div>
      <div className="summary-kpis">
        <KpiCard label="Total Tickets" value={summary.totalTickets ?? 0} color={TEAL} />
        <KpiCard label="Frustrated Developers" value={`${summary.frustratedRate ?? 0}%`} color="#ef4444" sub={`${summary.frustratedTickets ?? 0} tickets`} />
        <KpiCard label="Recurring Issues" value={`${summary.recurringRate ?? 0}%`} color="#f59e0b" sub={`${summary.recurringTickets ?? 0} tickets`} />
        <KpiCard label="Knowledge Gaps" value={summary.knowledgeGapTickets ?? 0} color="#7c3aed" sub="preventable tickets" />
        <KpiCard label="Cross-Tool Issues" value={summary.crossToolTickets ?? 0} color="#0891b2" sub="wrong team assigned" />
      </div>

      <div className="charts-grid">
        <div className="chart-card wide">
          <div className="chart-title">Tickets by tool</div>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={analytics.byTool} layout="vertical" margin={{left:8,right:24,top:4,bottom:4}}>
              <XAxis type="number" tick={{fontSize:11}} />
              <YAxis type="category" dataKey="tool" width={130} tick={{fontSize:11}} />
              <Tooltip formatter={v=>[v,"tickets"]} />
              <Bar dataKey="count" radius={[0,4,4,0]}>
                {analytics.byTool.map((_,i)=><Cell key={i} fill={TEAL} opacity={1-i*0.08} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <div className="chart-card">
          <div className="chart-title">By category</div>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={analytics.byCategory} layout="vertical" margin={{left:8,right:24,top:4,bottom:4}}>
              <XAxis type="number" tick={{fontSize:11}} />
              <YAxis type="category" dataKey="category" width={120} tick={{fontSize:11}} />
              <Tooltip formatter={v=>[v,"tickets"]} />
              <Bar dataKey="count" radius={[0,4,4,0]}>
                {analytics.byCategory.map((e,i)=><Cell key={i} fill={CATEGORY_COLOR[e.category]??"#94a3b8"} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <div className="chart-card">
          <div className="chart-title">By severity</div>
          <div className="severity-pills">
            {analytics.bySeverity.map(s=>(
              <div key={s.severity} className="sev-pill" style={{borderColor:SEVERITY_COLOR[s.severity]??"#94a3b8"}}>
                <span className="sev-dot" style={{background:SEVERITY_COLOR[s.severity]??"#94a3b8"}} />
                <span className="sev-label">{s.severity}</span>
                <span className="sev-count">{s.count}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function ResolutionPanel({ data }) {
  const summary = data.summary ?? [];
  const total = summary.reduce((a,s)=>a+s.count,0);

  // Group byTool data for stacked view
  const toolMap = {};
  (data.byTool ?? []).forEach(r => {
    if (!toolMap[r.tool]) toolMap[r.tool] = { tool:r.tool };
    toolMap[r.tool][r.resolutionType] = r.count;
  });
  const toolData = Object.values(toolMap);

  return (
    <div>
      <div className="panel-header">
        <div className="panel-title">Resolution Quality</div>
        <div className="panel-sub">How tickets were actually resolved — beyond just "Done" status</div>
      </div>
      <div className="charts-grid">
        <div className="chart-card">
          <div className="chart-title">Overall resolution breakdown</div>
          <ResponsiveContainer width="100%" height={220}>
            <PieChart>
              <Pie data={summary} dataKey="count" nameKey="resolutionType" cx="50%" cy="50%" outerRadius={80} label={({resolutionType,percent})=>`${resolutionType} ${(percent*100).toFixed(0)}%`} labelLine={false}>
                {summary.map((e,i)=><Cell key={i} fill={RESOLUTION_COLOR[e.resolutionType]??"#94a3b8"} />)}
              </Pie>
              <Tooltip formatter={(v,n)=>[v,n]} />
            </PieChart>
          </ResponsiveContainer>
          <div className="resolution-legend">
            {Object.entries(RESOLUTION_COLOR).map(([k,c])=>(
              <span key={k} className="res-chip" style={{background:c+"22",color:c,borderColor:c+"55"}}>{k}</span>
            ))}
          </div>
        </div>
        <div className="chart-card wide">
          <div className="chart-title">Resolution type by tool</div>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={toolData} layout="vertical" margin={{left:8,right:16,top:4,bottom:4}}>
              <XAxis type="number" tick={{fontSize:11}} />
              <YAxis type="category" dataKey="tool" width={130} tick={{fontSize:11}} />
              <Tooltip />
              <Legend wrapperStyle={{fontSize:11}} />
              {Object.keys(RESOLUTION_COLOR).map(rt=>(
                <Bar key={rt} dataKey={rt} stackId="a" fill={RESOLUTION_COLOR[rt]} />
              ))}
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
      <InsightBox color="#f59e0b" title="Leadership insight">
        {total > 0 ? `${((summary.find(s=>s.resolutionType==="WORKAROUND")?.count??0)/total*100).toFixed(0)}% of resolved tickets were closed with workarounds — these problems will likely return.` : "No resolution data yet."}
      </InsightBox>
    </div>
  );
}

function SentimentPanel({ data }) {
  const byTool = data.byTool ?? [];
  const frustrated = data.frustratedTickets ?? [];
  const recurring = data.recurringTickets ?? [];

  return (
    <div>
      <div className="panel-header">
        <div className="panel-title">Sentiment & Frustration</div>
        <div className="panel-sub">Developer satisfaction hidden inside comment threads — beyond ticket status</div>
      </div>
      <div className="charts-grid">
        <div className="chart-card wide">
          <div className="chart-title">Average sentiment by tool (1=satisfied · 5=frustrated)</div>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={byTool} layout="vertical" margin={{left:8,right:60,top:4,bottom:4}}>
              <XAxis type="number" domain={[0,5]} tick={{fontSize:11}} />
              <YAxis type="category" dataKey="tool" width={130} tick={{fontSize:11}} />
              <Tooltip formatter={(v,n)=>[n==="avgSentiment"?v+" / 5":v+"%", n==="avgSentiment"?"Avg Sentiment":"Frustration Rate"]} />
              <Bar dataKey="avgSentiment" radius={[0,4,4,0]} name="avgSentiment">
                {byTool.map((e,i)=>{
                  const score = e.avgSentiment??0;
                  const color = score<=2?"#10b981":score<=3?"#f59e0b":"#ef4444";
                  return <Cell key={i} fill={color} />;
                })}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <div className="chart-card">
          <div className="chart-title">Frustration rate by tool</div>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={byTool} layout="vertical" margin={{left:8,right:40,top:4,bottom:4}}>
              <XAxis type="number" unit="%" domain={[0,100]} tick={{fontSize:11}} />
              <YAxis type="category" dataKey="tool" width={130} tick={{fontSize:11}} />
              <Tooltip formatter={v=>[v+"%","Frustration rate"]} />
              <Bar dataKey="frustrationRate" radius={[0,4,4,0]} fill="#ef4444" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
      <div className="charts-grid" style={{marginTop:12}}>
        <TicketListCard title={`Frustrated tickets (${frustrated.length})`} tickets={frustrated} badgeKey="sentimentScore" badgeLabel="score" color="#ef4444" />
        <TicketListCard title={`Recurring issues (${recurring.length})`} tickets={recurring} badgeKey="category" color="#f59e0b" />
      </div>
      <InsightBox color="#ef4444" title="Leadership insight">
        {frustrated.length > 0 ? `${frustrated.length} tickets marked Done show developer frustration signals in comments — these may be masking unresolved problems.` : "No frustrated tickets detected."}
      </InsightBox>
    </div>
  );
}

function CrossToolPanel({ data }) {
  const deps = data.dependencies ?? [];
  // const _tools = [...new Set([...deps.map(d=>d.filedAgainst), ...deps.map(d=>d.rootCauseTool)])].sort();

  return (
    <div>
      <div className="panel-header">
        <div className="panel-title">Cross-Tool Dependencies</div>
        <div className="panel-sub">Tickets filed against one tool whose root cause lies in another — revealing hidden dependencies</div>
      </div>
      {deps.length === 0 ? (
        <div className="kb-empty-sub" style={{padding:"2rem"}}>No cross-tool dependencies detected yet. This improves as more tickets are indexed.</div>
      ) : (
        <>
          <div className="chart-card" style={{marginBottom:12}}>
            <div className="chart-title">Dependency flow — filed against → root cause tool</div>
            <div className="dep-list">
              {deps.map((d,i)=>(
                <div key={i} className="dep-row">
                  <span className="dep-tool filed">{d.filedAgainst}</span>
                  <span className="dep-arrow">→</span>
                  <span className="dep-tool root">{d.rootCauseTool}</span>
                  <span className="dep-count">{d.count} ticket{d.count!==1?"s":""}</span>
                </div>
              ))}
            </div>
          </div>
          <InsightBox color="#0891b2" title="Leadership insight">
            {`${deps.length} cross-tool dependency pattern${deps.length!==1?"s":""} detected. Tickets being investigated by the wrong team, leading to longer resolution times.`}
          </InsightBox>
        </>
      )}
    </div>
  );
}

function KnowledgeGapsPanel({ data }) {
  const gaps = data.gaps ?? [];
  // const _gapTickets = data.gapTickets ?? [];

  return (
    <div>
      <div className="panel-header">
        <div className="panel-title">Knowledge Gaps</div>
        <div className="panel-sub">Tickets that a documentation article or onboarding guide would have prevented</div>
      </div>
      <div className="kpi-row-small">
        <KpiCard label="Preventable tickets" value={data.totalGapTickets??0} color="#7c3aed" />
        <KpiCard label="Gap rate" value={`${data.gapRate??0}%`} color="#7c3aed" sub="of all tickets" />
      </div>
      {gaps.length === 0 ? (
        <div className="kb-empty-sub" style={{padding:"2rem"}}>No knowledge gaps detected yet.</div>
      ) : (
        <>
          <div className="chart-card" style={{marginBottom:12}}>
            <div className="chart-title">Suggested documentation articles — ranked by tickets prevented</div>
            {gaps.map((g,i)=>(
              <div key={i} className="gap-row">
                <div className="gap-rank">#{i+1}</div>
                <div className="gap-body">
                  <div className="gap-title">{g.description}</div>
                  <div className="gap-meta">{g.toolName} · {g.category} · {g.ticketCount} ticket{g.ticketCount!==1?"s":""} prevented</div>
                </div>
                <div className="gap-count">{g.ticketCount}</div>
              </div>
            ))}
          </div>
          <InsightBox color="#7c3aed" title="Leadership insight">
            {`Writing ${Math.min(gaps.length, 3)} documentation articles would prevent approximately ${gaps.slice(0,3).reduce((a,g)=>a+g.ticketCount,0)} future support tickets.`}
          </InsightBox>
        </>
      )}
    </div>
  );
}

// ── Shared components ─────────────────────────────────────────────────────────

function KpiCard({ label, value, color, sub }) {
  return (
    <div className="kpi-card">
      <div className="kpi-val" style={{color}}>{value}</div>
      <div className="kpi-label">{label}</div>
      {sub && <div className="kpi-sub">{sub}</div>}
    </div>
  );
}

function InsightBox({ color, title, children }) {
  return (
    <div className="insight-box" style={{borderLeftColor:color}}>
      <div className="insight-title" style={{color}}>💡 {title}</div>
      <div className="insight-body">{children}</div>
    </div>
  );
}

function TicketListCard({ title, tickets, badgeKey, badgeLabel, color }) {
  return (
    <div className="chart-card">
      <div className="chart-title">{title}</div>
      <div className="ticket-mini-list">
        {tickets.slice(0,6).map(t=>(
          <div key={t.ticketId} className="ticket-mini-row">
            <span className="src-id">{t.ticketId}</span>
            <span className="ticket-mini-tool">{t.toolName}</span>
            {t[badgeKey] && <span className="ticket-mini-badge" style={{background:color+"22",color,borderColor:color+"55"}}>{badgeLabel?`${badgeLabel}: `:" "}{t[badgeKey]}</span>}
          </div>
        ))}
        {tickets.length > 6 && <div className="ticket-mini-more">+{tickets.length-6} more</div>}
        {tickets.length === 0 && <div className="kb-empty-sub">None detected</div>}
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
        <select value={toolFilter} onChange={e=>setToolFilter(e.target.value)}>
          <option value="">All tools</option>
          {tools.map(t=><option key={t} value={t}>{t}</option>)}
        </select>
      </div>
      {loading ? <div className="kb-loading">Loading tickets…</div> : (
        <div className="browser-split">
          <div className="ticket-list">
            {tickets.map(t=>(
              <div key={t.ticketId} className={`ticket-row ${selectedTicket?.ticketId===t.ticketId?"selected":""}`} onClick={()=>setSelectedTicket(selectedTicket?.ticketId===t.ticketId?null:t)}>
                <div className="tr-top">
                  <span className="tr-id">{t.ticketId}</span>
                  <SevBadge severity={t.severity} />
                  {t.frustrationFlag && <span className="flag-badge frustration">😤</span>}
                  {t.recurrenceSignal && <span className="flag-badge recurring">🔄</span>}
                  {t.knowledgeGapFlag && <span className="flag-badge gap">📝</span>}
                </div>
                <div className="tr-tool">{t.toolName} · {t.category}</div>
                <div className="tr-pain">{t.painPoint}</div>
              </div>
            ))}
            {tickets.length===0 && <div className="kb-empty-sub" style={{padding:"2rem 1rem"}}>No tickets found</div>}
          </div>
          {selectedTicket && <TicketDetail ticket={selectedTicket} onClose={()=>setSelectedTicket(null)} />}
        </div>
      )}
    </div>
  );
}

function TicketDetail({ ticket, onClose }) {
  return (
    <div className="ticket-detail">
      <div className="detail-header">
        <div><span className="detail-id">{ticket.ticketId}</span><SevBadge severity={ticket.severity} /></div>
        <button className="detail-close" onClick={onClose}>✕</button>
      </div>
      <div className="detail-meta">
        <MetaChip label="Tool" value={ticket.toolName} />
        <MetaChip label="Category" value={ticket.category} />
        <MetaChip label="Priority" value={ticket.priority} />
        <MetaChip label="Status" value={ticket.status} />
        {ticket.resolutionType && <MetaChip label="Resolution" value={ticket.resolutionType} />}
        {ticket.sentimentScore && <MetaChip label="Sentiment" value={`${ticket.sentimentScore}/5`} />}
      </div>
      {ticket.rootCauseTool && ticket.rootCauseTool !== ticket.toolName && (
        <div className="detail-alert">⚠️ Root cause in: <strong>{ticket.rootCauseTool}</strong></div>
      )}
      {ticket.frustrationFlag && <div className="detail-flag frustration">😤 Developer frustration detected in comments</div>}
      {ticket.recurrenceSignal && <div className="detail-flag recurring">🔄 Likely to recur — root cause not fully addressed</div>}
      {ticket.knowledgeGapFlag && <div className="detail-flag gap">📝 Knowledge gap: {ticket.knowledgeGapDescription}</div>}
      {ticket.createdDate && <div className="detail-dates">Created: {ticket.createdDate}{ticket.resolvedDate?` · Resolved: ${ticket.resolvedDate}`:""}</div>}
      <div className="detail-section"><div className="detail-section-label">Pain Point</div><div className="detail-text">{ticket.painPoint}</div></div>
      <div className="detail-section"><div className="detail-section-label">Summary</div><div className="detail-text">{ticket.summary}</div></div>
    </div>
  );
}

function SevBadge({ severity }) {
  return <span className="sev-badge" style={{background:(SEVERITY_COLOR[severity]??"#94a3b8")+"22",color:SEVERITY_COLOR[severity]??"#94a3b8",borderColor:(SEVERITY_COLOR[severity]??"#94a3b8")+"55"}}>{severity}</span>;
}
function MetaChip({ label, value }) {
  return value?<span className="meta-chip"><span className="meta-label">{label}</span> {value}</span>:null;
}
