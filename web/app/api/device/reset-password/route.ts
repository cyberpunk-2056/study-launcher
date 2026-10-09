import { NextRequest, NextResponse } from "next/server";
import bcrypt from "bcryptjs";
import { db } from "@/lib/supabase";
import { authDevice, logEvent } from "@/lib/device";
import { sha256 } from "@/lib/crypto";
import { sendMail, smtpConfigured } from "@/lib/mail";

export async function POST(req: NextRequest) {
  const s = await authDevice(req);
  if (!s) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  if (!s.approved) return NextResponse.json({ error: "not approved" }, { status: 403 });
  const { code, newPassword } = await req.json().catch(() => ({}));
  if (typeof code !== "string" || typeof newPassword !== "string" || newPassword.length < 6)
    return NextResponse.json({ error: "Enter the code and a new password of at least 6 characters." }, { status: 400 });

  const { data: r } = await db()
    .from("password_resets")
    .select("id,code_hash,attempts")
    .eq("student_id", s.id)
    .eq("used", false)
    .gt("expires_at", new Date().toISOString())
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (!r) return NextResponse.json({ error: "No active reset code. Request a new one." }, { status: 404 });

  if (r.attempts >= 5) {
    await db().from("password_resets").update({ used: true }).eq("id", r.id);
    return NextResponse.json({ error: "Too many wrong tries. Request a new code." }, { status: 429 });
  }
  if (sha256(`${s.id}:${code.trim()}`) !== r.code_hash) {
    await db().from("password_resets").update({ attempts: r.attempts + 1 }).eq("id", r.id);
    await logEvent(s.id, "password_wrong", "wrong reset code");
    return NextResponse.json({ error: `Wrong code (${4 - r.attempts} tries left).` }, { status: 401 });
  }

  await db().from("password_resets").update({ used: true }).eq("id", r.id);
  await db()
    .from("students")
    .update({ unlock_password_hash: await bcrypt.hash(newPassword, 10), failed_attempts: 0, locked_until: null })
    .eq("id", s.id);
  await logEvent(s.id, "password_reset", "changed with guardian e-mail code");
  if (s.guardian_email && smtpConfigured()) {
    sendMail(s.guardian_email, `Study Launcher: password changed for ${s.name}`,
      `The Study Launcher password for ${s.name} was just changed using your reset code. If this wasn't you or the student, change it in the admin panel.`).catch(() => {});
  }
  return NextResponse.json({ ok: true });
}
