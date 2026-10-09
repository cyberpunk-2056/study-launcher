import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/supabase";
import { newToken, sha256 } from "@/lib/crypto";
import { logEvent } from "@/lib/device";

export async function POST(req: NextRequest) {
  const { pairingCode, deviceName } = await req.json().catch(() => ({}));
  if (typeof pairingCode !== "string" || pairingCode.length < 6)
    return NextResponse.json({ error: "invalid code" }, { status: 400 });
  const { data: s } = await db()
    .from("students")
    .select("id,name,offline_secret")
    .eq("pairing_code", pairingCode.trim().toUpperCase())
    .maybeSingle();
  if (!s) return NextResponse.json({ error: "invalid or already used code" }, { status: 404 });

  const token = newToken();
  const { error } = await db()
    .from("students")
    .update({
      device_token_hash: sha256(token),
      device_name: typeof deviceName === "string" ? deviceName.slice(0, 80) : null,
      pairing_code: null, // single use
      last_seen: new Date().toISOString(),
    })
    .eq("id", s.id);
  if (error) return NextResponse.json({ error: error.message }, { status: 500 });
  await logEvent(s.id, "paired", deviceName);
  return NextResponse.json({ token, studentName: s.name, offlineSecret: s.offline_secret });
}
