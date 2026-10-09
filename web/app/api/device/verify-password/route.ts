import { NextRequest, NextResponse } from "next/server";
import bcrypt from "bcryptjs";
import { db } from "@/lib/supabase";
import { authDevice, logEvent } from "@/lib/device";

const MAX_ATTEMPTS = 5;
const LOCK_MINUTES = 15;

export async function POST(req: NextRequest) {
  const s = await authDevice(req);
  if (!s) return NextResponse.json({ error: "unauthorized" }, { status: 401 });

  if (s.locked_until && new Date(s.locked_until) > new Date()) {
    return NextResponse.json({ ok: false, locked: true, retryAt: new Date(s.locked_until).getTime() }, { status: 429 });
  }
  const { password } = await req.json().catch(() => ({}));
  const good = typeof password === "string" && !!s.unlock_password_hash && (await bcrypt.compare(password, s.unlock_password_hash));

  if (good) {
    await db().from("students").update({ failed_attempts: 0, locked_until: null }).eq("id", s.id);
    await logEvent(s.id, "password_ok", "removal/unlock password accepted");
    return NextResponse.json({ ok: true });
  }

  const attempts = s.failed_attempts + 1;
  const lock = attempts >= MAX_ATTEMPTS;
  await db()
    .from("students")
    .update({
      failed_attempts: lock ? 0 : attempts,
      locked_until: lock ? new Date(Date.now() + LOCK_MINUTES * 60_000).toISOString() : null,
    })
    .eq("id", s.id);
  await logEvent(s.id, lock ? "password_lockout" : "password_wrong", `attempt ${attempts}`);
  return NextResponse.json({ ok: false, locked: lock, attemptsLeft: lock ? 0 : MAX_ATTEMPTS - attempts }, { status: 401 });
}
