import { describe, expect, it } from "vitest";
import type { SiteRequest, TrackingSessionRequest } from "./types";
import { buildInvoiceLines, invoiceShowsAmounts, invoiceTotalAmount, invoiceTotalHours, lineAmount, lineHours } from "./invoiceLogic";

function session(partial: Partial<TrackingSessionRequest> & { startTimestampMillis: number }): TrackingSessionRequest {
  return {
    id: crypto.randomUUID(),
    clientName: "Acme",
    siteLabel: "HQ",
    jobTypeLabel: "Install",
    startLatitude: 0,
    startLongitude: 0,
    stopLatitude: 0,
    stopLongitude: 0,
    hourlyRate: null,
    invoiceId: null,
    stopTimestampMillis: partial.startTimestampMillis + 3_600_000,
    ...partial,
  };
}

const DAY1 = Date.UTC(2026, 2, 16, 8, 0); // 08:00
const DAY2 = Date.UTC(2026, 2, 17, 9, 0);

describe("buildInvoiceLines", () => {
  it("merges same-day sessions into one line and sums their duration", () => {
    const sessions = [
      session({ startTimestampMillis: DAY1, stopTimestampMillis: DAY1 + 2 * 3_600_000 }),
      session({ startTimestampMillis: DAY1 + 3 * 3_600_000, stopTimestampMillis: DAY1 + 4 * 3_600_000 }),
    ];
    const lines = buildInvoiceLines(sessions, new Map());
    expect(lines).toHaveLength(1);
    expect(lines[0].durationMillis).toBe(3 * 3_600_000);
    expect(lines[0].sessionIds).toHaveLength(2);
  });

  it("splits a day into separate lines per distinct rate", () => {
    const sessions = [
      session({ startTimestampMillis: DAY1, hourlyRate: 50 }),
      session({ startTimestampMillis: DAY1 + 2 * 3_600_000, hourlyRate: 80 }),
    ];
    const lines = buildInvoiceLines(sessions, new Map());
    expect(lines).toHaveLength(2);
    expect(lines.map((l) => l.hourlyRate).sort()).toEqual([50, 80]);
  });

  it("orders lines oldest day first", () => {
    const sessions = [session({ startTimestampMillis: DAY2 }), session({ startTimestampMillis: DAY1 })];
    const lines = buildInvoiceLines(sessions, new Map());
    expect(lines[0].startTimestampMillis).toBe(DAY1);
    expect(lines[1].startTimestampMillis).toBe(DAY2);
  });

  it("falls back to the default rate when a session has none of its own", () => {
    const sessions = [session({ startTimestampMillis: DAY1 })];
    const lines = buildInvoiceLines(sessions, new Map(), 65);
    expect(lines[0].hourlyRate).toBe(65);
  });

  it("looks up each site's address for the invoice", () => {
    const sessions = [session({ startTimestampMillis: DAY1, siteLabel: "HQ" })];
    const sitesByLabel = new Map<string, SiteRequest>([["HQ", { id: "s1", label: "HQ", address: "1 Main St", latitude: 0, longitude: 0, createdAtMillis: 0 }]]);
    const lines = buildInvoiceLines(sessions, sitesByLabel);
    expect(lines[0].addresses).toEqual(["1 Main St"]);
  });

  it("ignores sessions that are still open (no stop time)", () => {
    const sessions = [session({ startTimestampMillis: DAY1, stopTimestampMillis: null })];
    expect(buildInvoiceLines(sessions, new Map())).toHaveLength(0);
  });
});

describe("line and invoice totals", () => {
  it("computes line hours and amount from duration and rate", () => {
    const line = buildInvoiceLines([session({ startTimestampMillis: DAY1, stopTimestampMillis: DAY1 + 5_400_000, hourlyRate: 40 })], new Map())[0];
    expect(lineHours(line)).toBe(1.5);
    expect(lineAmount(line)).toBe(60);
  });

  it("an hours-only invoice (no rate anywhere) shows no amounts", () => {
    const lines = buildInvoiceLines([session({ startTimestampMillis: DAY1 })], new Map());
    expect(invoiceShowsAmounts(lines)).toBe(false);
    expect(invoiceTotalAmount(lines)).toBeNull();
  });

  it("totals hours and amount across lines with a rate", () => {
    const sessions = [
      session({ startTimestampMillis: DAY1, stopTimestampMillis: DAY1 + 3_600_000, hourlyRate: 50 }),
      session({ startTimestampMillis: DAY2, stopTimestampMillis: DAY2 + 2 * 3_600_000, hourlyRate: 50 }),
    ];
    const lines = buildInvoiceLines(sessions, new Map());
    expect(invoiceTotalHours(lines)).toBe(3);
    expect(invoiceTotalAmount(lines)).toBe(150);
  });
});
