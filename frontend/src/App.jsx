import { useState, useEffect } from "react";
import ChatTab from "./components/ChatTab";
import KnowledgeTab from "./components/KnowledgeTab";
import "./App.css";

const API_BASE = "http://localhost:8080/api";

export default function App() {
  const [activeTab, setActiveTab] = useState("chat");
  const [status, setStatus] = useState(null);
  const [tools, setTools] = useState([]);

  // Poll backend until ready, then fetch tool list once
  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const res = await fetch(`${API_BASE}/status`);
        const data = await res.json();
        if (cancelled) return;
        setStatus(data);
        if (data.ready) {
          const tr = await fetch(`${API_BASE}/tools`);
          if (!cancelled) setTools(await tr.json());
        } else {
          setTimeout(poll, 2500);
        }
      } catch {
        if (!cancelled) {
          setStatus({ ready: false, documentCount: 0, message: "Can't reach backend at :8080" });
          setTimeout(poll, 3000);
        }
      }
    }

    poll();
    return () => { cancelled = true; };
  }, []);

  return (
    <div className="app">
      <header className="app-header">
        <div className="header-brand">
          <span className="brand-dot" />
          Developer Tools Intelligence
        </div>

        <nav className="tab-nav">
          <button
            className={`tab-btn ${activeTab === "chat" ? "active" : ""}`}
            onClick={() => setActiveTab("chat")}
          >
            Chat
          </button>
          <button
            className={`tab-btn ${activeTab === "knowledge" ? "active" : ""}`}
            onClick={() => setActiveTab("knowledge")}
          >
            Knowledge Base
          </button>
        </nav>

        <div className="header-right">
          {status?.ready ? (
            <span className="status-pill ready">{status.documentCount} indexed</span>
          ) : (
            <span className="status-pill pending">{status?.message ?? "Connecting…"}</span>
          )}
        </div>
      </header>

      <div className="tab-content">
        {activeTab === "chat" ? (
          <ChatTab status={status} tools={tools} apiBase={API_BASE} />
        ) : (
          <KnowledgeTab status={status} tools={tools} apiBase={API_BASE} />
        )}
      </div>
    </div>
  );
}
