import { NextResponse } from "next/server";
import { db } from "@/lib/supabase";
import { SEVERE } from "@/lib/events";

export const dynamic = "force-dynamic";

export async function GET() {
  const since = new Date(Date.now() - 7 * 86_400_000).toISOString();
  const { data, error } = await db()
    .from("events")
    .select("id,type,detail,created_at,student_id,students(name)")
    .in("type", [...SEVERE, "enrolled", "paired"])
    .gte("created_at", since)
    .order("created_at", { ascending: false })
    .limit(300);
  if (error) return NextResponse.json({ error: error.message }, { status: 500 });
  return NextResponse.json(
    (data ?? []).map((e: any) => ({
      id: e.id, type: e.type, detail: e.detail, created_at: e.created_at,
      student_id: e.student_id, student: Array.isArray(e.students) ? e.students[0]?.name : e.students?.name,
    }))
  );
}
