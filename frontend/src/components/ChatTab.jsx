import { useState, useRef, useEffect } from "react";

export default function ChatTab({ status, tools, apiBase }) {
  const [messages, setMessages] = useState([
    {
      role: "assistant",
      content: "Ask me about issues developers have run into with internal tools — I'll search resolved tickets from the pgvector knowledge base and answer based on what's actually been reported and fixed.",
      sources: [],
    },
  ]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [toolFilter, setToolFilter] = useState("");
  const scrollRef = useRef(null);
  const textareaRef = useRef(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages, loading]);

  async function send() {
    const q = input.trim();
    if (!q || loading) return;
    setMessages(prev => [...prev, { role: "user", content: q }]);
    setInput("");
    setLoading(true);

    try {
      const res = await fetch(`${apiBase}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question: q, toolFilter: toolFilter || null }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setMessages(prev => [...prev, {
        role: "assistant",
        content: data.answer,
        sources: data.sources ?? [],
      }]);
    } catch (err) {
      setMessages(prev => [...prev, {
        role: "assistant",
        content: `Error: ${err.message}. Is the Spring Boot backend running on port 8080?`,
        sources: [],
        isError: true,
      }]);
    } finally {
      setLoading(false);
    }
  }

  function onKey(e) {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); }
  }

  // Auto-resize textarea
  function onInput(e) {
    setInput(e.target.value);
    e.target.style.height = "auto";
    e.target.style.height = Math.min(e.target.scrollHeight, 120) + "px";
  }

  const disabled = !status?.ready;

  return (
    <div className="chat-layout">
      <div className="chat-toolbar">
        <label>Filter by tool</label>
        <select value={toolFilter} onChange={e => setToolFilter(e.target.value)} disabled={disabled}>
          <option value="">All tools</option>
          {tools.map(t => <option key={t} value={t}>{t}</option>)}
        </select>
      </div>

      <div className="messages" ref={scrollRef}>
        {messages.map((m, i) => <Bubble key={i} m={m} />)}
        {loading && (
          <div className="msg assistant">
            <div className="bubble typing">
              <span /><span /><span />
            </div>
          </div>
        )}
      </div>

      <div className="composer">
        <textarea
          ref={textareaRef}
          rows={1}
          value={input}
          disabled={disabled}
          onChange={onInput}
          onKeyDown={onKey}
          placeholder={disabled
            ? "Waiting for ingestion to finish…"
            : 'Ask a question, e.g. "Why does CI/CD fail on token expiry?"'}
        />
        <button onClick={send} disabled={disabled || loading || !input.trim()}>Send</button>
      </div>
    </div>
  );
}

function Bubble({ m }) {
  return (
    <div className={`msg ${m.role}`}>
      <div className={`bubble ${m.isError ? "err" : ""}`}>
        <p>{m.content}</p>
        {m.sources?.length > 0 && (
          <div className="sources">
            <div className="sources-label">Sources from pgvector</div>
            {m.sources.map(s => (
              <div className="src-card" key={s.ticketId}>
                <div className="src-top">
                  <span className="src-id">{s.ticketId}</span>
                  <span className="src-score">{Math.round(s.similarityScore * 100)}% match</span>
                </div>
                <div className="src-meta">{s.toolName} · {s.category}</div>
                <div className="src-pain">{s.painPoint}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
