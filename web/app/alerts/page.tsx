"use client";
import { useEffect, useState } from "react";
import { Pill, SEV, ago } from "@/components/ui";

type Alert = { id: number; type: string; detail: string | null; created_at: string; student_id: string; student: string | null };
type Student = { id: string; name: string; approved: boolean; guardian_email: string | null; created_at: string; device_model: string | null };

const LABEL: Record<string, string> = {
  protection_lost: "Protection switched off", admin_disabled: "Device admin removed", password_lockout: "Password lockout",
  clock_changed: "Clock changed", released: "Protection released", password_wrong: "Wrong password",
  blocked_settings: "Blocked a Settings screen", admin_disable_requested: "Tried to remove device admin",
  forgot_password_requested: "Forgot-password requested", enrolled: "New student enrolled", paired: "Phone paired",
};

export default function Alerts() {
  const [alerts, setAlerts] = useState<Alert[] | null>(null);
  const [pending, setPending] = useState<Student[]>([]);
  const [sev, setSev] = useState<"all" | "high">("all");

  async function load() {
    const [a, s] = await Promise.all([fetch("/api/admin/alerts"), fetch("/api/admin/students")]);
    if (a.ok) setAlerts(await a.json());
    if (s.ok) setPending((await s.json()).filter((x: Student) => !x.approved));
  }
  useEffect(() => { load(); const t = setInterval(load, 30_000); return () => clearInterval(t); }, []);

  const shown = (alerts ?? []).filter((a) => sev === "all" || SEV[a.type] === "high");

  return (
    <>
      <div className="top"><h1>Alerts &amp; approvals</h1></div>

      <div className="card">
        <h2>Waiting for approval ({pending.length})</h2>
        {pending.length === 0 ? <p className="muted">No new students waiting.</p> : (
          <table>
            <tbody>
              {pending.map((p) => (
                <tr key={p.id}>
                  <td><b>{p.name}</b><div className="muted">{p.device_model ?? "phone"} · joined {ago(p.created_at)}</div></td>
                  <td>{p.guardian_email ?? <span className="muted">no guardian e-mail</span>}</td>
                  <td style={{ textAlign: "right" }}><a href={`/students/${p.id}?tab=security`}><button className="primary">Review &amp; approve</button></a></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <div className="row" style={{ marginBottom: 12 }}>
          <h2 style={{ margin: 0, flex: 1 }}>Last 7 days</h2>
          <button className={sev === "all" ? "primary" : ""} onClick={() => setSev("all")}>All</button>
          <button className={sev === "high" ? "primary" : ""} onClick={() => setSev("high")}>High severity</button>
        </div>
        {alerts === null ? <p className="muted">Loading…</p> : shown.length === 0 ? <p className="muted">Nothing to report. 🎉</p> : (
          <table>
            <tbody>
              {shown.map((a) => (
                <tr key={a.id} className={SEV[a.type] === "high" ? "sev-high" : SEV[a.type] === "med" ? "sev-med" : ""}>
                  <td className="muted" style={{ whiteSpace: "nowrap" }}>{new Date(a.created_at).toLocaleString()}</td>
                  <td><a href={`/students/${a.student_id}`}>{a.student ?? "?"}</a></td>
                  <td><Pill tone={SEV[a.type] === "high" ? "bad" : SEV[a.type] === "med" ? "warn" : undefined}>{LABEL[a.type] ?? a.type}</Pill></td>
                  <td className="muted">{a.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
