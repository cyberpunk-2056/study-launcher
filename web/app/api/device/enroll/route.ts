import { NextRequest, NextResponse } from "next/server";
import { timingSafeEqual } from "crypto";
import bcrypt from "bcryptjs";
import { db } from "@/lib/supabase";
import { newSecret, newToken, sha256 } from "@/lib/crypto";
import { DEFAULT_ALLOWED } from "@/lib/defaults";
import { logEvent } from "@/lib/device";

const same = (a: string, b: string) => {
  const x = Buffer.from(a), y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
};

/**
 * A new student sets the launcher up on their own phone. They show up in the admin panel as
 * "pending". The removal password is random and unknown until the admin sets one, and the guardian
 * e-mail they type is NOT trusted for resets until the admin approves the student.
 */
export async function POST(req: NextRequest) {
  const code = process.env.ENROLL_CODE;
  if (!code) return NextResponse.json({ error: "Self-enrollment is turned off. Ask your admin for a pairing code." }, { status: 403 });
  const { enrollCode, name, guardianEmail, deviceName } = await req.json().catch(() => ({}));
  if (typeof enrollCode !== "string" || !same(enrollCode.trim(), code)) {
    await new Promise((r) => setTimeout(r, 800));
    return NextResponse.json({ error: "Wrong enrollment code" }, { status: 401 });
  }
  if (typeof name !== "string" || name.trim().length < 2) return NextResponse.json({ error: "Enter your name" }, { status: 400 });
  const email = typeof guardianEmail === "string" ? guardianEmail.trim().toLowerCase() : "";
  if (email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return NextResponse.json({ error: "Invalid guardian e-mail" }, { status: 400 });

  const since = new Date(Date.now() - 3600_000).toISOString();
  const { count } = await db().from("students").select("id", { count: "exact", head: true }).eq("approved", false).gte("created_at", since);
  if ((count ?? 0) >= 30) return NextResponse.json({ error: "Too many sign-ups right now. Try later." }, { status: 429 });

  const token = newToken();
  const secret = newSecret();
  const { data, error } = await db()
    .from("students")
    .insert({
      name: name.trim().slice(0, 80),
      guardian_email: email || null,
      approved: false,
      offline_secret: secret,
      allowed_packages: DEFAULT_ALLOWED,
      unlock_password_hash: await bcrypt.hash(newSecret(), 10),
      device_token_hash: sha256(token),
      device_name: typeof deviceName === "string" ? deviceName.slice(0, 80) : null,
      last_seen: new Date().toISOString(),
    })
    .select("id")
    .single();
  if (error) return NextResponse.json({ error: error.message }, { status: 500 });
  await logEvent(data.id, "enrolled", `self-enrolled from ${deviceName ?? "a phone"}`);
  return NextResponse.json({ token, studentName: name.trim(), offlineSecret: secret });
}
