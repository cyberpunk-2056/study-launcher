import { NextResponse } from "next/server";
import { smtpConfigured } from "@/lib/mail";

export const dynamic = "force-dynamic";

export async function GET() {
  return NextResponse.json({
    smtpConfigured: smtpConfigured(),
    smtpHost: process.env.SMTP_HOST ?? null,
    smtpFrom: process.env.SMTP_FROM || process.env.SMTP_USER || null,
    enrollEnabled: !!process.env.ENROLL_CODE,
    supabaseConfigured: !!process.env.SUPABASE_URL && !!process.env.SUPABASE_SERVICE_ROLE_KEY,
  });
}
