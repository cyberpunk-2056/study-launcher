import nodemailer from "nodemailer";

export function smtpConfigured(): boolean {
  return !!(process.env.SMTP_HOST && process.env.SMTP_USER && process.env.SMTP_PASS);
}

/** Sends through the SMTP server from env. Throws if SMTP isn't configured or the server rejects. */
export async function sendMail(to: string, subject: string, text: string) {
  if (!smtpConfigured()) throw new Error("SMTP is not configured");
  const port = Number(process.env.SMTP_PORT ?? 587);
  const transport = nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port,
    secure: process.env.SMTP_SECURE ? process.env.SMTP_SECURE === "true" : port === 465,
    auth: { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS },
  });
  const html = `<div style="font:15px/1.5 system-ui,sans-serif;max-width:480px">${text
    .split("\n")
    .map((l) => `<p style="margin:0 0 10px">${l.replace(/&/g, "&amp;").replace(/</g, "&lt;")}</p>`)
    .join("")}</div>`;
  await transport.sendMail({ from: process.env.SMTP_FROM || process.env.SMTP_USER, to, subject, text, html });
}

export function maskEmail(e: string): string {
  const [u, d] = e.split("@");
  if (!d) return "***";
  return `${u.slice(0, 1)}${"*".repeat(Math.max(2, u.length - 1))}@${d}`;
}
