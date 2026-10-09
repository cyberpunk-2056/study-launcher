"use client";
import { useEffect, useMemo, useState } from "react";
import { Pill, Stat, ago, fmtMs } from "@/components/ui";

type Row = {
  id: string; name: string; approved: boolean; guardian_email: string | null; pairing_code: string | null; paired: boolean;
  device_name: string | null; device_model: string | null; os_version: string | null; setup_missing: string[];
  last_seen: string | null; created_at: string; online: boolean;
  study_minutes: number; break_minutes: number; mode: "study" | "break"; mode_forced: boolean; mode_left_ms: number | null;
  release_requested: boolean; usage_day: string | null; screen_ms: number; focus_min: number; alerts24h: number;
};

const FILTERS = ["All", "Online", "Offline", "Pending", "Alerts"] as const;

export default function Dashboard() {
  const [rows, setRows] = useState<Row[] | null>(null);
  const [error, setError] = useState("");
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState<(typeof FILTERS)[number]>("All");
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [addErr, setAddErr] = useState("");

  async function load() {
    const r = await fetch("/api/admin/students");
    const j = await r.json().catch(() => ({}));
    if (r.ok) { setRows(j); setError(""); } else setError(j.error ?? "Could not load students");
  }
  useEffect(() => { load(); const t = setInterval(load, 30_000); return () => clearInterval(t); }, []);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    setAddErr("");
    const r = await fetch("/api/admin/students", {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ name, password, guardianEmail: email }),
    });
    const j = await r.json();
    if (!r.ok) return setAddErr(j.error ?? "failed");
    window.location.href = `/students/${j.id}`;
  }

  const stats = useMemo(() => {
    const r = rows ?? [];
    const withUsage = r.filter((s) => s.screen_ms > 0);
    return {
      total: r.length,
      online: r.filter((s) => s.online).length,
      pending: r.filter((s) => !s.approved).length,
      alerts: r.reduce((n, s) => n + s.alerts24h, 0),
      avg: withUsage.length ? withUsage.reduce((n, s) => n + s.screen_ms, 0) / withUsage.length : 0,
    };
  }, [rows]);

  const list = (rows ?? []).filter((s) => {
    if (q && !s.name.toLowerCase().includes(q.toLowerCase())) return false;
    if (filter === "Online") return s.online;
    if (filter === "Offline") return !s.online;
    if (filter === "Pending") return !s.approved;
    if (filter === "Alerts") return s.alerts24h > 0;
    return true;
  });
  const pending = (rows ?? []).filter((s) => !s.approved);

  return (
    <>
      <div className="top">
        <h1>Dashboard</h1>
        <button className="primary" onClick={() => setAdding(!adding)}>{adding ? "Cancel" : "+ Add student"}</button>
      </div>
      {error && <p className="err">{error}</p>}

      {adding && (
        <form className="card" onSubmit={add}>
          <h2>Add a student</h2>
          <div className="row">
            <div className="grow"><label>Student name</label><input value={name} onChange={(e) => setName(e.target.value)} required /></div>
            <div className="grow"><label>Guardian e-mail (for password reset codes)</label><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="parent@example.com" /></div>
            <div className="grow"><label>Secret removal password (min 6)</label><input value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} autoComplete="off" /></div>
            <button className="primary end">Create</button>
          </div>
          {addErr && <p className="err">{addErr}</p>}
        </form>
      )}

      <div className="grid g4" style={{ marginBottom: 16 }}>
        <Stat value={stats.total} label="Students" />
        <Stat value={stats.online} label="Online now" />
        <Stat value={stats.alerts} label="Alerts in last 24 h" />
        <Stat value={stats.avg ? fmtMs(stats.avg) : "–"} label="Avg screen time (latest day)" />
      </div>

      {pending.length > 0 && (
        <div className="banner">
          <b>{pending.length} new student{pending.length > 1 ? "s" : ""} set up the launcher:</b>{" "}
          {pending.map((p, i) => (<span key={p.id}>{i ? ", " : ""}<a href={`/students/${p.id}?tab=security`}>{p.name}</a></span>))}
          . Review them, confirm the guardian e-mail, set a removal password and approve.
        </div>
      )}

      <div className="card">
        <div className="row" style={{ marginBottom: 12 }}>
          <div className="grow"><input placeholder="Search students…" value={q} onChange={(e) => setQ(e.target.value)} /></div>
          <div className="row" style={{ gap: 6 }}>
            {FILTERS.map((f) => (
              <button key={f} className={filter === f ? "primary" : ""} onClick={() => setFilter(f)}>{f}{f === "Pending" && stats.pending ? ` (${stats.pending})` : ""}</button>
            ))}
          </div>
        </div>
        {rows === null && !error ? <p className="muted">Loading…</p> : list.length === 0 ? <p className="muted">No students match.</p> : (
          <div style={{ overflowX: "auto" }}>
            <table>
              <thead><tr><th>Student</th><th>Status</th><th>Right now</th><th>Screen time</th><th className="hide-sm">Focus</th><th className="hide-sm">Last seen</th><th>Alerts</th></tr></thead>
              <tbody>
                {list.map((s) => (
                  <tr key={s.id} className="click" onClick={() => (window.location.href = `/students/${s.id}`)}>
                    <td>
                      <a href={`/students/${s.id}`}><b>{s.name}</b></a>
                      <div className="muted">{s.device_model ?? s.device_name ?? (s.paired ? "phone" : "not paired")}</div>
                    </td>
                    <td>
                      {!s.paired ? <Pill tone="warn">code {s.pairing_code}</Pill> : (
                        <><span className={`dot ${s.online ? "on" : ""}`} />{s.online ? "Online" : "Offline"}</>
                      )}
                      {!s.approved && <> <Pill tone="brand">new</Pill></>}
                      {s.paired && s.setup_missing.length > 0 && <> <Pill tone="warn">setup {s.setup_missing.length} left</Pill></>}
                      {s.release_requested && <> <Pill tone="warn">release pending</Pill></>}
                    </td>
                    <td>
                      {s.paired ? <Pill tone={s.mode}>{s.mode === "study" ? "Study" : "Break"}{s.mode_forced ? " (forced)" : s.mode_left_ms ? ` · ${fmtMs(s.mode_left_ms)}` : ""}</Pill> : "–"}
                    </td>
                    <td>{s.usage_day ? <>{fmtMs(s.screen_ms)} <span className="muted">{s.usage_day.slice(5)}</span></> : <span className="muted">no data</span>}</td>
                    <td className="hide-sm">{s.usage_day ? `${s.focus_min}m` : "–"}</td>
                    <td className="hide-sm muted">{ago(s.last_seen)}</td>
                    <td>{s.alerts24h > 0 ? <Pill tone="bad">{s.alerts24h}</Pill> : <span className="muted">0</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}
