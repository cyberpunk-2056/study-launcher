import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/supabase";
import { authDevice } from "@/lib/device";

type Day = {
  day?: unknown; screenMs?: unknown; focusMin?: unknown; pomodoros?: unknown; tasks?: unknown;
  apps?: { package?: unknown; label?: unknown; ms?: unknown }[];
};

const int = (v: unknown, max: number) => Math.max(0, Math.min(max, Math.round(Number(v) || 0)));

export async function POST(req: NextRequest) {
  const s = await authDevice(req);
  if (!s) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  const { days } = await req.json().catch(() => ({}));
  if (!Array.isArray(days)) return NextResponse.json({ error: "days[] required" }, { status: 400 });

  const rows = (days as Day[]).slice(0, 4).flatMap((d) => {
    if (typeof d.day !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(d.day)) return [];
    const apps = (Array.isArray(d.apps) ? d.apps : []).slice(0, 40).map((a) => ({
      package: String(a.package ?? "").slice(0, 200),
      label: String(a.label ?? a.package ?? "").slice(0, 80),
      ms: int(a.ms, 86_400_000),
    }));
    return [{
      student_id: s.id,
      day: d.day,
      screen_ms: int(d.screenMs, 86_400_000),
      apps,
      focus_min: int(d.focusMin, 1440),
      pomodoros: int(d.pomodoros, 200),
      tasks_done: int(d.tasks, 500),
      updated_at: new Date().toISOString(),
    }];
  });
  if (rows.length) {
    const { error } = await db().from("usage_daily").upsert(rows, { onConflict: "student_id,day" });
    if (error) return NextResponse.json({ error: error.message }, { status: 500 });
  }
  return NextResponse.json({ ok: true });
}
