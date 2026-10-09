import { NextRequest, NextResponse } from "next/server";
import { sendMail } from "@/lib/mail";

export async function POST(req: NextRequest) {
  const { to } = await req.json().catch(() => ({}));
  if (typeof to !== "string" || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(to)) return NextResponse.json({ error: "enter a valid e-mail" }, { status: 400 });
  try {
    await sendMail(to, "Study Launcher test e-mail", "SMTP is working. Password reset codes will be delivered like this.");
    return NextResponse.json({ ok: true });
  } catch (e) {
    return NextResponse.json({ error: e instanceof Error ? e.message : "send failed" }, { status: 502 });
  }
}
