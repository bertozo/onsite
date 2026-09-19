import { useEffect, useMemo, useState } from "react";
import { backendApi } from "../lib/backendApi";
import type { CompanyRequest, InvoiceRequest, SiteRequest, TrackingSessionRequest } from "../lib/types";
import { buildInvoiceLines, invoiceTotalAmount, invoiceTotalHours, lineAmount, lineHours, type InvoiceLine } from "../lib/invoiceLogic";
import { epochDayToLabel, formatMoney, millisToLocalEpochDay, millisToTimeInput, todayEpochDay } from "../lib/format";
import { loadProfile, saveProfile, type BusinessProfile } from "../lib/profileStore";
import { loadReportColumns } from "../lib/columnPrefs";
import type { ReportColumn } from "../lib/reportLogic";
import { Badge, Button, Card, EmptyState, ErrorBanner, Field, Input, Modal, PageHeader, Select, Spinner, Textarea } from "../components/ui";

const STATUS_TONE: Record<InvoiceRequest["status"], "default" | "green" | "amber" | "red" | "violet"> = {
  DRAFT: "default",
  SENT: "amber",
  PAID: "green",
  VOID: "red",
};
const STATUS_LABEL: Record<InvoiceRequest["status"], string> = { DRAFT: "Rascunho", SENT: "Enviada", PAID: "Paga", VOID: "Anulada" };

export function InvoicesPage() {
  const [invoices, setInvoices] = useState<InvoiceRequest[] | null>(null);
  const [companies, setCompanies] = useState<CompanyRequest[]>([]);
  const [sites, setSites] = useState<SiteRequest[]>([]);
  const [sessions, setSessions] = useState<TrackingSessionRequest[]>([]);
  const [creating, setCreating] = useState(false);
  const [viewing, setViewing] = useState<InvoiceRequest | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function reload() {
    const [inv, comp, st, ses] = await Promise.all([
      backendApi.listInvoices(),
      backendApi.listCompanies(),
      backendApi.listSites(),
      backendApi.listTrackingSessions(),
    ]);
    setInvoices(inv);
    setCompanies(comp);
    setSites(st);
    setSessions(ses);
  }

  useEffect(() => {
    reload().catch(() => setInvoices([]));
  }, []);

  if (!invoices) return <Spinner className="h-6 w-6 text-violet-600" />;

  const sitesByLabel = new Map(sites.map((s) => [s.label, s]));

  return (
    <div>
      <PageHeader title="Faturas" subtitle="Gere faturas a partir das sessões não faturadas" action={<Button onClick={() => setCreating(true)}>+ Nova fatura</Button>} />

      {error && <ErrorBanner message={error} />}

      {invoices.length === 0 ? (
        <EmptyState title="Nenhuma fatura gerada" description="Gere uma fatura a partir das sessões concluídas e não faturadas de um cliente." />
      ) : (
        <Card>
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-400">
              <tr>
                <th className="px-4 py-3">Número</th>
                <th className="px-4 py-3">Cliente</th>
                <th className="px-4 py-3">Período</th>
                <th className="px-4 py-3">Horas</th>
                <th className="px-4 py-3">Valor</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {[...invoices]
                .sort((a, b) => b.createdAtMillis - a.createdAtMillis)
                .map((inv) => (
                  <tr key={inv.id} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium text-slate-800">{inv.number}</td>
                    <td className="px-4 py-3 text-slate-600">{inv.companyName}</td>
                    <td className="px-4 py-3 text-slate-500">
                      {epochDayToLabel(inv.periodStartEpochDay)} – {epochDayToLabel(inv.periodEndEpochDay)}
                    </td>
                    <td className="px-4 py-3 text-slate-600">{inv.totalHours.toFixed(2)}</td>
                    <td className="px-4 py-3 text-slate-600">{formatMoney(inv.totalAmount)}</td>
                    <td className="px-4 py-3">
                      <Badge tone={STATUS_TONE[inv.status]}>{STATUS_LABEL[inv.status]}</Badge>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button onClick={() => setViewing(inv)} className="text-violet-600 hover:underline">
                        Abrir
                      </button>
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </Card>
      )}

      {creating && (
        <NewInvoiceModal
          companies={companies}
          sessions={sessions}
          sitesByLabel={sitesByLabel}
          existingInvoices={invoices}
          onClose={() => setCreating(false)}
          onError={setError}
          onCreated={async () => {
            setCreating(false);
            await reload();
          }}
        />
      )}

      {viewing && (
        <InvoiceDetailModal
          invoice={viewing}
          sessions={sessions}
          sitesByLabel={sitesByLabel}
          onClose={() => setViewing(null)}
          onError={setError}
          onChanged={async () => {
            await reload();
            setViewing(null);
          }}
        />
      )}
    </div>
  );
}

function nextInvoiceNumber(existing: InvoiceRequest[]): string {
  const max = existing.reduce((m, inv) => {
    const match = /^INV-(\d+)$/.exec(inv.number);
    return match ? Math.max(m, Number(match[1])) : m;
  }, 0);
  return `INV-${String(max + 1).padStart(4, "0")}`;
}

function NewInvoiceModal({
  companies,
  sessions,
  sitesByLabel,
  existingInvoices,
  onClose,
  onCreated,
  onError,
}: {
  companies: CompanyRequest[];
  sessions: TrackingSessionRequest[];
  sitesByLabel: Map<string, SiteRequest>;
  existingInvoices: InvoiceRequest[];
  onClose: () => void;
  onCreated: () => Promise<void>;
  onError: (msg: string | null) => void;
}) {
  const [companyId, setCompanyId] = useState(companies[0]?.id ?? "");
  const company = companies.find((c) => c.id === companyId) ?? null;
  const [hourlyRate, setHourlyRate] = useState(company?.hourlyRate?.toString() ?? "");
  const [notes, setNotes] = useState("");
  const [number, setNumber] = useState(nextInvoiceNumber(existingInvoices));
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setHourlyRate(company?.hourlyRate?.toString() ?? "");
  }, [companyId]); // eslint-disable-line react-hooks/exhaustive-deps

  const unbilled = useMemo(
    () => sessions.filter((s) => s.companyName === company?.name && s.stopTimestampMillis != null && s.invoiceId == null),
    [sessions, company],
  );
  const rate = hourlyRate ? Number(hourlyRate) : null;
  const baseLines = useMemo(() => buildInvoiceLines(unbilled, sitesByLabel, rate), [unbilled, sitesByLabel, rate]);

  /**
   * Per-line rate overrides from the review step, keyed by index into baseLines (stable for
   * as long as unbilled/rate don't change - not by the line's own rate, which is exactly
   * what an override changes). "" clears back to the line's base rate.
   */
  const [rateOverrides, setRateOverrides] = useState<Record<number, string>>({});
  useEffect(() => setRateOverrides({}), [companyId]);

  const lines = useMemo(
    () =>
      baseLines.map((l, i) => {
        const override = rateOverrides[i];
        if (override === undefined || override === "") return l;
        const parsed = Number(override);
        return Number.isFinite(parsed) ? { ...l, hourlyRate: parsed } : l;
      }),
    [baseLines, rateOverrides],
  );

  async function handleCreate() {
    if (!company) return;
    if (unbilled.length === 0) {
      onError(`Nenhuma sessão pendente de fatura para ${company.name}.`);
      return;
    }
    onError(null);
    setSaving(true);
    try {
      // Persist any per-line rate override onto its sessions before totalling the invoice,
      // mirroring the Android review screen's setLineRate (an override sticks to those
      // sessions for future reports/invoices, not just this one).
      const bySessionId = new Map(unbilled.map((s) => [s.id, s]));
      const changedSessionUpdates = lines.flatMap((l, i) => {
        if (l.hourlyRate === baseLines[i].hourlyRate) return [];
        return l.sessionIds.map((id) => {
          const session = bySessionId.get(id);
          if (!session) return Promise.resolve();
          return backendApi.updateTrackingSession(id, { ...session, hourlyRate: l.hourlyRate });
        });
      });
      await Promise.all(changedSessionUpdates);

      const periodStart = Math.min(...unbilled.map((s) => millisToLocalEpochDay(s.startTimestampMillis)));
      const periodEnd = Math.max(...unbilled.map((s) => millisToLocalEpochDay(s.startTimestampMillis)));
      const invoiceId = crypto.randomUUID();
      await backendApi.createInvoice({
        id: invoiceId,
        number: number.trim(),
        companyName: company.name,
        periodStartEpochDay: periodStart,
        periodEndEpochDay: periodEnd,
        issueDateEpochDay: todayEpochDay(),
        totalHours: invoiceTotalHours(lines),
        totalAmount: invoiceTotalAmount(lines),
        hourlyRate: rate,
        status: "DRAFT",
        notes: notes.trim() || null,
        createdAtMillis: Date.now(),
      });
      await Promise.all(unbilled.map((s) => backendApi.updateTrackingSession(s.id, { ...s, invoiceId })));
      await onCreated();
    } catch {
      onError("Não foi possível gerar a fatura.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal title="Nova fatura" onClose={onClose} wide>
      <div className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <Field label="Cliente">
            <Select value={companyId} onChange={(e) => setCompanyId(e.target.value)}>
              {companies.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Número da fatura">
            <Input value={number} onChange={(e) => setNumber(e.target.value)} />
          </Field>
        </div>
        <Field label="Valor/h padrão" hint="Sessões com valor próprio mantêm seu valor individual">
          <Input type="number" step="0.01" min="0" value={hourlyRate} onChange={(e) => setHourlyRate(e.target.value)} />
        </Field>
        <Field label="Observações">
          <Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>

        <div>
          <p className="mb-2 text-sm font-medium text-slate-700">
            {lines.length} {lines.length === 1 ? "dia" : "dias"} pendente(s) · {invoiceTotalHours(lines).toFixed(2)}h · {formatMoney(invoiceTotalAmount(lines))}
          </p>
          {lines.length === 0 ? (
            <p className="text-sm text-slate-400">Nenhuma sessão concluída e não faturada para este cliente.</p>
          ) : (
            <div className="max-h-56 overflow-y-auto rounded-lg border border-slate-200">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-50 text-slate-500">
                  <tr>
                    <th className="px-3 py-2">Data</th>
                    <th className="px-3 py-2">Local</th>
                    <th className="px-3 py-2">Horas</th>
                    <th className="px-3 py-2">Valor/h</th>
                    <th className="px-3 py-2">Valor</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {lines.map((l: InvoiceLine, i) => (
                    <tr key={i}>
                      <td className="px-3 py-1.5">{epochDayToLabel(l.dateEpochDay)}</td>
                      <td className="px-3 py-1.5">{l.sites.join(", ") || "—"}</td>
                      <td className="px-3 py-1.5">{lineHours(l).toFixed(2)}</td>
                      <td className="px-3 py-1.5">
                        <input
                          type="number"
                          step="0.01"
                          min="0"
                          placeholder={baseLines[i].hourlyRate?.toString() ?? "—"}
                          value={rateOverrides[i] ?? ""}
                          onChange={(e) => setRateOverrides((prev) => ({ ...prev, [i]: e.target.value }))}
                          className="w-20 rounded border border-slate-200 px-1.5 py-0.5 text-xs"
                        />
                      </td>
                      <td className="px-3 py-1.5">{formatMoney(lineAmount(l))}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={handleCreate} disabled={saving || lines.length === 0}>
            {saving && <Spinner className="h-4 w-4" />}
            Gerar fatura
          </Button>
        </div>
      </div>
    </Modal>
  );
}

function InvoiceDetailModal({
  invoice,
  sessions,
  sitesByLabel,
  onClose,
  onChanged,
  onError,
}: {
  invoice: InvoiceRequest;
  sessions: TrackingSessionRequest[];
  sitesByLabel: Map<string, SiteRequest>;
  onClose: () => void;
  onChanged: () => Promise<void>;
  onError: (msg: string | null) => void;
}) {
  const [profile, setProfile] = useState<BusinessProfile>(loadProfile());
  const [busy, setBusy] = useState(false);
  const invoiceSessions = sessions.filter((s) => s.invoiceId === invoice.id);
  const lines = buildInvoiceLines(invoiceSessions, sitesByLabel, invoice.hourlyRate ?? null);
  const columns = loadReportColumns();
  const showAmounts = lines.some((l) => l.hourlyRate != null);
  const colOn = (c: ReportColumn) => columns.includes(c);

  async function setStatus(status: InvoiceRequest["status"], extra: Partial<InvoiceRequest> = {}) {
    setBusy(true);
    onError(null);
    try {
      await backendApi.updateInvoice(invoice.id, { ...invoice, status, ...extra });
      if (status === "VOID") {
        await Promise.all(invoiceSessions.map((s) => backendApi.updateTrackingSession(s.id, { ...s, invoiceId: null })));
      }
      await onChanged();
    } catch {
      onError("Não foi possível atualizar a fatura.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={`Fatura ${invoice.number}`} onClose={onClose} wide>
      <div className="mb-4 flex flex-wrap gap-2">
        {invoice.status === "DRAFT" && (
          <Button variant="secondary" disabled={busy} onClick={() => setStatus("SENT", { sentAtMillis: Date.now() })}>
            Marcar como enviada
          </Button>
        )}
        {invoice.status !== "PAID" && invoice.status !== "VOID" && (
          <Button variant="secondary" disabled={busy} onClick={() => setStatus("PAID", { paidAtMillis: Date.now() })}>
            Marcar como paga
          </Button>
        )}
        {invoice.status !== "VOID" && (
          <Button variant="danger" disabled={busy} onClick={() => setStatus("VOID")}>
            Anular fatura
          </Button>
        )}
        <Button variant="secondary" onClick={() => window.print()} className="ml-auto">
          Imprimir / salvar PDF
        </Button>
      </div>

      <div id="invoice-print" className="rounded-lg border border-slate-200 p-6 text-sm text-slate-800">
        <div className="mb-6 flex justify-between">
          <div>
            <p className="text-lg font-semibold">{profile.name || "Sua empresa"}</p>
            {profile.abn && <p className="text-xs text-slate-500">ABN: {profile.abn}</p>}
            {profile.phone && <p className="text-xs text-slate-500">{profile.phone}</p>}
            {profile.email && <p className="text-xs text-slate-500">{profile.email}</p>}
          </div>
          <div className="text-right">
            <p className="text-lg font-semibold">Fatura {invoice.number}</p>
            <p className="text-xs text-slate-500">Emitida em {epochDayToLabel(invoice.issueDateEpochDay)}</p>
            <p className="text-xs text-slate-500">
              Período: {epochDayToLabel(invoice.periodStartEpochDay)} – {epochDayToLabel(invoice.periodEndEpochDay)}
            </p>
          </div>
        </div>

        <p className="mb-4 text-sm">
          <span className="text-slate-400">Cliente: </span>
          {invoice.companyName}
        </p>

        <table className="mb-4 w-full text-left text-xs">
          <thead className="border-b border-slate-300 text-slate-500">
            <tr>
              <th className="py-1.5">Data</th>
              {colOn("SITE") && <th className="py-1.5">Local</th>}
              {colOn("ADDRESS") && <th className="py-1.5">Endereço</th>}
              {colOn("COMPANY") && <th className="py-1.5">Cliente</th>}
              {colOn("JOB_TYPE") && <th className="py-1.5">Serviço</th>}
              {colOn("START_TIME") && <th className="py-1.5">Início</th>}
              {colOn("END_TIME") && <th className="py-1.5">Fim</th>}
              <th className="py-1.5">Horas</th>
              {showAmounts && (
                <>
                  <th className="py-1.5">Valor/h</th>
                  <th className="py-1.5">Valor</th>
                </>
              )}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {lines.map((l, i) => (
              <tr key={i}>
                <td className="py-1.5">{epochDayToLabel(l.dateEpochDay)}</td>
                {colOn("SITE") && <td className="py-1.5">{l.sites.join(", ") || "—"}</td>}
                {colOn("ADDRESS") && <td className="py-1.5">{l.addresses.join(" | ") || "—"}</td>}
                {colOn("COMPANY") && <td className="py-1.5">{l.companies.join(", ") || "—"}</td>}
                {colOn("JOB_TYPE") && <td className="py-1.5">{l.jobTypes.join(", ") || "—"}</td>}
                {colOn("START_TIME") && <td className="py-1.5">{millisToTimeInput(l.startTimestampMillis)}</td>}
                {colOn("END_TIME") && <td className="py-1.5">{millisToTimeInput(l.endTimestampMillis)}</td>}
                <td className="py-1.5">{lineHours(l).toFixed(2)}</td>
                {showAmounts && (
                  <>
                    <td className="py-1.5">{l.hourlyRate != null ? formatMoney(l.hourlyRate) : "—"}</td>
                    <td className="py-1.5">{formatMoney(lineAmount(l))}</td>
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>

        {invoice.notes && <p className="mb-4 whitespace-pre-wrap text-xs text-slate-600">{invoice.notes}</p>}

        <div className="flex justify-end border-t border-slate-300 pt-3">
          <div className="text-right">
            <p className="text-xs text-slate-500">Total de horas: {invoice.totalHours.toFixed(2)}</p>
            {invoice.totalAmount != null && <p className="text-base font-semibold">Total: {formatMoney(invoice.totalAmount)}</p>}
          </div>
        </div>

        {profile.bankDetails && (
          <div className="mt-4 border-t border-dashed border-slate-200 pt-3 text-xs text-slate-500">
            <p className="font-medium text-slate-600">Dados para pagamento</p>
            <p className="whitespace-pre-wrap">{profile.bankDetails}</p>
          </div>
        )}
      </div>

      <ProfileEditorHint profile={profile} onSaved={setProfile} />
    </Modal>
  );
}

function ProfileEditorHint({ profile, onSaved }: { profile: BusinessProfile; onSaved: (p: BusinessProfile) => void }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(profile);
  if (!editing) {
    return (
      <button onClick={() => setEditing(true)} className="mt-3 text-xs text-violet-600 hover:underline print:hidden">
        Editar dados da minha empresa (cabeçalho da fatura)
      </button>
    );
  }
  return (
    <div className="mt-3 space-y-2 rounded-lg border border-slate-200 p-3 print:hidden">
      <div className="grid grid-cols-2 gap-2">
        <Input placeholder="Nome da empresa" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
        <Input placeholder="ABN" value={draft.abn} onChange={(e) => setDraft({ ...draft, abn: e.target.value })} />
        <Input placeholder="Telefone" value={draft.phone} onChange={(e) => setDraft({ ...draft, phone: e.target.value })} />
        <Input placeholder="E-mail" value={draft.email} onChange={(e) => setDraft({ ...draft, email: e.target.value })} />
      </div>
      <Textarea placeholder="Dados bancários / forma de pagamento" rows={2} value={draft.bankDetails} onChange={(e) => setDraft({ ...draft, bankDetails: e.target.value })} />
      <div className="flex justify-end gap-2">
        <Button
          variant="secondary"
          onClick={() => {
            setEditing(false);
            setDraft(profile);
          }}
        >
          Cancelar
        </Button>
        <Button
          onClick={() => {
            saveProfile(draft);
            onSaved(draft);
            setEditing(false);
          }}
        >
          Salvar
        </Button>
      </div>
    </div>
  );
}
