import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/supabase";
import { authDevice } from "@/lib/device";

// The phone reports what is installed so the admin can pick allowed apps from a real list.
export async function POST(req: NextRequest) {
  const s = await authDevice(req);
  if (!s) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  const { apps } = await req.json().catch(() => ({}));
  if (!Array.isArray(apps)) return NextResponse.json({ error: "apps[] required" }, { status: 400 });
  const rows = apps.slice(0, 1000).map((a: { package?: unknown; label?: unknown }) => ({
    student_id: s.id,
    package_name: String(a.package ?? "").slice(0, 200),
    label: String(a.label ?? a.package ?? "").slice(0, 120),
  })).filter((r) => r.package_name);
  await db().from("device_apps").delete().eq("student_id", s.id);
  if (rows.length) await db().from("device_apps").insert(rows);
  return NextResponse.json({ ok: true });
}
