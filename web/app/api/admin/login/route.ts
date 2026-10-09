import { NextRequest, NextResponse } from "next/server";
import { timingSafeEqual } from "crypto";
import { COOKIE, signSession } from "@/lib/session";

function same(a: string, b: string) {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

export async function POST(req: NextRequest) {
  const { username, password } = await req.json().catch(() => ({}));
  const u = process.env.ADMIN_USERNAME ?? "";
  const p = process.env.ADMIN_PASSWORD ?? "";
  await new Promise((r) => setTimeout(r, 600)); // slow down guessing
  if (!u || !p || typeof username !== "string" || typeof password !== "string" || !same(username, u) || !same(password, p)) {
    return NextResponse.json({ error: "invalid credentials" }, { status: 401 });
  }
  const res = NextResponse.json({ ok: true });
  res.cookies.set(COOKIE, await signSession(), {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict",
    path: "/",
    maxAge: 12 * 3600,
  });
  return res;
}
