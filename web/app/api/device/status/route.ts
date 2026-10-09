import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/supabase";
import { authDevice } from "@/lib/device";

// Heartbeat with device info + which setup permissions are still missing.
export async function POST(req: NextRequest) {
  const s = await authDevice(req);
  if (!s) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  const b = await req.json().catch(() => ({}));
  const str = (v: unknown, n = 80) => (typeof v === "string" ? v.slice(0, n) : null);
  await db()
    .from("students")
    .update({
      device_model: str(b.model),
      os_version: str(b.os, 30),
      app_version: str(b.appVersion, 20),
      setup_missing: Array.isArray(b.missing) ? b.missing.map((x: unknown) => String(x).slice(0, 30)).slice(0, 12) : [],
      last_seen: new Date().toISOString(),
    })
    .eq("id", s.id);
  return NextResponse.json({ ok: true });
}
