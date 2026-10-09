import { NextRequest } from "next/server";
import { db } from "./supabase";
import { sha256 } from "./crypto";

export type Student = {
  id: string;
  name: string;
  guardian_email: string | null;
  approved: boolean;
  message: string | null;
  offline_secret: string;
  allowed_packages: string[];
  study_minutes: number;
  break_minutes: number;
  cycle_anchor: string;
  force_mode: "study" | "break" | null;
  unlock_password_hash: string | null;
  failed_attempts: number;
  locked_until: string | null;
  release_requested: boolean;
};

/** Resolve the student that owns the bearer token, or null. */
export async function authDevice(req: NextRequest): Promise<Student | null> {
  const h = req.headers.get("authorization") ?? "";
  const token = h.startsWith("Bearer ") ? h.slice(7).trim() : "";
  if (token.length < 32) return null;
  const { data } = await db()
    .from("students")
    .select("*")
    .eq("device_token_hash", sha256(token))
    .maybeSingle();
  return (data as Student) ?? null;
}

export async function logEvent(studentId: string, type: string, detail?: string, deviceTime?: string) {
  await db().from("events").insert({
    student_id: studentId,
    type,
    detail: detail?.slice(0, 500) ?? null,
    device_time: deviceTime ?? null,
  });
}
