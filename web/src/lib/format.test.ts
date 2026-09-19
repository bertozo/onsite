import { describe, expect, it } from "vitest";
import {
  combineLocalDateTime,
  epochDayToIsoDate,
  formatHours,
  isoDateToEpochDay,
  millisToDateInput,
  millisToLocalEpochDay,
  millisToTimeInput,
  minuteOfDayToTimeInput,
  timeInputToMinuteOfDay,
} from "./format";

describe("epoch day conversions", () => {
  it("round-trips an ISO date through epoch day and back", () => {
    const epochDay = isoDateToEpochDay("2026-03-15");
    expect(epochDayToIsoDate(epochDay)).toBe("2026-03-15");
  });

  it("matches java.time.LocalDate.toEpochDay() for the Unix epoch", () => {
    expect(isoDateToEpochDay("1970-01-01")).toBe(0);
    expect(isoDateToEpochDay("1970-01-02")).toBe(1);
    expect(isoDateToEpochDay("1969-12-31")).toBe(-1);
  });

  it("derives the same epoch day from a local timestamp regardless of time of day", () => {
    const morning = combineLocalDateTime("2026-06-01", "00:05");
    const night = combineLocalDateTime("2026-06-01", "23:55");
    expect(millisToLocalEpochDay(morning)).toBe(millisToLocalEpochDay(night));
    expect(millisToLocalEpochDay(morning)).toBe(isoDateToEpochDay("2026-06-01"));
  });
});

describe("time-of-day conversions", () => {
  it("converts minute-of-day to HH:mm and back", () => {
    expect(minuteOfDayToTimeInput(0)).toBe("00:00");
    expect(minuteOfDayToTimeInput(90)).toBe("01:30");
    expect(minuteOfDayToTimeInput(1439)).toBe("23:59");
    expect(timeInputToMinuteOfDay("01:30")).toBe(90);
  });

  it("combines a date and time input into the expected local timestamp fields", () => {
    const millis = combineLocalDateTime("2026-01-10", "08:15");
    expect(millisToDateInput(millis)).toBe("2026-01-10");
    expect(millisToTimeInput(millis)).toBe("08:15");
  });
});

describe("formatHours", () => {
  it("converts milliseconds to hours with 2 decimals", () => {
    expect(formatHours(3_600_000)).toBe("1.00");
    expect(formatHours(5_400_000)).toBe("1.50");
    expect(formatHours(0)).toBe("0.00");
  });
});
