// Date/time/money helpers shared by Sessions, Planning, Invoices and Reports. Mirrors the
// conventions used by the Android client's java.time-based code: dateEpochDay is a plain
// calendar date (java.time.LocalDate.toEpochDay()), independent of timezone; timestamps are
// plain Unix millis, displayed in the browser's local timezone.

const DAY_MILLIS = 86_400_000;

/** yyyy-MM-dd (an <input type="date"> value) -> LocalDate.toEpochDay()-compatible day count. */
export function isoDateToEpochDay(iso: string): number {
  return Math.round(new Date(`${iso}T00:00:00Z`).getTime() / DAY_MILLIS);
}

/** Inverse of isoDateToEpochDay, for populating an <input type="date">. */
export function epochDayToIsoDate(epochDay: number): string {
  return new Date(epochDay * DAY_MILLIS).toISOString().slice(0, 10);
}

export function epochDayToLabel(epochDay: number, opts: Intl.DateTimeFormatOptions = { day: "2-digit", month: "2-digit", year: "numeric" }): string {
  return new Date(epochDay * DAY_MILLIS).toLocaleDateString("pt-BR", { ...opts, timeZone: "UTC" });
}

export function todayEpochDay(): number {
  return isoDateToEpochDay(new Date().toISOString().slice(0, 10));
}

/** Epoch day of the browser's local calendar date for a given instant (not UTC's date). */
export function millisToLocalEpochDay(millis: number): number {
  const d = new Date(millis);
  return Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / DAY_MILLIS);
}

/** Combines an <input type="date"> value and an <input type="time"> value into local epoch millis. */
export function combineLocalDateTime(dateIso: string, timeHm: string): number {
  const [y, m, d] = dateIso.split("-").map(Number);
  const [hh, mm] = timeHm.split(":").map(Number);
  return new Date(y, m - 1, d, hh, mm).getTime();
}

export function millisToDateInput(millis: number): string {
  const d = new Date(millis);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function millisToTimeInput(millis: number): string {
  const d = new Date(millis);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function minuteOfDayToTimeInput(minute: number): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(Math.floor(minute / 60))}:${pad(minute % 60)}`;
}

export function timeInputToMinuteOfDay(timeHm: string): number {
  const [hh, mm] = timeHm.split(":").map(Number);
  return hh * 60 + mm;
}

export function formatDateTime(millis: number): string {
  return new Date(millis).toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function formatTime(millis: number): string {
  return new Date(millis).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
}

export function formatHours(millis: number): string {
  return (millis / 3_600_000).toFixed(2);
}

export function formatMoney(amount: number | null | undefined): string {
  if (amount == null) return "—";
  return amount.toLocaleString("pt-BR", { style: "currency", currency: "AUD" });
}

export function durationMillis(startMillis: number, stopMillis: number | null | undefined): number | null {
  return stopMillis != null ? stopMillis - startMillis : null;
}
