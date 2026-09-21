import { useEffect, useMemo, useState } from "react";
import { backendApi, ApiError } from "../lib/backendApi";
import { useCrud } from "../lib/useCrud";
import type { ClientRequest, JobTypeRequest, SiteRequest, TrackingSessionRequest } from "../lib/types";
import { combineLocalDateTime, formatHours, formatMoney, millisToDateInput, millisToTimeInput } from "../lib/format";
import { amountOk, parseAmount, VALIDATION_MESSAGES } from "../lib/validators";
import { Badge, Button, Card, ConfirmDialog, EmptyState, ErrorBanner, Field, Input, Modal, PageHeader, Select, Spinner } from "../components/ui";

const sessionApi = {
  list: backendApi.listTrackingSessions,
  create: backendApi.createTrackingSession,
  update: backendApi.updateTrackingSession,
  remove: backendApi.deleteTrackingSession,
};

function useCatalog() {
  const [clients, setClients] = useState<ClientRequest[]>([]);
  const [sites, setSites] = useState<SiteRequest[]>([]);
  const [jobTypes, setJobTypes] = useState<JobTypeRequest[]>([]);
  useEffect(() => {
    backendApi.listClients().then(setClients).catch(() => undefined);
    backendApi.listSites().then(setSites).catch(() => undefined);
    backendApi.listJobTypes().then(setJobTypes).catch(() => undefined);
  }, []);
  return { clients, sites, jobTypes };
}

export function SessionsPage() {
  const { items, loading, error, create, update, remove } = useCrud<TrackingSessionRequest>(sessionApi);
  const { clients, sites, jobTypes } = useCatalog();
  const [editing, setEditing] = useState<TrackingSessionRequest | "new" | null>(null);
  const [deleting, setDeleting] = useState<TrackingSessionRequest | null>(null);
  const [onlyUnbilled, setOnlyUnbilled] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const sorted = useMemo(() => {
    const filtered = onlyUnbilled ? items.filter((s) => s.invoiceId == null) : items;
    return [...filtered].sort((a, b) => b.startTimestampMillis - a.startTimestampMillis);
  }, [items, onlyUnbilled]);

  async function handleSave(req: TrackingSessionRequest) {
    setSaveError(null);
    try {
      if (editing === "new") await create(req);
      else await update(req.id, req);
      setEditing(null);
    } catch (e) {
      setSaveError(e instanceof ApiError ? e.message : "Não foi possível salvar a sessão.");
    }
  }

  return (
    <div>
      <PageHeader
        title="Sessões"
        subtitle="Lançamentos manuais de horas trabalhadas"
        action={<Button onClick={() => setEditing("new")}>+ Nova sessão</Button>}
      />

      <label className="mb-3 flex items-center gap-2 text-sm text-slate-600">
        <input type="checkbox" checked={onlyUnbilled} onChange={(e) => setOnlyUnbilled(e.target.checked)} />
        Mostrar apenas não faturadas
      </label>

      {loading && <Spinner className="h-6 w-6 text-violet-600" />}
      {error && <ErrorBanner message={error} />}
      {!loading && sorted.length === 0 && (
        <EmptyState title="Nenhuma sessão encontrada" description="Lance manualmente as horas trabalhadas para gerar relatórios e faturas." />
      )}

      {sorted.length > 0 && (
        <Card>
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-400">
              <tr>
                <th className="px-4 py-3">Data</th>
                <th className="px-4 py-3">Cliente</th>
                <th className="px-4 py-3">Local</th>
                <th className="px-4 py-3">Serviço</th>
                <th className="px-4 py-3">Horário</th>
                <th className="px-4 py-3">Horas</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sorted.map((s) => (
                <tr key={s.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 text-slate-600">{millisToDateInput(s.startTimestampMillis)}</td>
                  <td className="px-4 py-3 text-slate-800">{s.clientName || "—"}</td>
                  <td className="px-4 py-3 text-slate-600">{s.siteLabel || "—"}</td>
                  <td className="px-4 py-3 text-slate-600">{s.jobTypeLabel || "—"}</td>
                  <td className="px-4 py-3 text-slate-600">
                    {millisToTimeInput(s.startTimestampMillis)}
                    {s.stopTimestampMillis != null ? ` – ${millisToTimeInput(s.stopTimestampMillis)}` : " (em aberto)"}
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    {s.stopTimestampMillis != null ? formatHours(s.stopTimestampMillis - s.startTimestampMillis) : "—"}
                    {s.hourlyRate != null && s.stopTimestampMillis != null && (
                      <span className="ml-1 text-xs text-slate-400">
                        ({formatMoney(((s.stopTimestampMillis - s.startTimestampMillis) / 3_600_000) * s.hourlyRate)})
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {s.invoiceId ? <Badge tone="green">Faturada</Badge> : <Badge>Pendente</Badge>}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button onClick={() => setEditing(s)} className="mr-3 text-violet-600 hover:underline">
                      Editar
                    </button>
                    <button onClick={() => setDeleting(s)} className="text-red-600 hover:underline">
                      Excluir
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {editing && (
        <SessionFormModal
          initial={editing === "new" ? null : editing}
          clients={clients}
          sites={sites}
          jobTypes={jobTypes}
          error={saveError}
          onClose={() => {
            setEditing(null);
            setSaveError(null);
          }}
          onSave={handleSave}
        />
      )}

      {deleting && (
        <ConfirmDialog
          title="Excluir sessão"
          message="Tem certeza que deseja excluir esta sessão?"
          danger
          confirmLabel="Excluir"
          onCancel={() => setDeleting(null)}
          onConfirm={async () => {
            await remove(deleting.id);
            setDeleting(null);
          }}
        />
      )}
    </div>
  );
}

function SessionFormModal({
  initial,
  clients,
  sites,
  jobTypes,
  error,
  onClose,
  onSave,
}: {
  initial: TrackingSessionRequest | null;
  clients: ClientRequest[];
  sites: SiteRequest[];
  jobTypes: JobTypeRequest[];
  error: string | null;
  onClose: () => void;
  onSave: (req: TrackingSessionRequest) => Promise<void>;
}) {
  const [clientName, setClientName] = useState(initial?.clientName ?? "");
  const [siteLabel, setSiteLabel] = useState(initial?.siteLabel ?? "");
  const [jobTypeLabel, setJobTypeLabel] = useState(initial?.jobTypeLabel ?? "");
  const [date, setDate] = useState(initial ? millisToDateInput(initial.startTimestampMillis) : millisToDateInput(Date.now()));
  const [startTime, setStartTime] = useState(initial ? millisToTimeInput(initial.startTimestampMillis) : "08:00");
  const [endTime, setEndTime] = useState(initial?.stopTimestampMillis != null ? millisToTimeInput(initial.stopTimestampMillis) : "16:00");
  const [hourlyRate, setHourlyRate] = useState(initial?.hourlyRate?.toString() ?? "");
  const rateError = amountOk(hourlyRate) ? undefined : VALIDATION_MESSAGES.amount;
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const selectedSite = sites.find((s) => s.label === siteLabel);
  const selectedClient = clients.find((c) => c.name === clientName);

  return (
    <Modal title={initial ? "Editar sessão" : "Nova sessão"} onClose={onClose}>
      <form
        className="space-y-3"
        onSubmit={async (e) => {
          e.preventDefault();
          const start = combineLocalDateTime(date, startTime);
          const stop = combineLocalDateTime(date, endTime);
          if (stop <= start) {
            setFormError("O horário de término deve ser depois do início.");
            return;
          }
          setFormError(null);
          setSaving(true);
          await onSave({
            id: initial?.id ?? crypto.randomUUID(),
            clientName: clientName || null,
            siteLabel: siteLabel || null,
            jobTypeLabel: jobTypeLabel || null,
            startTimestampMillis: start,
            startLatitude: selectedSite?.latitude ?? 0,
            startLongitude: selectedSite?.longitude ?? 0,
            stopTimestampMillis: stop,
            stopLatitude: selectedSite?.latitude ?? 0,
            stopLongitude: selectedSite?.longitude ?? 0,
            hourlyRate: hourlyRate.trim() ? parseAmount(hourlyRate) : null,
            invoiceId: initial?.invoiceId ?? null,
          });
          setSaving(false);
        }}
      >
        <Field label="Cliente">
          <Select value={clientName} onChange={(e) => setClientName(e.target.value)}>
            <option value="">—</option>
            {clients.map((c) => (
              <option key={c.id} value={c.name}>
                {c.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Local">
          <Select value={siteLabel} onChange={(e) => setSiteLabel(e.target.value)}>
            <option value="">—</option>
            {sites.map((s) => (
              <option key={s.id} value={s.label}>
                {s.label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Serviço">
          <Select value={jobTypeLabel} onChange={(e) => setJobTypeLabel(e.target.value)}>
            <option value="">—</option>
            {jobTypes.map((j) => (
              <option key={j.id} value={j.name}>
                {j.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Data">
          <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Início">
            <Input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} required />
          </Field>
          <Field label="Fim">
            <Input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} required />
          </Field>
        </div>
        <Field
          label="Valor/h (substitui o do cliente)"
          hint={selectedClient?.hourlyRate != null ? `Padrão do cliente: ${selectedClient.hourlyRate.toFixed(2)}` : undefined}
          error={rateError}
        >
          <Input inputMode="decimal" value={hourlyRate} onChange={(e) => setHourlyRate(e.target.value)} invalid={!!rateError} />
        </Field>

        {formError && <ErrorBanner message={formError} />}
        {error && <ErrorBanner message={error} />}

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={saving || !!rateError}>
            {saving && <Spinner className="h-4 w-4" />}
            Salvar
          </Button>
        </div>
      </form>
    </Modal>
  );
}
