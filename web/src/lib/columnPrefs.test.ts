import { afterEach, describe, expect, it } from "vitest";
import { loadReportColumns, saveReportColumns } from "./columnPrefs";
import { REPORT_COLUMNS } from "./reportLogic";

afterEach(() => localStorage.clear());

describe("columnPrefs", () => {
  it("defaults to every column when nothing is saved yet", () => {
    expect(loadReportColumns()).toEqual([...REPORT_COLUMNS]);
  });

  it("round-trips a saved selection", () => {
    saveReportColumns(["DATE", "HOURS", "SITE"]);
    expect(loadReportColumns()).toEqual(["DATE", "SITE", "HOURS"]);
  });

  it("always includes DATE and HOURS even if a stale save dropped them", () => {
    localStorage.setItem("onsite_report_columns", JSON.stringify(["SITE"]));
    const columns = loadReportColumns();
    expect(columns).toContain("DATE");
    expect(columns).toContain("HOURS");
  });
});
