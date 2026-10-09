import { NextRequest, NextResponse } from "next/server";
import { randomInt } from "crypto";
import { db } from "@/lib/supabase";
import { authDevice, logEvent } from "@/lib/device";
import { sha256 } from "@/lib/crypto";
import { maskEmail, sendMail, smtpConfigured } from "@/lib/mail";

const resetHash = (studentId: string, code: string) => sha256(`${studentId}:${code}`);

// The reset code is e-mailed to the guardian address the ADMIN approved - never to the student.
export async function POST(req: NextRequest) {
  const s = await authDevice(req);
  if (!s) return NextResponse.json({ error: "unauthorized" }, { status: 401 });
  if (!s.approved) return NextResponse.json({ error: "Your admin hasn't approved this phone yet. Ask them to reset the password." }, { status: 403 });
  if (!s.guardian_email) return NextResponse.json({ error: "No guardian e-mail is set. Ask your admin." }, { status: 400 });
  if (!smtpConfigured()) return NextResponse.json({ error: "E-mail is not set up on the server. Ask your admin." }, { status: 503 });

  const since = new Date(Date.now() - 3600_000).toISOString();
  const { count } = await db().from("password_resets").select("id", { count: "exact", head: true }).eq("student_id", s.id).gte("created_at", since);
  if ((count ?? 0) >= 3) return NextResponse.json({ error: "Too many requests. Try again in an hour." }, { status: 429 });

  const code = String(randomInt(0, 100_000_000)).padStart(8, "0");
  await db().from("password_resets").insert({
    student_id: s.id,
    code_hash: resetHash(s.id, code),
    expires_at: new Date(Date.now() + 15 * 60_000).toISOString(),
  });
  try {
    await sendMail(
      s.guardian_email,
      `Study Launcher: password reset code for ${s.name}`,
      `${s.name} asked to reset the Study Launcher password.\n\nReset code: ${code}\n\nIt expires in 15 minutes. Only share it if you approve. If you did not expect this, ignore this e-mail.`
    );
  } catch (e) {
    return NextResponse.json({ error: "Could not send the e-mail. Ask your admin." }, { status: 502 });
  }
  await logEvent(s.id, "forgot_password_requested", `code e-mailed to ${maskEmail(s.guardian_email)}`);
  return NextResponse.json({ ok: true, maskedEmail: maskEmail(s.guardian_email) });
}
