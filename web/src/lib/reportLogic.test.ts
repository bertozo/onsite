import { describe, expect, it } from "vitest";
import type { SiteRequest, TrackingSessionRequest } from "./types";
import { buildCsv, effectiveRate, periodRange, summarize, totalsByCompany, totalsBySite, weeklyTotals } from "./reportLogic";

function session(partial: Partial<TrackingSessionRequest> & { startTimestampMillis: number }): TrackingSessionRequest {
  return {
    id: crypto.randomUUID(),
    companyName: null,
    siteLabel: null,
    jobTypeLabel: null,
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

// A fixed Wednesday (2026-03-18) so period presets are deterministic regardless of when tests run.
const WEDNESDAY = new Date(Date.UTC(2026, 2, 18));

describe("periodRange", () => {
  it("THIS_WEEK is the Monday-to-Sunday week containing today", () => {
    const [start, end] = periodRange("THIS_WEEK", WEDNESDAY)!;
    expect(start.toISOString().slice(0, 10)).toBe("2026-03-16"); // Monday
    expect(end.toISOString().slice(0, 10)).toBe("2026-03-22"); // Sunday
  });

  it("FORTNIGHT extends THIS_WEEK back by 7 days", () => {
    const [start, end] = periodRange("FORTNIGHT", WEDNESDAY)!;
    expect(start.toISOString().slice(0, 10)).toBe("2026-03-09");
    expect(end.toISOString().slice(0, 10)).toBe("2026-03-22");
  });

  it("THIS_MONTH and LAST_MONTH bracket the full calendar months", () => {
    const [thisStart, thisEnd] = periodRange("THIS_MONTH", WEDNESDAY)!;
    expect(thisStart.toISOString().slice(0, 10)).toBe("2026-03-01");
    expect(thisEnd.toISOString().slice(0, 10)).toBe("2026-03-31");

    const [lastStart, lastEnd] = periodRange("LAST_MONTH", WEDNESDAY)!;
    expect(lastStart.toISOString().slice(0, 10)).toBe("2026-02-01");
    expect(lastEnd.toISOString().slice(0, 10)).toBe("2026-02-28");
  });

  it("CUSTOM has no preset range", () => {
    expect(periodRange("CUSTOM", WEDNESDAY)).toBeNull();
  });
});

describe("effectiveRate", () => {
  it("prefers the session's own rate over the company default", () => {
    const s = session({ startTimestampMillis: 0, companyName: "Acme", hourlyRate: 80 });
    expect(effectiveRate(s, new Map([["Acme", 50]]))).toBe(80);
  });

  it("falls back to the company default when the session has none", () => {
    const s = session({ startTimestampMillis: 0, companyName: "Acme" });
    expect(effectiveRate(s, new Map([["Acme", 50]]))).toBe(50);
  });

  it("is null when neither the session nor the company has a rate", () => {
    const s = session({ startTimestampMillis: 0, companyName: "Acme" });
    expect(effectiveRate(s, new Map())).toBeNull();
  });
});

describe("summarize", () => {
  const day = Date.UTC(2026, 2, 16); // Monday, local-timezone-independent enough for a 2h session
  const sessions = [
    session({ startTimestampMillis: day, stopTimestampMillis: day + 2 * 3_600_000, companyName: "Acme", hourlyRate: 50 }),
    session({ startTimestampMillis: day + 3 * 3_600_000, stopTimestampMillis: day + 4 * 3_600_000, companyName: "Acme", invoiceId: "inv-1" }),
    session({ startTimestampMillis: day + 100 * 3_600_000, stopTimestampMillis: null }), // still open, excluded
  ];

  it("only counts completed sessions and separates unbilled ones", () => {
    const summary = summarize(sessions, new Map([["Acme", 50]]));
    expect(summary.totalMillis).toBe(3 * 3_600_000);
    expect(summary.unbilledMillis).toBe(2 * 3_600_000); // the invoiced session is excluded
  });

  it("estimatedAmount is null when nothing has a rate", () => {
    const noRateSessions = [session({ startTimestampMillis: day, companyName: "Acme" })];
    expect(summarize(noRateSessions, new Map()).estimatedAmount).toBeNull();
  });
});

describe("weeklyTotals", () => {
  it("includes empty weeks in range so a chart has no gaps", () => {
    const monday1 = Date.UTC(2026, 2, 2);
    const monday3 = Date.UTC(2026, 2, 16);
    const sessions = [session({ startTimestampMillis: monday1, stopTimestampMillis: monday1 + 3_600_000 })];
    const weeks = weeklyTotals(sessions, new Date(monday1), new Date(monday3 + 6 * 86_400_000));
    expect(weeks).toHaveLength(3);
    expect(weeks[0].millis).toBe(3_600_000);
    expect(weeks[1].millis).toBe(0);
    expect(weeks[2].millis).toBe(0);
  });
});

describe("totalsBy*", () => {
  it("groups and sorts by hours descending", () => {
    const day = Date.UTC(2026, 2, 16);
    const sessions = [
      session({ startTimestampMillis: day, stopTimestampMillis: day + 1 * 3_600_000, companyName: "A", siteLabel: "Site 1" }),
      session({ startTimestampMillis: day, stopTimestampMillis: day + 3 * 3_600_000, companyName: "B", siteLabel: "Site 2" }),
    ];
    expect(totalsByCompany(sessions).map((t) => t.label)).toEqual(["B", "A"]);
    expect(totalsBySite(sessions).map((t) => t.label)).toEqual(["Site 2", "Site 1"]);
  });
});

describe("buildCsv", () => {
  it("produces a header row and one row per completed session with rate/amount columns", () => {
    const day = Date.UTC(2026, 2, 16, 8, 0);
    const sessions = [session({ startTimestampMillis: day, stopTimestampMillis: day + 3_600_000, companyName: "Acme", siteLabel: "HQ", hourlyRate: 40 })];
    const sitesByLabel = new Map<string, SiteRequest>([["HQ", { id: "s1", label: "HQ", address: "1 Main St", latitude: 0, longitude: 0, createdAtMillis: 0 }]]);
    const csv = buildCsv(sessions, ["DATE", "SITE", "ADDRESS", "HOURS"], sitesByLabel, new Map());
    const lines = csv.trim().split("\r\n");
    expect(lines).toHaveLength(2);
    expect(lines[1]).toContain('"HQ"');
    expect(lines[1]).toContain('"1 Main St"');
    expect(lines[1]).toContain('"1.00"');
    expect(lines[1]).toContain('"40.00"'); // rate column
  });
});
