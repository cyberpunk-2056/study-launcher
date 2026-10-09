import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/supabase";

export const dynamic = "force-dynamic";

export async function GET(_: NextRequest, { params }: { params: { id: string } }) {
  const { data: s } = await db().from("students").select("name").eq("id", params.id).maybeSingle();
  if (!s) return NextResponse.json({ error: "not found" }, { status: 404 });
  const { data } = await db().from("usage_daily").select("*").eq("student_id", params.id).order("day", { ascending: false });
  const esc = (v: string) => `"${v.replace(/"/g, '""')}"`;
  const lines = ["day,screen_minutes,focus_minutes,pomodoros,tasks_done,top_apps"];
  for (const r of data ?? []) {
    const top = (r.apps as { label: string; ms: number }[]).slice(0, 10).map((a) => `${a.label} ${Math.round(a.ms / 60000)}m`).join("; ");
    lines.push([r.day, Math.round(r.screen_ms / 60000), r.focus_min, r.pomodoros, r.tasks_done, esc(top)].join(","));
  }
  return new NextResponse(lines.join("\n"), {
    headers: { "content-type": "text/csv", "content-disposition": `attachment; filename="${s.name.replace(/[^\w-]+/g, "_")}-usage.csv"` },
  });
}
