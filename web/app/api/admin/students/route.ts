import { NextRequest, NextResponse } from "next/server";
import bcrypt from "bcryptjs";
import { db } from "@/lib/supabase";
import { newPairingCode, newSecret } from "@/lib/crypto";
import { DEFAULT_ALLOWED } from "@/lib/defaults";
import { computeMode } from "@/lib/mode";
import { SEVERE } from "@/lib/events";

export const dynamic = "force-dynamic";

export async function GET() {
  const { data, error } = await db()
    .from("students")
    .select("id,name,approved,guardian_email,pairing_code,device_name,device_model,os_version,app_version,device_token_hash,setup_missing,last_seen,created_at,study_minutes,break_minutes,cycle_anchor,force_mode,release_requested")
    .order("created_at", { ascending: false });
  if (error) return NextResponse.json({ error: error.message }, { status: 500 });
  const studs = data ?? [];

  const since = new Date(Date.now() - 3 * 86_400_000).toISOString().slice(0, 10);
  const dayAgo = new Date(Date.now() - 86_400_000).toISOString();
  const [usage, ev] = await Promise.all([
    db().from("usage_daily").select("student_id,day,screen_ms,focus_min").gte("day", since).order("day", { ascending: false }),
    db().from("events").select("student_id").in("type", SEVERE).gte("created_at", dayAgo),
  ]);
  const latest = new Map<string, { day: string; screen_ms: number; focus_min: number }>();
  for (const u of usage.data ?? []) if (!latest.has(u.student_id)) latest.set(u.student_id, u);
  const alerts = new Map<string, number>();
  for (const e of ev.data ?? []) alerts.set(e.student_id, (alerts.get(e.student_id) ?? 0) + 1);

  const now = Date.now();
  const rows = studs.map(({ device_token_hash, ...r }) => {
    const m = computeMode(r, now);
    const u = latest.get(r.id);
    return {
      ...r,
      paired: !!device_token_hash,
      online: !!r.last_seen && now - new Date(r.last_seen).getTime() < 3 * 60_000,
      mode: m.mode,
      mode_forced: m.forced,
      mode_left_ms: m.leftMs,
      usage_day: u?.day ?? null,
      screen_ms: u?.screen_ms ?? 0,
      focus_min: u?.focus_min ?? 0,
      alerts24h: alerts.get(r.id) ?? 0,
    };
  });
  return NextResponse.json(rows);
}

export async function POST(req: NextRequest) {
  const { name, password, guardianEmail } = await req.json().catch(() => ({}));
  if (typeof name !== "string" || !name.trim()) return NextResponse.json({ error: "name required" }, { status: 400 });
  if (typeof password !== "string" || password.length < 6)
    return NextResponse.json({ error: "removal password must be at least 6 characters" }, { status: 400 });
  const email = typeof guardianEmail === "string" ? guardianEmail.trim().toLowerCase() : "";
  if (email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return NextResponse.json({ error: "invalid guardian e-mail" }, { status: 400 });
  const { data, error } = await db()
    .from("students")
    .insert({
      name: name.trim(),
      guardian_email: email || null,
      pairing_code: newPairingCode(),
      offline_secret: newSecret(),
      allowed_packages: DEFAULT_ALLOWED,
      unlock_password_hash: await bcrypt.hash(password, 10),
    })
    .select("id")
    .single();
  if (error) return NextResponse.json({ error: error.message }, { status: 500 });
  return NextResponse.json({ id: data.id });
}
