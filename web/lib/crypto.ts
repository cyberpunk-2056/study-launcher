import { createHash, createHmac, randomBytes, randomInt } from "crypto";

export const OFFLINE_WINDOW_MS = 10 * 60 * 1000;

export const sha256 = (s: string) => createHash("sha256").update(s).digest("hex");
export const newToken = () => randomBytes(32).toString("hex");
export const newSecret = () => randomBytes(24).toString("hex");

export function newPairingCode(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let out = "";
  for (let i = 0; i < 8; i++) out += alphabet[randomInt(alphabet.length)];
  return out;
}

/** Time-limited unlock code. Must match OfflineCode.kt on the phone. */
export function offlineCode(secret: string, atMs: number = Date.now()): string {
  const window = Math.floor(atMs / OFFLINE_WINDOW_MS);
  const d = createHmac("sha256", secret).update(String(window)).digest();
  const n = ((d[0] & 0x7f) << 24) | (d[1] << 16) | (d[2] << 8) | d[3];
  return String(n % 1_000_000).padStart(6, "0");
}
