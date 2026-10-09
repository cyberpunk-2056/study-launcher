export type ModeInfo = { mode: "study" | "break"; forced: boolean; leftMs: number | null };

/** Mirrors ModeLogic.kt so the admin sees what the student's phone is doing right now. */
export function computeMode(
  s: { study_minutes: number; break_minutes: number; cycle_anchor: string; force_mode: "study" | "break" | null },
  now: number = Date.now()
): ModeInfo {
  if (s.force_mode) return { mode: s.force_mode, forced: true, leftMs: null };
  const study = s.study_minutes * 60_000;
  const total = study + s.break_minutes * 60_000;
  const pos = (((now - new Date(s.cycle_anchor).getTime()) % total) + total) % total;
  return pos < study
    ? { mode: "study", forced: false, leftMs: study - pos }
    : { mode: "break", forced: false, leftMs: total - pos };
}
