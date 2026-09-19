// Ported from android/app/src/main/java/com/xbertz/onsite/invoice/InvoiceData.kt.
import type { SiteRequest, TrackingSessionRequest } from "./types";

/** One invoice line = one worked day at one rate (all sessions of that day merged). */
export interface InvoiceLine {
  dateEpochDay: number;
  sites: string[];
  addresses: string[];
  companies: string[];
  jobTypes: string[];
  startTimestampMillis: number;
  endTimestampMillis: number;
  durationMillis: number;
  hourlyRate: number | null;
  sessionIds: string[];
}

export function lineHours(line: InvoiceLine): number {
  return Math.round((line.durationMillis / 3_600_000) * 100) / 100;
}

export function lineAmount(line: InvoiceLine): number | null {
  return line.hourlyRate != null ? Math.round(lineHours(line) * line.hourlyRate * 100) / 100 : null;
}

function localDateEpochDay(millis: number): number {
  const d = new Date(millis);
  return Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / 86_400_000);
}

function uniq(values: string[]): string[] {
  return [...new Set(values)];
}

/**
 * Groups completed sessions into one InvoiceLine per calendar day (and rate), oldest first.
 * [defaultRate] applies to sessions without their own hourlyRate override.
 */
export function buildInvoiceLines(
  sessions: TrackingSessionRequest[],
  sitesByLabel: Map<string, SiteRequest>,
  defaultRate: number | null = null,
): InvoiceLine[] {
  const groups = new Map<string, { dateEpochDay: number; rate: number | null; sessions: TrackingSessionRequest[] }>();
  for (const s of sessions) {
    if (s.stopTimestampMillis == null) continue;
    const dateEpochDay = localDateEpochDay(s.startTimestampMillis);
    const rate = s.hourlyRate ?? defaultRate;
    const key = `${dateEpochDay}|${rate ?? "none"}`;
    if (!groups.has(key)) groups.set(key, { dateEpochDay, rate, sessions: [] });
    groups.get(key)!.sessions.push(s);
  }
  return [...groups.values()]
    .sort((a, b) => a.dateEpochDay - b.dateEpochDay || (a.rate ?? -1) - (b.rate ?? -1))
    .map(({ dateEpochDay, rate, sessions: daySessions }) => {
      const sorted = [...daySessions].sort((a, b) => a.startTimestampMillis - b.startTimestampMillis);
      const sites = uniq(sorted.map((s) => s.siteLabel).filter((v): v is string => !!v));
      return {
        dateEpochDay,
        sites,
        addresses: uniq(sites.map((label) => sitesByLabel.get(label)?.address).filter((v): v is string => !!v)),
        companies: uniq(sorted.map((s) => s.companyName).filter((v): v is string => !!v)),
        jobTypes: uniq(sorted.map((s) => s.jobTypeLabel).filter((v): v is string => !!v)),
        startTimestampMillis: sorted[0].startTimestampMillis,
        endTimestampMillis: sorted[sorted.length - 1].stopTimestampMillis!,
        durationMillis: sorted.reduce((sum, s) => sum + (s.stopTimestampMillis! - s.startTimestampMillis), 0),
        hourlyRate: rate,
        sessionIds: sorted.map((s) => s.id),
      };
    });
}

export function invoiceTotalHours(lines: InvoiceLine[]): number {
  return Math.round(lines.reduce((sum, l) => sum + lineHours(l), 0) * 100) / 100;
}

export function invoiceShowsAmounts(lines: InvoiceLine[]): boolean {
  return lines.some((l) => l.hourlyRate != null);
}

export function invoiceTotalAmount(lines: InvoiceLine[]): number | null {
  if (!invoiceShowsAmounts(lines)) return null;
  return Math.round(lines.reduce((sum, l) => sum + (lineAmount(l) ?? 0), 0) * 100) / 100;
}
