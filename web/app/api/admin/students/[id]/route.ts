import { NextRequest, NextResponse } from "next/server";
import bcrypt from "bcryptjs";
import { db } from "@/lib/supabase";
import { newPairingCode } from "@/lib/crypto";
import { computeMode } from "@/lib/mode";

type Ctx = { params: { id: string } };
export const dynamic = "force-dynamic";

export async function GET(_: NextRequest, { params }: Ctx) {
  const { data: s } = await db()
    .from("students")
    .select("id,name,notes,message,approved,guardian_email,pairing_code,device_name,device_model,os_version,app_version,device_token_hash,setup_missing,last_seen,created_at,allowed_packages,study_minutes,break_minutes,cycle_anchor,force_mode,release_requested,locked_until,failed_attempts")
    .eq("id", params.id)
    .maybeSingle();
  if (!s) return NextResponse.json({ error: "not found" }, { status: 404 });
  const since = new Date(Date.now() - 30 * 86_400_000).toISOString().slice(0, 10);
  const [apps, events, usage] = await Promise.all([
    db().from("device_apps").select("package_name,label").eq("student_id", params.id).order("label"),
    db().from("events").select("id,type,detail,created_at").eq("student_id", params.id).order("created_at", { ascending: false }).limit(300),
    db().from("usage_daily").select("day,screen_ms,apps,focus_min,pomodoros,tasks_done").eq("student_id", params.id).gte("day", since).order("day", { ascending: false }),
  ]);
  const { device_token_hash, ...rest } = s;
  const m = computeMode(s);
  return NextResponse.json({
    ...rest,
    paired: !!device_token_hash,
    online: !!s.last_seen && Date.now() - new Date(s.last_seen).getTime() < 3 * 60_000,
    mode: m.mode, mode_forced: m.forced, mode_left_ms: m.leftMs,
    apps: apps.data ?? [], events: events.data ?? [], usage: usage.data ?? [],
  });
}

export async function PATCH(req: NextRequest, { params }: Ctx) {
  const b = await req.json().catch(() => ({}));
  const u: Record<string, unknown> = {};
  if (typeof b.name === "string" && b.name.trim()) u.name = b.name.trim();
  if (typeof b.notes === "string") u.notes = b.notes.slice(0, 2000);
  if (typeof b.message === "string") u.message = b.message.slice(0, 280);
  if (typeof b.guardian_email === "string") {
    const e = b.guardian_email.trim().toLowerCase();
    if (e && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(e)) return NextResponse.json({ error: "invalid e-mail" }, { status: 400 });
    u.guardian_email = e || null;
  }
  if (typeof b.approved === "boolean") u.approved = b.approved;
  if (Array.isArray(b.allowed_packages)) u.allowed_packages = b.allowed_packages.filter((x: unknown) => typeof x === "string");
  if (typeof b.copy_apps_from === "string") {
    const { data: src } = await db().from("students").select("allowed_packages").eq("id", b.copy_apps_from).maybeSingle();
    if (!src) return NextResponse.json({ error: "source student not found" }, { status: 404 });
    u.allowed_packages = src.allowed_packages;
  }
  if (Number.isInteger(b.study_minutes) && b.study_minutes >= 1 && b.study_minutes <= 1440) u.study_minutes = b.study_minutes;
  if (Number.isInteger(b.break_minutes) && b.break_minutes >= 1 && b.break_minutes <= 1440) u.break_minutes = b.break_minutes;
  if (b.force_mode === "study" || b.force_mode === "break" || b.force_mode === null) u.force_mode = b.force_mode;
  if (typeof b.release_requested === "boolean") u.release_requested = b.release_requested;
  if (b.restart_cycle === true) u.cycle_anchor = new Date().toISOString();
  if (typeof b.password === "string") {
    if (b.password.length < 6) return NextResponse.json({ error: "password must be at least 6 characters" }, { status: 400 });
    u.unlock_password_hash = await bcrypt.hash(b.password, 10);
    u.failed_attempts = 0;
    u.locked_until = null;
  }
  if (b.clear_lockout === true) { u.failed_attempts = 0; u.locked_until = null; }
  if (b.repair === true) {
    // Detach the current phone and issue a fresh pairing code.
    u.device_token_hash = null;
    u.device_name = null;
    u.pairing_code = newPairingCode();
    u.release_requested = false;
  }
  if (!Object.keys(u).length) return NextResponse.json({ error: "nothing to update" }, { status: 400 });
  const { error } = await db().from("students").update(u).eq("id", params.id);
  if (error) return NextResponse.json({ error: error.message }, { status: 500 });
  return NextResponse.json({ ok: true });
}

export async function DELETE(_: NextRequest, { params }: Ctx) {
  const { error } = await db().from("students").delete().eq("id", params.id);
  if (error) return NextResponse.json({ error: error.message }, { status: 500 });
  return NextResponse.json({ ok: true });
}
