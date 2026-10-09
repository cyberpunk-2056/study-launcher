import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/supabase";
import { OFFLINE_WINDOW_MS, offlineCode } from "@/lib/crypto";

export const dynamic = "force-dynamic";

// Time-limited code the admin can read out to a student whose phone is offline.
export async function GET(_: NextRequest, { params }: { params: { id: string } }) {
  const { data } = await db().from("students").select("offline_secret").eq("id", params.id).maybeSingle();
  if (!data) return NextResponse.json({ error: "not found" }, { status: 404 });
  const now = Date.now();
  const expiresIn = OFFLINE_WINDOW_MS - (now % OFFLINE_WINDOW_MS);
  return NextResponse.json({ code: offlineCode(data.offline_secret, now), expiresInSeconds: Math.floor(expiresIn / 1000) });
}
