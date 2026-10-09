import { NextRequest, NextResponse } from "next/server";
import { randomInt } from "crypto";
import bcrypt from "bcryptjs";
import { db } from "@/lib/supabase";
import { sendMail, smtpConfigured } from "@/lib/mail";

// Generates a fresh random removal password, saves it, and e-mails it to the guardian (not shown on screen).
export async function POST(_: NextRequest, { params }: { params: { id: string } }) {
  if (!smtpConfigured()) return NextResponse.json({ error: "SMTP is not configured on the server" }, { status: 503 });
  const { data: s } = await db().from("students").select("name,guardian_email").eq("id", params.id).maybeSingle();
  if (!s) return NextResponse.json({ error: "not found" }, { status: 404 });
  if (!s.guardian_email) return NextResponse.json({ error: "Set a guardian e-mail first" }, { status: 400 });

  const alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let pw = "";
  for (let i = 0; i < 10; i++) pw += alphabet[randomInt(alphabet.length)];
  try {
    await sendMail(s.guardian_email, `Study Launcher: new password for ${s.name}`,
      `A new Study Launcher removal password was set for ${s.name}:\n\n${pw}\n\nKeep it private. Anyone with it can remove the launcher from the phone.`);
  } catch {
    return NextResponse.json({ error: "Could not send the e-mail. Password was NOT changed." }, { status: 502 });
  }
  await db().from("students").update({ unlock_password_hash: await bcrypt.hash(pw, 10), failed_attempts: 0, locked_until: null }).eq("id", params.id);
  await db().from("events").insert({ student_id: params.id, type: "password_emailed", detail: "new password e-mailed to guardian by admin" });
  return NextResponse.json({ ok: true, to: s.guardian_email });
}
