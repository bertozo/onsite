import { useEffect, useState, type ReactNode } from "react";
import { backendApi, ApiError } from "../lib/backendApi";
import { useAuth } from "../context/AuthContext";
import type { ClientRequest, ConnectionDto, ConnectionInviteDto, ConnectionSessionDto, InviteDto, MemberDto } from "../lib/types";
import { loadProfile, saveProfile, syncProfile, type BusinessProfile } from "../lib/profileStore";
import { hasErrors, profileErrors } from "../lib/validators";
import { loadReportColumns, saveReportColumns } from "../lib/columnPrefs";
import { ColumnPicker } from "../components/ColumnPicker";
import { epochDayToIsoDate, formatDateTime, isoDateToEpochDay, timeInputToMinuteOfDay, todayEpochDay } from "../lib/format";
import { log, logAndFallback } from "../lib/log";
import { Badge, Button, Card, ErrorBanner, Field, InfoBanner, Input, Modal, PageHeader, Select, Spinner } from "../components/ui";

export function SettingsPage() {
  const { state } = useAuth();
  if (state.status !== "loggedIn") return null;
  const isOwner = state.activeRole === "OWNER";

  return (
    <div className="space-y-6">
      <PageHeader title="Equipe e conta" subtitle={`Conectado como ${state.email}`} />
      <BusinessProfileCard />
      <ReportColumnsCard />
      <ReceivedInvitesCard />
      {isOwner && <TeamCard accountId={state.activeAccountId} />}
      <MyConnectionsCard />
      <ReceivedConnectionInvitesCard />
      {isOwner && <EmployerConnectionsCard accountId={state.activeAccountId} />}
    </div>
  );
}

function SectionCard({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <Card className="p-5">
      <h2 className="text-sm font-semibold text-slate-800">{title}</h2>
      {subtitle && <p className="mb-3 mt-0.5 text-xs text-slate-500">{subtitle}</p>}
      <div className={subtitle ? "" : "mt-3"}>{children}</div>
    </Card>
  );
}

function BusinessProfileCard() {
  const [profile, setProfile] = useState<BusinessProfile>(loadProfile());
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    syncProfile().then(setProfile);
  }, []);
  const errors = profileErrors(profile);
  const canSave = profile.name.trim() !== "" && !hasErrors(errors);

  return (
    <SectionCard title="Minha empresa" subtitle="Aparece no cabeçalho das faturas, aqui e no celular">
      <div className="grid grid-cols-2 gap-3">
        <Field label="Nome da empresa">
          <Input value={profile.name} onChange={(e) => setProfile({ ...profile, name: e.target.value })} />
        </Field>
        <Field label="Cargo">
          <Input value={profile.role} onChange={(e) => setProfile({ ...profile, role: e.target.value })} />
        </Field>
        <Field label="ABN" error={errors.abn}>
          <Input value={profile.abn} onChange={(e) => setProfile({ ...profile, abn: e.target.value })} invalid={!!errors.abn} />
        </Field>
        <Field label="Telefone" error={errors.phone}>
          <Input value={profile.phone} onChange={(e) => setProfile({ ...profile, phone: e.target.value })} invalid={!!errors.phone} />
        </Field>
        <Field label="E-mail" error={errors.email}>
          <Input value={profile.email} onChange={(e) => setProfile({ ...profile, email: e.target.value })} invalid={!!errors.email} />
        </Field>
        <Field label="BSB" error={errors.bsb}>
          <Input value={profile.bankBsb} onChange={(e) => setProfile({ ...profile, bankBsb: e.target.value })} invalid={!!errors.bsb} />
        </Field>
        <Field label="Número da conta" error={errors.accountNumber}>
          <Input value={profile.bankAccount} onChange={(e) => setProfile({ ...profile, bankAccount: e.target.value })} invalid={!!errors.accountNumber} />
        </Field>
      </div>
      <div className="mt-3 flex items-center gap-3">
        <Button
          disabled={!canSave}
          onClick={() => {
            saveProfile(profile).then((winner) => {
              setProfile(winner);
              setSaved(true);
              setTimeout(() => setSaved(false), 2000);
            });
          }}
        >
          Salvar
        </Button>
        {saved && <span className="text-sm text-emerald-600">Salvo</span>}
      </div>
    </SectionCard>
  );
}

function ReportColumnsCard() {
  const [columns, setColumns] = useState(loadReportColumns());
  return (
    <SectionCard title="Colunas dos relatórios e faturas" subtitle="Usadas no export CSV e na tabela impressa das faturas. Data e Horas aparecem sempre">
      <ColumnPicker
        selected={columns}
        onChange={(next) => {
          setColumns(next);
          saveReportColumns(next);
        }}
      />
    </SectionCard>
  );
}

function ReceivedInvitesCard() {
  const { refreshMe } = useAuth();
  const [invites, setInvites] = useState<InviteDto[] | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const reload = () =>
    backendApi
      .listMyInvites()
      .then(setInvites)
      .catch((e) => {
        log.warn("loading my invites failed, showing none", e);
        setInvites([]);
      });
  useEffect(() => {
    reload();
  }, []);

  if (!invites || invites.length === 0) return null;

  return (
    <SectionCard title="Convites de equipe recebidos" subtitle="Contas que te convidaram para trabalhar nelas">
      <ul className="space-y-2">
        {invites.map((inv) => (
          <li key={inv.id} className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-sm">
            <span>
              {inv.accountName} <Badge>{inv.role === "OWNER" ? "dono" : "trabalhador"}</Badge>
            </span>
            <Button
              variant="secondary"
              disabled={busyId === inv.id}
              onClick={async () => {
                setBusyId(inv.id);
                await backendApi.acceptInvite(inv.id);
                await refreshMe();
                await reload();
                setBusyId(null);
              }}
            >
              Aceitar
            </Button>
          </li>
        ))}
      </ul>
    </SectionCard>
  );
}

function TeamCard({ accountId }: { accountId: string }) {
  const [members, setMembers] = useState<MemberDto[]>([]);
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState<{ kind: "ok" | "err"; text: string } | null>(null);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    backendApi.listAccountMembers().then(setMembers).catch(logAndFallback("loading account members", undefined));
  }, [accountId]);

  return (
    <SectionCard title="Equipe" subtitle="Convide trabalhadores para esta conta">
      {members.length > 0 && (
        <ul className="mb-3 space-y-1 text-sm text-slate-600">
          {members.map((m) => (
            <li key={m.userId} className="flex items-center justify-between">
              <span>{m.displayName || m.email}</span>
              <Badge tone={m.role === "OWNER" ? "violet" : "default"}>{m.role === "OWNER" ? "dono" : "trabalhador"}</Badge>
            </li>
          ))}
        </ul>
      )}
      <form
        className="flex gap-2"
        onSubmit={async (e) => {
          e.preventDefault();
          setSending(true);
          setMessage(null);
          try {
            await backendApi.createInvite(accountId, email.trim());
            setMessage({ kind: "ok", text: "Convite enviado." });
            setEmail("");
          } catch {
            setMessage({ kind: "err", text: "Não foi possível enviar o convite." });
          } finally {
            setSending(false);
          }
        }}
      >
        <Input type="email" placeholder="email@trabalhador.com" value={email} onChange={(e) => setEmail(e.target.value)} required className="flex-1" />
        <Button type="submit" disabled={sending}>
          {sending && <Spinner className="h-4 w-4" />}
          Convidar
        </Button>
      </form>
      {message && (message.kind === "ok" ? <InfoBanner message={message.text} /> : <ErrorBanner message={message.text} />)}
    </SectionCard>
  );
}

function MyConnectionsCard() {
  const [connections, setConnections] = useState<ConnectionDto[]>([]);
  useEffect(() => {
    backendApi.listMyConnections().then(setConnections).catch(logAndFallback("loading my connections", undefined));
  }, []);
  if (connections.length === 0) return null;
  return (
    <SectionCard title="Clientes conectados" subtitle="Contas que agendam jobs diretamente na sua agenda">
      <ul className="space-y-1 text-sm text-slate-600">
        {connections.map((c) => (
          <li key={c.id} className="flex items-center justify-between">
            <span>
              {c.employerAccountName} → {c.workerClientName}
            </span>
            <Badge tone={c.status === "ACTIVE" ? "green" : "default"}>{c.status}</Badge>
          </li>
        ))}
      </ul>
    </SectionCard>
  );
}

function ReceivedConnectionInvitesCard() {
  const [invites, setInvites] = useState<ConnectionInviteDto[]>([]);
  const [clients, setClients] = useState<ClientRequest[]>([]);
  const [picking, setPicking] = useState<ConnectionInviteDto | null>(null);
  const [clientId, setClientId] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = () => backendApi.listMyConnectionInvites().then(setInvites).catch(logAndFallback("loading my connection invites", undefined));
  useEffect(() => {
    reload();
    backendApi.listClients().then(setClients).catch(logAndFallback("loading clients", undefined));
  }, []);

  if (invites.length === 0) return null;

  return (
    <SectionCard title="Convites de conexão recebidos" subtitle="Um cliente quer agendar jobs direto na sua agenda">
      <ul className="space-y-2">
        {invites.map((inv) => (
          <li key={inv.id} className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-sm">
            <span>{inv.employerAccountName}</span>
            <Button variant="secondary" onClick={() => setPicking(inv)}>
              Aceitar
            </Button>
          </li>
        ))}
      </ul>

      {picking && (
        <Modal title="Vincular a qual dos seus clientes?" onClose={() => setPicking(null)}>
          <p className="mb-3 text-sm text-slate-500">Escolha qual cadastro de cliente representa {picking.employerAccountName}.</p>
          <Select value={clientId} onChange={(e) => setClientId(e.target.value)} className="mb-3">
            <option value="">Selecione...</option>
            {clients.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
          {error && <ErrorBanner message={error} />}
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setPicking(null)}>
              Cancelar
            </Button>
            <Button
              disabled={!clientId || busy}
              onClick={async () => {
                setBusy(true);
                setError(null);
                try {
                  await backendApi.acceptConnectionInvite(picking.id, clientId);
                  setPicking(null);
                  await reload();
                } catch {
                  setError("Não foi possível aceitar o convite.");
                } finally {
                  setBusy(false);
                }
              }}
            >
              Confirmar
            </Button>
          </div>
        </Modal>
      )}
    </SectionCard>
  );
}

function EmployerConnectionsCard({ accountId }: { accountId: string }) {
  const [connections, setConnections] = useState<ConnectionDto[]>([]);
  const [email, setEmail] = useState("");
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<{ kind: "ok" | "err"; text: string } | null>(null);
  const [detail, setDetail] = useState<ConnectionDto | null>(null);
  const [revoking, setRevoking] = useState<ConnectionDto | null>(null);

  const reload = () => backendApi.listEmployerConnections().then(setConnections).catch(logAndFallback("loading employer connections", undefined));
  useEffect(() => {
    reload();
  }, [accountId]);

  return (
    <SectionCard title="Prestadores conectados a mim" subtitle="Agende jobs e veja as horas de subcontratados, sem que eles entrem na sua conta">
      <form
        className="mb-3 flex gap-2"
        onSubmit={async (e) => {
          e.preventDefault();
          setSending(true);
          setMessage(null);
          try {
            await backendApi.createConnectionInvite(accountId, email.trim());
            setMessage({ kind: "ok", text: "Convite enviado." });
            setEmail("");
          } catch {
            setMessage({ kind: "err", text: "Não foi possível enviar o convite." });
          } finally {
            setSending(false);
          }
        }}
      >
        <Input type="email" placeholder="email@prestador.com" value={email} onChange={(e) => setEmail(e.target.value)} required className="flex-1" />
        <Button type="submit" disabled={sending}>
          {sending && <Spinner className="h-4 w-4" />}
          Convidar
        </Button>
      </form>
      {message && (message.kind === "ok" ? <InfoBanner message={message.text} /> : <ErrorBanner message={message.text} />)}

      {connections.length > 0 && (
        <ul className="mt-3 space-y-2">
          {connections.map((c) => (
            <li key={c.id} className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-sm">
              <span>
                {c.workerClientName} <Badge tone={c.status === "ACTIVE" ? "green" : "default"}>{c.status}</Badge>
              </span>
              <div className="flex gap-3">
                <button onClick={() => setDetail(c)} className="text-violet-600 hover:underline">
                  Agenda e horas
                </button>
                <button onClick={() => setRevoking(c)} className="text-red-600 hover:underline">
                  Revogar
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {detail && <ConnectionDetailModal connection={detail} onClose={() => setDetail(null)} />}

      {revoking && (
        <Modal title="Revogar conexão" onClose={() => setRevoking(null)}>
          <p className="mb-4 text-sm text-slate-600">Tem certeza que deseja revogar a conexão com {revoking.workerClientName}?</p>
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setRevoking(null)}>
              Cancelar
            </Button>
            <Button
              variant="danger"
              onClick={async () => {
                await backendApi.revokeConnection(revoking.id);
                setRevoking(null);
                await reload();
              }}
            >
              Revogar
            </Button>
          </div>
        </Modal>
      )}
    </SectionCard>
  );
}

function ConnectionDetailModal({ connection, onClose }: { connection: ConnectionDto; onClose: () => void }) {
  const [sessions, setSessions] = useState<ConnectionSessionDto[] | null>(null);
  const [date, setDate] = useState(epochDayToIsoDate(todayEpochDay()));
  const [startTime, setStartTime] = useState("08:00");
  const [siteLabel, setSiteLabel] = useState("");
  const [jobTypeLabel, setJobTypeLabel] = useState("");
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);

  useEffect(() => {
    backendApi.listConnectionSessions(connection.id).then(setSessions).catch((e) => {
      log.warn("loading connection sessions failed, showing none", e);
      setSessions([]);
    });
  }, [connection.id]);

  return (
    <Modal title={`${connection.workerClientName}`} onClose={onClose} wide>
      <h3 className="mb-2 text-sm font-semibold text-slate-700">Horas lançadas</h3>
      {!sessions ? (
        <Spinner className="h-5 w-5 text-violet-600" />
      ) : sessions.length === 0 ? (
        <p className="mb-4 text-sm text-slate-400">Nenhuma sessão lançada ainda.</p>
      ) : (
        <div className="mb-4 max-h-40 overflow-y-auto rounded-lg border border-slate-200">
          <table className="w-full text-left text-xs">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-3 py-2">Início</th>
                <th className="px-3 py-2">Fim</th>
                <th className="px-3 py-2">Local</th>
                <th className="px-3 py-2">Serviço</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sessions.map((s) => (
                <tr key={s.id}>
                  <td className="px-3 py-1.5">{formatDateTime(s.startTimestampMillis)}</td>
                  <td className="px-3 py-1.5">{s.stopTimestampMillis ? formatDateTime(s.stopTimestampMillis) : "em aberto"}</td>
                  <td className="px-3 py-1.5">{s.siteLabel || "—"}</td>
                  <td className="px-3 py-1.5">{s.jobTypeLabel || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h3 className="mb-2 text-sm font-semibold text-slate-700">Agendar job na agenda do prestador</h3>
      <form
        className="space-y-3"
        onSubmit={async (e) => {
          e.preventDefault();
          setSaving(true);
          setError(null);
          setOk(false);
          try {
            await backendApi.createConnectionPlannedJob(connection.id, {
              id: crypto.randomUUID(),
              dateEpochDay: isoDateToEpochDay(date),
              startMinute: timeInputToMinuteOfDay(startTime),
              siteLabel: siteLabel || null,
              jobTypeLabel: jobTypeLabel || null,
              notes: notes.trim() || null,
            });
            setOk(true);
            setNotes("");
          } catch (e) {
            setError(e instanceof ApiError ? e.message : "Não foi possível agendar o job.");
          } finally {
            setSaving(false);
          }
        }}
      >
        <div className="grid grid-cols-2 gap-3">
          <Field label="Data">
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
          </Field>
          <Field label="Início">
            <Input type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} required />
          </Field>
          <Field label="Local (opcional)">
            <Input value={siteLabel} onChange={(e) => setSiteLabel(e.target.value)} />
          </Field>
          <Field label="Serviço (opcional)">
            <Input value={jobTypeLabel} onChange={(e) => setJobTypeLabel(e.target.value)} />
          </Field>
        </div>
        <Field label="Observações">
          <Input value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
        {error && <ErrorBanner message={error} />}
        {ok && <InfoBanner message="Job agendado na agenda do prestador." />}
        <div className="flex justify-end">
          <Button type="submit" disabled={saving}>
            {saving && <Spinner className="h-4 w-4" />}
            Agendar
          </Button>
        </div>
      </form>
    </Modal>
  );
}
