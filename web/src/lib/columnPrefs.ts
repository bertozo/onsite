// Which report columns to include in the Reports CSV export and the printed invoice table -
// a single global, per-browser preference, mirroring Android's `report_columns`
// SharedPreferences key (one setting shared by both the Reports screen and invoice
// generation, not a per-invoice choice - see ReportViewModel/InvoiceViewModel).
import { REPORT_COLUMNS, type ReportColumn } from "./reportLogic";
import { log } from "./log";

const KEY = "onsite_report_columns";

/** DATE and HOURS are always printed - see ReportColumn.kt's ordering note in the Android app. */
const ALWAYS_ON: ReportColumn[] = ["DATE", "HOURS"];
const TOGGLEABLE = REPORT_COLUMNS.filter((c) => !ALWAYS_ON.includes(c));

export function loadReportColumns(): ReportColumn[] {
  const raw = localStorage.getItem(KEY);
  if (!raw) return [...REPORT_COLUMNS];
  try {
    const saved = new Set(JSON.parse(raw) as ReportColumn[]);
    return REPORT_COLUMNS.filter((c) => ALWAYS_ON.includes(c) || saved.has(c));
  } catch (e) {
    log.debug("stored report columns could not be parsed, falling back to all of them", e);
    return [...REPORT_COLUMNS];
  }
}

export function saveReportColumns(columns: ReportColumn[]): void {
  localStorage.setItem(KEY, JSON.stringify(columns));
}

export { ALWAYS_ON as ALWAYS_ON_COLUMNS, TOGGLEABLE as TOGGLEABLE_COLUMNS };
