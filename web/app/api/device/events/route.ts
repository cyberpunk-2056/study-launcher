import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/supabase";
import { authDevice } from "@/lib/device";

export async function POST(req: NextRequest) {
  const s = await authDevice(req);
  if (!s) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  const { events } = await req.json().catch(() => ({}));
  if (!Array.isArray(events)) return NextResponse.json({ error: "events[] required" }, { status: 400 });
  const rows = events.slice(0, 100).map((e: { type?: unknown; detail?: unknown; time?: unknown }) => ({
    student_id: s.id,
    type: String(e.type ?? "unknown").slice(0, 60),
    detail: e.detail == null ? null : String(e.detail).slice(0, 500),
    device_time: typeof e.time === "number" ? new Date(e.time).toISOString() : null,
  }));
  if (rows.length) await db().from("events").insert(rows);
  return NextResponse.json({ ok: true });
}
