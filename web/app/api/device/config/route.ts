import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/supabase";
import { authDevice } from "@/lib/device";

export const dynamic = "force-dynamic";

export async function GET(req: NextRequest) {
  const s = await authDevice(req);
  if (!s) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  await db().from("students").update({ last_seen: new Date().toISOString() }).eq("id", s.id);
  return NextResponse.json({
    serverTime: Date.now(),
    studentName: s.name,
    studyMinutes: s.study_minutes,
    breakMinutes: s.break_minutes,
    cycleAnchor: new Date(s.cycle_anchor).getTime(),
    forceMode: s.force_mode,
    allowedPackages: s.allowed_packages,
    releaseRequested: s.release_requested,
    message: s.message ?? "",
    approved: s.approved,
  });
}
