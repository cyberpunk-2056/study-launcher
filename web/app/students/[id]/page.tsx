"use client";
import { useCallback, useEffect, useState } from "react";
import { Bars, HBars, Pill, SEV, Stat, ago, fmtMs } from "@/components/ui";

type UsageApp = { package: string; label: string; ms: number };
type Usage = { day: string; screen_ms: number; apps: UsageApp[]; focus_min: number; pomodoros: number; tasks_done: number };
type Detail = {
  id: string; name: string; notes: string | null; message: string | null; approved: boolean; guardian_email: string | null;
  pairing_code: string | null; paired: boolean; online: boolean; device_name: string | null; device_model: string | null;
  os_version: string | null; app_version: string | null; setup_missing: string[]; last_seen: string | null; created_at: string;
  allowed_packages: string[]; study_minutes: number; break_minutes: number; cycle_anchor: string;
  force_mode: "study" | "break" | null; release_requested: boolean; locked_until: string | null; failed_attempts: number;
  mode: "study" | "break"; mode_forced: boolean; mode_left_ms: number | null;
  apps: { package_name: string; label: string }[];
  events: { id: number; type: string; detail: string | null; created_at: string }[];
  usage: Usage[];
};
type Peer = { id: string; name: string };

const TABS = ["overview", "apps", "schedule", "security", "activity"] as const;
type Tab = (typeof TABS)[number];
const TAB_LABEL: Record<Tab, string> = { overview: "Overview", apps: "Allowed apps", schedule: "Schedule", security: "Security & password", activity: "Activity log" };

export default function StudentPage({ params }: { params: { id: string } }) {
  const id = params.id;
  const [d, setD] = useState<Detail | null>(null);
  const [tab, setTab] = useState<Tab>("overview");
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const [allowed, setAllowed] = useState<Set<string>>(new Set());
  const [study, setStudy] = useState(180);
  const [brk, setBrk] = useState(60);
  const [filter, setFilter] = useState("");
  const [manual, setManual] = useState("");
  const [peers, setPeers] = useState<Peer[]>([]);
  const [newPw, setNewPw] = useState("");
  const [email, setEmail] = useState("");
  const [note, setNote] = useState("");
  const [announce, setAnnounce] = useState("");
  const [offline, setOffline] = useState<{ code: string; expiresInSeconds: number } | null>(null);
  const [evFilter, setEvFilter] = useState("all");

  const load = useCallback(async () => {
    const r = await fetch(`/api/admin/students/${id}`);
    if (!r.ok) return setMsg({ ok: false, text: "Could not load student" });
    const j: Detail = await r.json();
    setD(j);
    setAllowed(new Set(j.allowed_packages));
    setStudy(j.study_minutes); setBrk(j.break_minutes);
    setEmail(j.guardian_email ?? ""); setNote(j.notes ?? ""); setAnnounce(j.message ?? "");
  }, [id]);

  useEffect(() => {
    const t = new URLSearchParams(window.location.search).get("tab") as Tab | null;
    if (t && TABS.includes(t)) setTab(t);
    load();
    fetch("/api/admin/students").then((r) => (r.ok ? r.json() : [])).then((rows: Peer[]) => setPeers(rows.filter((p) => p.id !== id)));
    const t2 = setInterval(load, 30_000);
    return () => clearInterval(t2);
  }, [id, load]);

  async function patch(body: object, ok = "Saved") {
    setMsg(null);
    const r = await fetch(`/api/admin/students/${id}`, { method: "PATCH", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
    const j = await r.json().catch(() => ({}));
    setMsg({ ok: r.ok, text: r.ok ? ok : j.error ?? "Failed" });
    if (r.ok) load();
  }

  async function post(url: string, ok: string) {
    setMsg(null);
    const r = await fetch(url, { method: "POST" });
    const j = await r.json().catch(() => ({}));
    setMsg({ ok: r.ok, text: r.ok ? ok.replace("{to}", j.to ?? "") : j.error ?? "Failed" });
    if (r.ok) load();
  }

  async function showOffline() {
    const r = await fetch(`/api/admin/students/${id}/offline-code`);
    if (r.ok) setOffline(await r.json());
  }

  async function remove() {
    if (!confirm("Delete this student and all their data? The phone keeps its protection until released.")) return;
    await fetch(`/api/admin/students/${id}`, { method: "DELETE" });
    window.location.href = "/";
  }

  if (!d) return <p className="muted">{msg?.text ?? "Loading…"}</p>;

  const today = d.usage[0];
  const week = [...d.usage.slice(0, 14)].reverse();
  const weekTotal = d.usage.slice(0, 7).reduce((n, u) => n + u.screen_ms, 0);
  const avg = d.usage.length ? weekTotal / Math.min(7, d.usage.length) : 0;

  const known = new Map(d.apps.map((a) => [a.package_name, a.label]));
  const extra = [...allowed].filter((p) => !known.has(p));
  const list = [...d.apps.map((a) => ({ p: a.package_name, l: a.label })), ...extra.map((p) => ({ p, l: p }))]
    .filter((a) => !filter || a.l.toLowerCase().includes(filter.toLowerCase()) || a.p.toLowerCase().includes(filter.toLowerCase()));
  const toggle = (p: string) => setAllowed((s) => { const n = new Set(s); n.has(p) ? n.delete(p) : n.add(p); return n; });

  const events = d.events.filter((e) => evFilter === "all" || (evFilter === "alerts" ? !!SEV[e.type] : e.type === evFilter));
  const evTypes = [...new Set(d.events.map((e) => e.type))];
  const locked = d.locked_until && new Date(d.locked_until) > new Date();

  return (
    <>
      <div className="top">
        <div>
          <a href="/" className="muted">← Dashboard</a>
          <h1 style={{ marginTop: 4 }}>
            {d.name}{" "}
            {d.paired && (d.online ? <Pill tone="ok">online</Pill> : <Pill>offline · {ago(d.last_seen)}</Pill>)}{" "}
            {d.paired && <Pill tone={d.mode}>{d.mode === "study" ? "Study" : "Break"}{d.mode_forced ? " (forced)" : d.mode_left_ms ? ` · ${fmtMs(d.mode_left_ms)} left` : ""}</Pill>}
          </h1>
        </div>
        {msg && <span className={msg.ok ? "okmsg" : "err"}>{msg.text}</span>}
      </div>

      {!d.approved && (
        <div className="banner">
          <b>New student – not reviewed yet.</b> They enrolled themselves from the phone. Check the guardian e-mail below belongs to a parent/admin
          (not the student), set a removal password, then approve. Forgot-password stays disabled until you approve.
          <div style={{ marginTop: 8 }}><button className="primary" onClick={() => setTab("security")}>Go to Security</button></div>
        </div>
      )}
      {d.paired && d.setup_missing.length > 0 && (
        <div className="banner">Setup not finished on the phone. Still missing: <b>{d.setup_missing.join(", ")}</b>. The launcher stays locked on its setup screen until the student grants them.</div>
      )}

      <div className="tabs">
        {TABS.map((t) => <button key={t} className={tab === t ? "on" : ""} onClick={() => setTab(t)}>{TAB_LABEL[t]}</button>)}
      </div>

      {tab === "overview" && (
        <>
          <div className="grid g4" style={{ marginBottom: 16 }}>
            <Stat value={today ? fmtMs(today.screen_ms) : "–"} label={`Screen time ${today ? `(${today.day.slice(5)})` : ""}`} />
            <Stat value={d.usage.length ? fmtMs(avg) : "–"} label="Daily average (7 days)" />
            <Stat value={today ? `${today.focus_min}m` : "–"} label="Focus (pomodoro) today" />
            <Stat value={today ? today.tasks_done : "–"} label="Tasks done today" />
          </div>

          <div className="grid g2">
            <div className="card">
              <h2>Screen time – last 14 days</h2>
              {week.length === 0 ? <p className="muted">No usage reported yet. It appears a few minutes after the phone finishes setup.</p> : (
                <Bars data={week.map((u) => ({ label: u.day.slice(8), value: u.screen_ms, text: fmtMs(u.screen_ms) }))} />
              )}
              <p className="muted">Total time apps were in the foreground (the launcher itself is excluded).</p>
            </div>
            <div className="card">
              <h2>Focus minutes – last 14 days</h2>
              {week.length === 0 ? <p className="muted">–</p> : (
                <Bars alt data={week.map((u) => ({ label: u.day.slice(8), value: u.focus_min, text: `${u.focus_min}` }))} />
              )}
              <p className="muted">Completed pomodoro focus sessions.</p>
            </div>
          </div>

          <div className="card">
            <h2>Most used apps {today ? `– ${today.day}` : ""}</h2>
            {!today || today.apps.length === 0 ? <p className="muted">No app data yet.</p> : (
              <HBars data={[...today.apps].sort((a, b) => b.ms - a.ms).slice(0, 10).map((a) => ({ name: a.label, value: a.ms, text: fmtMs(a.ms) }))} />
            )}
            {d.usage.length > 0 && <p><a href={`/api/admin/students/${id}/usage-csv`}><button>Download usage CSV</button></a></p>}
          </div>

          <div className="grid g2">
            <div className="card">
              <h2>Phone</h2>
              {d.paired ? (
                <table><tbody>
                  <tr><td className="muted">Device</td><td>{d.device_model ?? d.device_name ?? "–"}</td></tr>
                  <tr><td className="muted">Android</td><td>{d.os_version ?? "–"}</td></tr>
                  <tr><td className="muted">Launcher version</td><td>{d.app_version ?? "–"}</td></tr>
                  <tr><td className="muted">Last seen</td><td>{d.last_seen ? new Date(d.last_seen).toLocaleString() : "never"}</td></tr>
                  <tr><td className="muted">Joined</td><td>{new Date(d.created_at).toLocaleDateString()}</td></tr>
                </tbody></table>
              ) : (
                <p>Not paired. Enter this code in the launcher: <span className="code">{d.pairing_code}</span></p>
              )}
            </div>
            <div className="card">
              <h2>Message to student</h2>
              <textarea value={announce} maxLength={280} onChange={(e) => setAnnounce(e.target.value)} placeholder="Shown on the launcher home screen, e.g. “Mock test on Friday – revise chapters 3–5”" />
              <p><button className="primary" onClick={() => patch({ message: announce }, "Message saved")}>Save message</button></p>
              <label>Private notes (only you see this)</label>
              <textarea value={note} onChange={(e) => setNote(e.target.value)} />
              <p><button onClick={() => patch({ notes: note }, "Notes saved")}>Save notes</button></p>
            </div>
          </div>
        </>
      )}

      {tab === "apps" && (
        <div className="card">
          <h2>Apps allowed during study time ({allowed.size})</h2>
          {d.apps.length === 0 && <p className="muted">The phone hasn&apos;t reported its installed apps yet. You can still add package names manually.</p>}
          <div className="row" style={{ marginBottom: 10 }}>
            <div className="grow"><input placeholder="Search apps…" value={filter} onChange={(e) => setFilter(e.target.value)} /></div>
            <div className="grow"><input placeholder="Add package, e.g. com.phonepe.app" value={manual} onChange={(e) => setManual(e.target.value)} /></div>
            <button onClick={() => { if (manual.trim()) { toggle(manual.trim()); setManual(""); } }}>Add</button>
          </div>
          <div className="apps">
            {list.map((a) => <label key={a.p}><input type="checkbox" checked={allowed.has(a.p)} onChange={() => toggle(a.p)} />{a.l}</label>)}
          </div>
          <div className="row" style={{ marginTop: 14 }}>
            <button className="primary" onClick={() => patch({ allowed_packages: [...allowed] })}>Save allowed apps</button>
            {peers.length > 0 && (
              <select style={{ width: "auto" }} defaultValue="" onChange={(e) => { if (e.target.value && confirm("Replace this student's allowed apps with the other student's list?")) patch({ copy_apps_from: e.target.value }, "Apps copied"); e.target.value = ""; }}>
                <option value="">Copy list from another student…</option>
                {peers.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            )}
          </div>
          <p className="muted">The emergency dialer, this launcher, system UI and keyboards are always allowed, whatever you choose.</p>
        </div>
      )}

      {tab === "schedule" && (
        <div className="card">
          <h2>Study / break schedule</h2>
          <div className="row">
            <div><label>Study (minutes)</label><input type="number" min={1} max={1440} value={study} onChange={(e) => setStudy(+e.target.value)} /></div>
            <div><label>Break (minutes)</label><input type="number" min={1} max={1440} value={brk} onChange={(e) => setBrk(+e.target.value)} /></div>
            <button className="primary end" onClick={() => patch({ study_minutes: study, break_minutes: brk })}>Save</button>
            <button className="end" onClick={() => patch({ restart_cycle: true }, "Cycle restarted: study starts now")}>Restart cycle now</button>
          </div>
          <h3 style={{ marginTop: 20 }}>Override right now</h3>
          <div className="row">
            <button className={d.force_mode === "study" ? "primary" : ""} onClick={() => patch({ force_mode: "study" }, "Forced study mode")}>Force study</button>
            <button className={d.force_mode === "break" ? "primary" : ""} onClick={() => patch({ force_mode: "break" }, "Forced break")}>Force break</button>
            <button className={!d.force_mode ? "primary" : ""} onClick={() => patch({ force_mode: null }, "Following the schedule")}>Follow schedule</button>
          </div>
          <p className="muted">The cycle runs on the server clock, so changing the phone&apos;s time does nothing. Cycle started {new Date(d.cycle_anchor).toLocaleString()}.</p>
        </div>
      )}

      {tab === "security" && (
        <>
          <div className="grid g2">
            <div className="card">
              <h2>Guardian e-mail</h2>
              <p className="muted">Forgot-password codes are sent here – only to this address, which only you can change. Use a parent&apos;s or your own address, never the student&apos;s.</p>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="parent@example.com" />
              <p className="row">
                <button className="primary" onClick={() => patch({ guardian_email: email }, "Guardian e-mail saved")}>Save e-mail</button>
                {!d.approved && <button className="primary" disabled={!email} onClick={() => patch({ approved: true, guardian_email: email }, "Student approved")}>Approve student</button>}
                {d.approved && <Pill tone="ok">approved</Pill>}
              </p>
            </div>
            <div className="card">
              <h2>Removal password</h2>
              <p className="muted">Needed to remove the launcher or open Settings on the phone.</p>
              <div className="row">
                <div className="grow"><input value={newPw} onChange={(e) => setNewPw(e.target.value)} placeholder="New password (min 6)" autoComplete="off" /></div>
                <button className="primary" disabled={newPw.length < 6} onClick={() => { patch({ password: newPw }, "Password changed"); setNewPw(""); }}>Set / reset</button>
              </div>
              <p className="row">
                <button disabled={!d.guardian_email} onClick={() => confirm(`Generate a new random password and e-mail it to ${d.guardian_email}?`) && post(`/api/admin/students/${id}/email-password`, "New password e-mailed to {to}")}>
                  Generate &amp; e-mail to guardian
                </button>
              </p>
              {locked && <p className="err">Password entry is locked until {new Date(d.locked_until!).toLocaleTimeString()} ({d.failed_attempts} wrong tries). <button onClick={() => patch({ clear_lockout: true }, "Lockout cleared")}>Clear lockout</button></p>}
            </div>
          </div>

          <div className="grid g2">
            <div className="card">
              <h2>Offline unlock code</h2>
              <p className="muted">Phone has no internet? Read out this one-time code. It works in the password box and is valid for the current 10-minute window.</p>
              <div className="row">
                <button onClick={showOffline}>Show code</button>
                {offline && <><span className="code">{offline.code}</span><span className="muted">~{Math.ceil(offline.expiresInSeconds / 60)} min left</span></>}
              </div>
            </div>
            <div className="card">
              <h2>Phone control</h2>
              <div className="row">
                {d.paired && <button onClick={() => confirm("Detach this phone and issue a new pairing code?") && patch({ repair: true }, "Phone detached")}>Re-pair phone</button>}
                <button className={d.release_requested ? "" : "danger"} onClick={() => patch({ release_requested: !d.release_requested }, d.release_requested ? "Release cancelled" : "Release armed")}>
                  {d.release_requested ? "Cancel release" : "Release phone"}
                </button>
              </div>
              <p className="muted">Release tells the phone, on its next sync, to drop protection so the launcher can be uninstalled.</p>
            </div>
          </div>
          <button className="danger" onClick={remove}>Delete student</button>
        </>
      )}

      {tab === "activity" && (
        <div className="card">
          <div className="row" style={{ marginBottom: 12 }}>
            <h2 style={{ margin: 0, flex: 1 }}>Activity &amp; tamper log</h2>
            <select style={{ width: "auto" }} value={evFilter} onChange={(e) => setEvFilter(e.target.value)}>
              <option value="all">All events</option><option value="alerts">Alerts only</option>
              {evTypes.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          {events.length === 0 ? <p className="muted">Nothing yet.</p> : (
            <table><tbody>
              {events.map((e) => (
                <tr key={e.id} className={SEV[e.type] === "high" ? "sev-high" : SEV[e.type] === "med" ? "sev-med" : ""}>
                  <td className="muted" style={{ whiteSpace: "nowrap" }}>{new Date(e.created_at).toLocaleString()}</td>
                  <td><Pill tone={SEV[e.type] === "high" ? "bad" : SEV[e.type] === "med" ? "warn" : undefined}>{e.type}</Pill></td>
                  <td className="muted">{e.detail}</td>
                </tr>
              ))}
            </tbody></table>
          )}
        </div>
      )}
    </>
  );
}
