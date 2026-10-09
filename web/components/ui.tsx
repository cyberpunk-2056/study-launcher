"use client";
import { ReactNode } from "react";

export function fmtMs(ms: number): string {
  const m = Math.round(ms / 60000);
  if (m < 1) return ms > 0 ? "<1m" : "0m";
  const h = Math.floor(m / 60);
  return h ? `${h}h ${m % 60}m` : `${m}m`;
}

export function ago(iso: string | null): string {
  if (!iso) return "never";
  const m = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (m < 1) return "just now";
  if (m < 60) return `${m} min ago`;
  if (m < 1440) return `${Math.round(m / 60)} h ago`;
  return `${Math.round(m / 1440)} d ago`;
}

export function Pill({ tone, children }: { tone?: "ok" | "warn" | "bad" | "study" | "break" | "brand"; children: ReactNode }) {
  return <span className={`pill ${tone ?? ""}`}>{children}</span>;
}

export function Stat({ value, label }: { value: ReactNode; label: string }) {
  return <div className="stat"><b>{value}</b><span>{label}</span></div>;
}

/** Vertical bar chart. `value` is any unit; `label` is shown under the bar, `text` above it. */
export function Bars({ data, alt }: { data: { label: string; value: number; text: string }[]; alt?: boolean }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  return (
    <div className="bars">
      {data.map((d, i) => (
        <div className="col" key={i}>
          <span className="val">{d.text}</span>
          <div className={`bar ${alt ? "alt" : ""}`} style={{ height: `${Math.max(1, (d.value / max) * 125)}px` }} />
          <small>{d.label}</small>
        </div>
      ))}
    </div>
  );
}

export function HBars({ data }: { data: { name: string; value: number; text: string }[] }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  return (
    <div>
      {data.map((d) => (
        <div className="hbar" key={d.name}>
          <span className="name" title={d.name}>{d.name}</span>
          <div className="track"><div className="fill" style={{ width: `${(d.value / max) * 100}%` }} /></div>
          <span className="t">{d.text}</span>
        </div>
      ))}
    </div>
  );
}

export const SEV: Record<string, "high" | "med"> = {
  protection_lost: "high", admin_disabled: "high", password_lockout: "high", clock_changed: "high", released: "high",
  password_wrong: "med", blocked_settings: "med", admin_disable_requested: "med", forgot_password_requested: "med",
};
