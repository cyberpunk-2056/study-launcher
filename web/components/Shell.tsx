"use client";
import { ReactNode, useEffect, useState } from "react";
import { usePathname } from "next/navigation";

export default function Shell({ children }: { children: ReactNode }) {
  const path = usePathname();
  const [alerts, setAlerts] = useState(0);

  useEffect(() => {
    if (path === "/login") return;
    fetch("/api/admin/students").then((r) => (r.ok ? r.json() : [])).then((rows: { alerts24h: number; approved: boolean }[]) =>
      setAlerts(rows.reduce((n, s) => n + (s.alerts24h > 0 ? 1 : 0) + (s.approved ? 0 : 1), 0))
    ).catch(() => {});
  }, [path]);

  if (path === "/login") return <>{children}</>;

  const link = (href: string, label: string, badge?: number) => (
    <a className={`nav ${path === href || (href !== "/" && path.startsWith(href)) ? "on" : ""}`} href={href}>
      {label}{badge ? <span className="badge">{badge}</span> : null}
    </a>
  );

  return (
    <div className="shell">
      <aside className="side">
        <div className="brand"><i />Study Launcher</div>
        {link("/", "Dashboard")}
        {link("/alerts", "Alerts & approvals", alerts || undefined)}
        {link("/settings", "Settings")}
        <div className="grow" />
        <button className="nav" onClick={async () => { await fetch("/api/admin/logout", { method: "POST" }); window.location.href = "/login"; }}>
          Sign out
        </button>
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
