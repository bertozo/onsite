import { REPORT_COLUMNS, REPORT_COLUMN_LABELS, type ReportColumn } from "../lib/reportLogic";
import { ALWAYS_ON_COLUMNS } from "../lib/columnPrefs";

/** Checkbox grid for which report columns print on the Reports CSV and invoice table. */
export function ColumnPicker({ selected, onChange }: { selected: ReportColumn[]; onChange: (columns: ReportColumn[]) => void }) {
  const set = new Set(selected);
  return (
    <div className="flex flex-wrap gap-x-4 gap-y-2">
      {REPORT_COLUMNS.map((column) => {
        const forced = (ALWAYS_ON_COLUMNS as readonly ReportColumn[]).includes(column);
        return (
          <label key={column} className={`flex items-center gap-1.5 text-sm ${forced ? "text-slate-400" : "text-slate-700"}`}>
            <input
              type="checkbox"
              checked={forced || set.has(column)}
              disabled={forced}
              onChange={(e) => {
                const next = new Set(set);
                if (e.target.checked) next.add(column);
                else next.delete(column);
                onChange(REPORT_COLUMNS.filter((c) => next.has(c)));
              }}
            />
            {REPORT_COLUMN_LABELS[column]}
          </label>
        );
      })}
    </div>
  );
}
