import { useEffect, useMemo, useState } from "react";
import { backendApi } from "../lib/backendApi";
import { useAuth } from "../context/AuthContext";
import { useCrud } from "../lib/useCrud";
import type { ClientRequest, JobTypeRequest, MemberDto, PlannedJobRequest, SiteRequest } from "../lib/types";
import { epochDayToIsoDate, epochDayToLabel, isoDateToEpochDay, minuteOfDayToTimeInput, timeInputToMinuteOfDay, todayEpochDay } from "../lib/format";
import { Button, Card, ConfirmDialog, EmptyState, Field, Input, Modal, PageHeader, Select, Spinner, Textarea } from "../components/ui";

const api = {
  list: backendApi.listPlannedJobs,
  create: backendApi.createPlannedJob,
  update: backendApi.updatePlannedJob,
  remove: backendApi.deletePlannedJob,
};

export function PlanningPage() {
  const { state } = useAuth();
  const isOwner = state.status === "loggedIn" && state.activeRole === "OWNER";
  const { items, loading, error, create, update, remove } = useCrud<PlannedJobRequest>(api);
  const [clients, setClients] = useState<ClientRequest[]>([]);
  const [sites, setSites] = useState<SiteRequest[]>([]);
  const [jobTypes, setJobTypes] = useState<JobTypeRequest[]>([]);
  const [members, setMembers] = useState<MemberDto[]>([]);
  const [editing, setEditing] = useState<PlannedJobRequest | "new" | null>(null);
  const [deleting, setDeleting] = useState<PlannedJobRequest | null>(null);
  const [fromDate, setFromDate] = useState(epochDayToIsoDate(todayEpochDay()));

  useEffect(() => {
    backendApi.listClients().then(setClients).catch(() => undefined);
    backendApi.listSites().then(setSites).catch(() => undefined);
    backendApi.listJobTypes().then(setJobTypes).catch(() => undefined);
    if (isOwner) backendApi.listAccountMembers().then(setMembers).catch(() => undefined);
  }, [isOwner]);

  const membersById = useMemo(() => new Map(members.map((m) => [m.userId, m])), [members]);

  const grouped = useMemo(() => {
    const fromEpoch = isoDateToEpochDay(fromDate);
    const upcoming = items.filter((j) => j.dateEpochDay >= fromEpoch).sort((a, b) => a.dateEpochDay - b.dateEpochDay || a.startMinute - b.startMinute);
    const byDay = new Map<number, PlannedJobRequest[]>();
    for (const job of upcoming) {
      if (!byDay.has(job.dateEpochDay)) byDay.set(job.dateEpochDay, []);
      byDay.get(job.dateEpochDay)!.push(job);
    }
    return [...byDay.entries()];
  }, [items, fromDate]);

  return (
    <div>
      <PageHeader title="Planejamento" subtitle="Jobs agendados por dia" action={<Button onClick={() => setEditing("new")}>+ Novo job</Button>} />

      <div className="mb-4 flex items-center gap-2">
        <span className="text-sm text-slate-500">A partir de</span>
        <Input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} className="w-40" />
      </div>

      {loading && <Spinner className="h-6 w-6 text-violet-600" />}
      {error && <p className="text-sm text-red-600">{error}</p>}
      {!loading && grouped.length === 0 && <EmptyState title="Nada planejado" description="Agende os próximos jobs para organizar a semana." />}

      <div className="space-y-4">
        {grouped.map(([dateEpochDay, jobs]) => (
          <Card key={dateEpochDay} className="p-4">
            <h3 className="mb-2 text-sm font-semibold text-slate-700">{epochDayToLabel(dateEpochDay, { weekday: "short", day: "2-digit", month: "2-digit", year: "numeric" })}</h3>
            <ul className="divide-y divide-slate-100">
              {jobs.map((job) => (
                <li key={job.id} className="flex items-center justify-between py-2 text-sm">
                  <div>
                    <p className="font-medium text-slate-800">
                      {minuteOfDayToTimeInput(job.startMinute)}
                      {job.endMinute != null ? `–${minuteOfDayToTimeInput(job.endMinute)}` : ""} · {job.clientName || "Sem cliente"}
                    </p>
                    <p className="text-xs text-slate-400">
                      {[job.siteLabel, job.jobTypeLabel].filter(Boolean).join(" · ")}
                      {job.assignedUserId && membersById.get(job.assignedUserId) && ` · ${membersById.get(job.assignedUserId)!.displayName || membersById.get(job.assignedUserId)!.email}`}
                    </p>
                    {job.notes && <p className="text-xs text-slate-400">{job.notes}</p>}
                  </div>
                  <div>
                    <button onClick={() => setEditing(job)} className="mr-3 text-violet-600 hover:underline">
                      Editar
                    </button>
                    <button onClick={() => setDeleting(job)} className="text-red-600 hover:underline">
                      Excluir
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          </Card>
        ))}
      </div>

      {editing && (
        <PlannedJobFormModal
          initial={editing === "new" ? null : editing}
          clients={clients}
          sites={sites}
          jobTypes={jobTypes}
          members={isOwner ? members : []}
          onClose={() => setEditing(null)}
          onSave={async (req) => {
            if (editing === "new") await create(req);
            else await update(req.id, req);
            setEditing(null);
          }}
        />
      )}

      {deleting && (
        <ConfirmDialog
          title="Excluir job"
          message="Tem certeza que deseja excluir este job planejado?"
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

function PlannedJobFormModal({
  initial,
  clients,
  sites,
  jobTypes,
  members,
  onClose,
  onSave,
}: {
  initial: PlannedJobRequest | null;
  clients: ClientRequest[];
  sites: SiteRequest[];
  jobTypes: JobTypeRequest[];
  members: MemberDto[];
  onClose: () => void;
  onSave: (req: PlannedJobRequest) => Promise<void>;
}) {
  const [date, setDate] = useState(initial ? epochDayToIsoDate(initial.dateEpochDay) : epochDayToIsoDate(todayEpochDay()));
  const [startTime, setStartTime] = useState(initial ? minuteOfDayToTimeInput(initial.startMinute) : "08:00");
  const [endTime, setEndTime] = useState(initial?.endMinute != null ? minuteOfDayToTimeInput(initial.endMinute) : "");
  const [clientName, setClientName] = useState(initial?.clientName ?? "");
  const [siteLabel, setSiteLabel] = useState(initial?.siteLabel ?? "");
  const [jobTypeLabel, setJobTypeLabel] = useState(initial?.jobTypeLabel ?? "");
  const [assignedUserId, setAssignedUserId] = useState(initial?.assignedUserId ?? "");
  const [notes, setNotes] = useState(initial?.notes ?? "");
  const [saving, setSaving] = useState(false);

  return (
    <Modal title={initial ? "Editar job" : "Novo job"} onClose={onClose}>
      <form
        className="space-y-3"
        onSubmit={async (e) => {
          e.preventDefault();
          setSaving(true);
          await onSave({
            id: initial?.id ?? crypto.randomUUID(),
            dateEpochDay: isoDateToEpochDay(date),
            startMinute: timeInputToMinuteOfDay(startTime),
            endMinute: endTime ? timeInputToMinuteOfDay(endTime) : null,
            clientName: clientName || null,
            siteLabel: siteLabel || null,
            jobTypeLabel: jobTypeLabel || null,
            notes: notes.trim() || null,
            assignedUserId: assignedUserId || null,
          });
          setSaving(false);
        }}
      >
        <Field label="Data">
          <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Início">
            <Input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} required />
          </Field>
          <Field label="Fim" hint="opcional">
            <Input type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
          </Field>
        </div>
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
        {members.length > 0 && (
          <Field label="Atribuir a">
            <Select value={assignedUserId} onChange={(e) => setAssignedUserId(e.target.value)}>
              <option value="">Eu mesmo</option>
              {members.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.displayName || m.email}
                </option>
              ))}
            </Select>
          </Field>
        )}
        <Field label="Observações">
          <Textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" disabled={saving}>
            {saving && <Spinner className="h-4 w-4" />}
            Salvar
          </Button>
        </div>
      </form>
    </Modal>
  );
}
