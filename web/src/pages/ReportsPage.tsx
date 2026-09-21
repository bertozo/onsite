import { useEffect, useMemo, useState } from "react";
import { backendApi } from "../lib/backendApi";
import type { ClientRequest, SiteRequest, TrackingSessionRequest } from "../lib/types";
import { buildCsv, periodRange, summarize, totalsByClient, totalsByJobType, totalsBySite, weeklyTotals, type ReportPeriod } from "../lib/reportLogic";
import { loadReportColumns } from "../lib/columnPrefs";
import { formatHours, formatMoney } from "../lib/format";
import { Card, PageHeader, Select, Spinner } from "../components/ui";
import { Link } from "react-router-dom";

const PERIOD_LABELS: Record<ReportPeriod, string> = {
  THIS_WEEK: "Esta semana",
  FORTNIGHT: "Últimas 2 semanas",
  THIS_MONTH: "Este mês",
  LAST_MONTH: "Mês passado",
  CUSTOM: "Todas as sessões",
};

export function ReportsPage() {
  const [sessions, setSessions] = useState<TrackingSessionRequest[] | null>(null);
  const [clients, setClients] = useState<ClientRequest[]>([]);
  const [sites, setSites] = useState<SiteRequest[]>([]);
  const [period, setPeriod] = useState<ReportPeriod>("THIS_WEEK");
  const [clientFilter, setClientFilter] = useState("");
  const [unbilledOnly, setUnbilledOnly] = useState(false);

  useEffect(() => {
    backendApi.listTrackingSessions().then(setSessions).catch(() => setSessions([]));
    backendApi.listClients().then(setClients).catch(() => undefined);
    backendApi.listSites().then(setSites).catch(() => undefined);
  }, []);

  const ratesByClient = useMemo(() => new Map(clients.map((c) => [c.name, c.hourlyRate])), [clients]);
  const sitesByLabel = useMemo(() => new Map(sites.map((s) => [s.label, s])), [sites]);

  const range = periodRange(period);
  const filtered = useMemo(() => {
    if (!sessions) return [];
    return sessions.filter((s) => {
      if (range && (s.startTimestampMillis < range[0].getTime() || s.startTimestampMillis >= range[1].getTime() + 86_400_000)) return false;
      if (clientFilter && s.clientName !== clientFilter) return false;
      if (unbilledOnly && s.invoiceId != null) return false;
      return true;
    });
  }, [sessions, range, clientFilter, unbilledOnly]);

  if (!sessions) return <Spinner className="h-6 w-6 text-violet-600" />;

  const summary = summarize(filtered, ratesByClient);
  const weeks = range ? weeklyTotals(filtered, range[0], range[1]) : [];
  const maxWeekMillis = Math.max(1, ...weeks.map((w) => w.millis));

  function downloadCsv() {
    const csv = buildCsv(filtered, loadReportColumns(), sitesByLabel, ratesByClient);
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `relatorio-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div>
      <PageHeader title="Relatórios" subtitle="Resumo das horas trabalhadas" />

      <div className="mb-5 flex flex-wrap items-center gap-3">
        <Select value={period} onChange={(e) => setPeriod(e.target.value as ReportPeriod)} className="w-48">
          {Object.entries(PERIOD_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <Select value={clientFilter} onChange={(e) => setClientFilter(e.target.value)} className="w-48">
          <option value="">Todos os clientes</option>
          {clients.map((c) => (
            <option key={c.id} value={c.name}>
              {c.name}
            </option>
          ))}
        </Select>
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input type="checkbox" checked={unbilledOnly} onChange={(e) => setUnbilledOnly(e.target.checked)} />
          Apenas não faturadas
        </label>
        <button onClick={downloadCsv} className="ml-auto rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50">
          Exportar CSV
        </button>
        <Link to="/settings" className="text-xs text-slate-400 hover:text-violet-600 hover:underline">
          Escolher colunas
        </Link>
      </div>

      <div className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard label="Total de horas" value={formatHours(summary.totalMillis)} />
        <StatCard label="Dias trabalhados" value={String(summary.workedDays)} />
        <StatCard label="Valor estimado" value={formatMoney(summary.estimatedAmount)} />
        <StatCard label="A faturar" value={formatMoney(summary.unbilledAmount)} sub={`${formatHours(summary.unbilledMillis)}h`} />
      </div>

      {weeks.length > 0 && (
        <Card className="mb-6 p-5">
          <h2 className="mb-4 text-sm font-semibold text-slate-700">Horas por semana</h2>
          <div className="flex items-end gap-3" style={{ height: 140 }}>
            {weeks.map((w) => (
              <div key={w.weekStart.toISOString()} className="flex flex-1 flex-col items-center gap-1">
                <div className="flex h-full w-full items-end">
                  <div
                    className="w-full rounded-t bg-violet-500"
                    style={{ height: `${Math.max(4, (w.millis / maxWeekMillis) * 100)}%` }}
                    title={formatHours(w.millis)}
                  />
                </div>
                <span className="text-[10px] text-slate-400">{w.weekStart.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" })}</span>
              </div>
            ))}
          </div>
        </Card>
      )}

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <BreakdownCard title="Por local" totals={totalsBySite(filtered)} />
        <BreakdownCard title="Por cliente" totals={totalsByClient(filtered)} />
        <BreakdownCard title="Por serviço" totals={totalsByJobType(filtered)} />
      </div>
    </div>
  );
}

function StatCard({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <Card className="p-4">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-400">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900">{value}</p>
      {sub && <p className="text-xs text-slate-400">{sub}</p>}
    </Card>
  );
}

function BreakdownCard({ title, totals }: { title: string; totals: { label: string; millis: number }[] }) {
  const max = Math.max(1, ...totals.map((t) => t.millis));
  return (
    <Card className="p-5">
      <h2 className="mb-3 text-sm font-semibold text-slate-700">{title}</h2>
      {totals.length === 0 && <p className="text-sm text-slate-400">Sem dados no período.</p>}
      <ul className="space-y-2">
        {totals.slice(0, 8).map((t) => (
          <li key={t.label || "—"}>
            <div className="mb-0.5 flex justify-between text-xs text-slate-600">
              <span className="truncate">{t.label || "Sem etiqueta"}</span>
              <span>{formatHours(t.millis)}h</span>
            </div>
            <div className="h-1.5 rounded-full bg-slate-100">
              <div className="h-1.5 rounded-full bg-violet-400" style={{ width: `${(t.millis / max) * 100}%` }} />
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}
