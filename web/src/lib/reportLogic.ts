// Ported from android/app/src/main/java/com/xbertz/onsite/report/ReportSummary.kt.
import type { SiteRequest, TrackingSessionRequest } from "./types";

export type ReportPeriod = "THIS_WEEK" | "FORTNIGHT" | "THIS_MONTH" | "LAST_MONTH" | "CUSTOM";

// All of periodRange/weeklyTotals works in the browser's *local* calendar date (matching
// summarize's localDateKey and Android's ZoneId.systemDefault()-based day grouping), not
// UTC - a session logged in the evening in Sydney (UTC+10/+11) is still "today" locally even
// though it may already be tomorrow in UTC, and "this week" must follow the same rule or the
// boundary silently shifts by up to a day depending on how far the viewer is from UTC.
function startOfLocalDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function mondayOnOrBefore(date: Date): Date {
  const d = startOfLocalDay(date);
  const day = d.getDay(); // 0 = Sunday
  const diff = day === 0 ? 6 : day - 1;
  d.setDate(d.getDate() - diff);
  return d;
}

/** Inclusive [start, end] range for a preset; CUSTOM returns null (caller keeps its own dates). */
export function periodRange(period: ReportPeriod, today: Date = new Date()): [Date, Date] | null {
  switch (period) {
    case "THIS_WEEK": {
      const monday = mondayOnOrBefore(today);
      const end = new Date(monday);
      end.setDate(end.getDate() + 6);
      return [monday, end];
    }
    case "FORTNIGHT": {
      const monday = mondayOnOrBefore(today);
      const start = new Date(monday);
      start.setDate(start.getDate() - 7);
      const end = new Date(monday);
      end.setDate(end.getDate() + 6);
      return [start, end];
    }
    case "THIS_MONTH": {
      const start = new Date(today.getFullYear(), today.getMonth(), 1);
      const end = new Date(today.getFullYear(), today.getMonth() + 1, 0);
      return [start, end];
    }
    case "LAST_MONTH": {
      const start = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      const end = new Date(today.getFullYear(), today.getMonth(), 0);
      return [start, end];
    }
    case "CUSTOM":
      return null;
  }
}

export interface ReportSummary {
  totalMillis: number;
  workedDays: number;
  estimatedAmount: number | null;
  unbilledMillis: number;
  unbilledAmount: number | null;
}

export interface WeekTotal {
  weekStart: Date;
  millis: number;
}

export interface LabeledTotal {
  label: string;
  millis: number;
}

function durationOf(s: TrackingSessionRequest): number {
  return s.stopTimestampMillis != null ? s.stopTimestampMillis - s.startTimestampMillis : 0;
}

/** Rate that applies to a session: its own override, else the client default, else none. */
export function effectiveRate(s: TrackingSessionRequest, ratesByClient: Map<string, number | null | undefined>): number | null {
  if (s.hourlyRate != null) return s.hourlyRate;
  const clientRate = s.clientName ? ratesByClient.get(s.clientName) : null;
  return clientRate ?? null;
}

function amountOf(sessions: TrackingSessionRequest[], ratesByClient: Map<string, number | null | undefined>): number | null {
  const priced = sessions.map((s) => {
    const rate = effectiveRate(s, ratesByClient);
    return rate != null ? (durationOf(s) / 3_600_000) * rate : null;
  }).filter((v): v is number => v != null);
  if (priced.length === 0) return null;
  return Math.round(priced.reduce((a, b) => a + b, 0) * 100) / 100;
}

function localDateKey(s: TrackingSessionRequest): string {
  const d = new Date(s.startTimestampMillis);
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

export function summarize(sessions: TrackingSessionRequest[], ratesByClient: Map<string, number | null | undefined>): ReportSummary {
  const completed = sessions.filter((s) => s.stopTimestampMillis != null);
  const unbilled = completed.filter((s) => s.invoiceId == null);
  return {
    totalMillis: completed.reduce((sum, s) => sum + durationOf(s), 0),
    workedDays: new Set(completed.map(localDateKey)).size,
    estimatedAmount: amountOf(completed, ratesByClient),
    unbilledMillis: unbilled.reduce((sum, s) => sum + durationOf(s), 0),
    unbilledAmount: amountOf(unbilled, ratesByClient),
  };
}

/** Hours per Monday-based week covering [start, end], including empty weeks so the chart has no gaps. */
export function weeklyTotals(sessions: TrackingSessionRequest[], start: Date, end: Date): WeekTotal[] {
  const byWeek = new Map<number, number>();
  for (const s of sessions) {
    if (s.stopTimestampMillis == null) continue;
    const monday = mondayOnOrBefore(new Date(s.startTimestampMillis)).getTime();
    byWeek.set(monday, (byWeek.get(monday) ?? 0) + durationOf(s));
  }
  const firstMonday = mondayOnOrBefore(start);
  const weeks: WeekTotal[] = [];
  const cursor = new Date(firstMonday);
  while (cursor.getTime() <= end.getTime()) {
    weeks.push({ weekStart: new Date(cursor), millis: byWeek.get(cursor.getTime()) ?? 0 });
    cursor.setDate(cursor.getDate() + 7);
  }
  return weeks;
}

function totalsBy(sessions: TrackingSessionRequest[], keyOf: (s: TrackingSessionRequest) => string | null | undefined): LabeledTotal[] {
  const byLabel = new Map<string, number>();
  for (const s of sessions) {
    if (s.stopTimestampMillis == null) continue;
    const label = keyOf(s) ?? "";
    byLabel.set(label, (byLabel.get(label) ?? 0) + durationOf(s));
  }
  return [...byLabel.entries()].map(([label, millis]) => ({ label, millis })).sort((a, b) => b.millis - a.millis);
}

export const totalsBySite = (sessions: TrackingSessionRequest[]) => totalsBy(sessions, (s) => s.siteLabel);
export const totalsByClient = (sessions: TrackingSessionRequest[]) => totalsBy(sessions, (s) => s.clientName);
export const totalsByJobType = (sessions: TrackingSessionRequest[]) => totalsBy(sessions, (s) => s.jobTypeLabel);

export const REPORT_COLUMNS = ["DATE", "SITE", "ADDRESS", "CLIENT", "JOB_TYPE", "START_TIME", "END_TIME", "HOURS"] as const;
export type ReportColumn = (typeof REPORT_COLUMNS)[number];

export const REPORT_COLUMN_LABELS: Record<ReportColumn, string> = {
  DATE: "Data",
  SITE: "Local",
  ADDRESS: "Endereço",
  CLIENT: "Cliente",
  JOB_TYPE: "Serviço",
  START_TIME: "Início",
  END_TIME: "Fim",
  HOURS: "Horas",
};

/** CSV with one row per session, same columns as the on-screen table, plus rate/amount. */
export function buildCsv(
  sessions: TrackingSessionRequest[],
  columns: ReportColumn[],
  sitesByLabel: Map<string, SiteRequest>,
  ratesByClient: Map<string, number | null | undefined>,
): string {
  const cell = (value: string) => `"${value.replace(/"/g, '""')}"`;
  const header = [...columns.map((c) => REPORT_COLUMN_LABELS[c]), "Valor/h", "Valor"];
  const rows = sessions
    .filter((s) => s.stopTimestampMillis != null)
    .sort((a, b) => a.startTimestampMillis - b.startTimestampMillis)
    .map((s) => {
      const start = new Date(s.startTimestampMillis);
      const stop = new Date(s.stopTimestampMillis!);
      const hours = durationOf(s) / 3_600_000;
      const rate = effectiveRate(s, ratesByClient);
      const pad = (n: number) => String(n).padStart(2, "0");
      const values = columns.map((column) => {
        switch (column) {
          case "DATE":
            return `${start.getFullYear()}-${pad(start.getMonth() + 1)}-${pad(start.getDate())}`;
          case "SITE":
            return s.siteLabel ?? "";
          case "ADDRESS":
            return sitesByLabel.get(s.siteLabel ?? "")?.address ?? "";
          case "CLIENT":
            return s.clientName ?? "";
          case "JOB_TYPE":
            return s.jobTypeLabel ?? "";
          case "START_TIME":
            return `${pad(start.getHours())}:${pad(start.getMinutes())}`;
          case "END_TIME":
            return `${pad(stop.getHours())}:${pad(stop.getMinutes())}`;
          case "HOURS":
            return hours.toFixed(2);
        }
      });
      values.push(rate != null ? rate.toFixed(2) : "", rate != null ? (hours * rate).toFixed(2) : "");
      return values.map(cell).join(",");
    });
  return [header.map(cell).join(","), ...rows].join("\r\n") + "\r\n";
}
