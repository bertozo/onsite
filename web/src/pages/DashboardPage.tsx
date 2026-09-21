import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { backendApi } from "../lib/backendApi";
import { useAuth } from "../context/AuthContext";
import type { ClientRequest, TrackingSessionRequest } from "../lib/types";
import { periodRange, summarize } from "../lib/reportLogic";
import { formatHours, formatMoney, millisToDateInput, millisToTimeInput } from "../lib/format";
import { Card, PageHeader, Spinner } from "../components/ui";

export function DashboardPage() {
  const { state } = useAuth();
  const [sessions, setSessions] = useState<TrackingSessionRequest[] | null>(null);
  const [clients, setClients] = useState<ClientRequest[]>([]);

  useEffect(() => {
    backendApi.listTrackingSessions().then(setSessions).catch(() => setSessions([]));
    backendApi.listClients().then(setClients).catch(() => undefined);
  }, []);

  const ratesByClient = useMemo(() => new Map(clients.map((c) => [c.name, c.hourlyRate])), [clients]);

  if (!sessions) return <Spinner className="h-6 w-6 text-violet-600" />;

  const [weekStart, weekEnd] = periodRange("THIS_WEEK")!;
  const weekSessions = sessions.filter((s) => s.startTimestampMillis >= weekStart.getTime() && s.startTimestampMillis < weekEnd.getTime() + 86_400_000);
  const summary = summarize(weekSessions, ratesByClient);
  const unbilledAll = summarize(
    sessions.filter((s) => s.invoiceId == null),
    ratesByClient,
  );
  const recent = [...sessions].sort((a, b) => b.startTimestampMillis - a.startTimestampMillis).slice(0, 6);

  return (
    <div>
      <PageHeader title={`Olá, ${state.status === "loggedIn" ? state.email : ""}`} subtitle={state.status === "loggedIn" ? state.activeAccountName : undefined} />

      <div className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard label="Horas esta semana" value={formatHours(summary.totalMillis)} />
        <StatCard label="Dias trabalhados" value={String(summary.workedDays)} />
        <StatCard label="Estimado esta semana" value={formatMoney(summary.estimatedAmount)} />
        <StatCard label="A faturar (total)" value={formatMoney(unbilledAll.unbilledAmount)} sub={`${formatHours(unbilledAll.unbilledMillis)}h`} />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="p-5 lg:col-span-2">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-700">Sessões recentes</h2>
            <Link to="/sessions" className="text-sm text-violet-600 hover:underline">
              Ver todas
            </Link>
          </div>
          {recent.length === 0 ? (
            <p className="text-sm text-slate-400">Nenhuma sessão lançada ainda.</p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {recent.map((s) => (
                <li key={s.id} className="flex items-center justify-between py-2 text-sm">
                  <div>
                    <p className="font-medium text-slate-800">{s.clientName || "Sem cliente"}</p>
                    <p className="text-xs text-slate-400">
                      {millisToDateInput(s.startTimestampMillis)} · {millisToTimeInput(s.startTimestampMillis)}
                      {s.stopTimestampMillis != null ? `–${millisToTimeInput(s.stopTimestampMillis)}` : ""}
                    </p>
                  </div>
                  <span className="text-slate-500">{s.stopTimestampMillis != null ? `${formatHours(s.stopTimestampMillis - s.startTimestampMillis)}h` : "em aberto"}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card className="p-5">
          <h2 className="mb-3 text-sm font-semibold text-slate-700">Atalhos</h2>
          <div className="flex flex-col gap-2">
            <Link to="/sessions" className="rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50">
              + Lançar sessão
            </Link>
            <Link to="/invoices" className="rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50">
              Gerar fatura
            </Link>
            <Link to="/planning" className="rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50">
              Planejar próximos dias
            </Link>
          </div>
        </Card>
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
