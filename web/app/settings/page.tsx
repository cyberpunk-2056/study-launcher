"use client";
import { useEffect, useState } from "react";
import { Pill } from "@/components/ui";

type S = { smtpConfigured: boolean; smtpHost: string | null; smtpFrom: string | null; enrollEnabled: boolean; supabaseConfigured: boolean };

export default function Settings() {
  const [s, setS] = useState<S | null>(null);
  const [to, setTo] = useState("");
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => { fetch("/api/admin/settings").then((r) => r.json()).then(setS); }, []);

  async function test(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setMsg(null);
    const r = await fetch("/api/admin/settings/test-email", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ to }) });
    const j = await r.json().catch(() => ({}));
    setMsg(r.ok ? { ok: true, text: "Sent. Check the inbox (and spam)." } : { ok: false, text: j.error ?? "Failed" });
    setBusy(false);
  }

  return (
    <>
      <div className="top"><h1>Settings</h1></div>
      <div className="card">
        <h2>Server status</h2>
        {!s ? <p className="muted">Loading…</p> : (
          <table>
            <tbody>
              <tr><td>Database (Supabase)</td><td>{s.supabaseConfigured ? <Pill tone="ok">connected</Pill> : <Pill tone="bad">not configured</Pill>}</td><td className="muted">SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY</td></tr>
              <tr><td>E-mail (SMTP)</td><td>{s.smtpConfigured ? <Pill tone="ok">{s.smtpHost}</Pill> : <Pill tone="warn">not configured</Pill>}</td><td className="muted">SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, SMTP_FROM</td></tr>
              <tr><td>Student self-enrollment</td><td>{s.enrollEnabled ? <Pill tone="ok">enabled</Pill> : <Pill>off</Pill>}</td><td className="muted">ENROLL_CODE</td></tr>
            </tbody>
          </table>
        )}
        <p className="muted">These are environment variables. On Vercel: Project → Settings → Environment Variables, then redeploy.</p>
      </div>

      <form className="card" onSubmit={test}>
        <h2>Test the e-mail setup</h2>
        <p className="muted">Password reset codes are sent from {s?.smtpFrom ?? "your SMTP account"} to each student&apos;s guardian e-mail. Send yourself a test first.</p>
        <div className="row">
          <div className="grow"><input type="email" value={to} onChange={(e) => setTo(e.target.value)} placeholder="you@example.com" required /></div>
          <button className="primary" disabled={busy || !s?.smtpConfigured}>{busy ? "Sending…" : "Send test e-mail"}</button>
        </div>
        {msg && <p className={msg.ok ? "okmsg" : "err"}>{msg.text}</p>}
      </form>

      <div className="card">
        <h2>How password reset works</h2>
        <ol className="muted" style={{ paddingLeft: 18, margin: 0 }}>
          <li>The student taps <b>🔒 → Forgot password</b> on the launcher.</li>
          <li>An 8-digit code (valid 15 min, 5 tries) is e-mailed to the <b>guardian e-mail you approved</b> – never to the student.</li>
          <li>The guardian shares the code if they agree; the student enters it and chooses a new password.</li>
          <li>You can also set a password directly, or e-mail a freshly generated one, from the student&apos;s <b>Security</b> tab.</li>
        </ol>
      </div>
    </>
  );
}
