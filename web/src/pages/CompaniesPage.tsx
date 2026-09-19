import { useState } from "react";
import { backendApi } from "../lib/backendApi";
import { useAuth } from "../context/AuthContext";
import { useCrud } from "../lib/useCrud";
import type { CompanyRequest } from "../lib/types";
import { Button, Card, ConfirmDialog, EmptyState, Field, Input, Modal, PageHeader, Spinner } from "../components/ui";

const api = {
  list: backendApi.listCompanies,
  create: backendApi.createCompany,
  update: backendApi.updateCompany,
  remove: backendApi.deleteCompany,
};

export function CompaniesPage() {
  const { state } = useAuth();
  const canManage = state.status === "loggedIn" && state.activeRole === "OWNER";
  const { items, loading, error, create, update, remove } = useCrud<CompanyRequest>(api);
  const [editing, setEditing] = useState<CompanyRequest | "new" | null>(null);
  const [deleting, setDeleting] = useState<CompanyRequest | null>(null);

  return (
    <div>
      <PageHeader
        title="Clientes"
        subtitle="Empresas para quem você trabalha"
        action={canManage && <Button onClick={() => setEditing("new")}>+ Novo cliente</Button>}
      />

      {loading && <Spinner className="h-6 w-6 text-violet-600" />}
      {error && <p className="text-sm text-red-600">{error}</p>}

      {!loading && items.length === 0 && (
        <EmptyState title="Nenhum cliente cadastrado" description="Cadastre seus clientes para associá-los às sessões e faturas." />
      )}

      {items.length > 0 && (
        <Card>
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-400">
              <tr>
                <th className="px-4 py-3">Nome</th>
                <th className="px-4 py-3">ABN</th>
                <th className="px-4 py-3">Contato</th>
                <th className="px-4 py-3">Valor/h padrão</th>
                {canManage && <th className="px-4 py-3" />}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((c) => (
                <tr key={c.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium text-slate-800">{c.name}</td>
                  <td className="px-4 py-3 text-slate-500">{c.abn || "—"}</td>
                  <td className="px-4 py-3 text-slate-500">{[c.phone, c.email].filter(Boolean).join(" · ") || "—"}</td>
                  <td className="px-4 py-3 text-slate-500">{c.hourlyRate != null ? `$${c.hourlyRate.toFixed(2)}` : "—"}</td>
                  {canManage && (
                    <td className="px-4 py-3 text-right">
                      <button onClick={() => setEditing(c)} className="mr-3 text-violet-600 hover:underline">
                        Editar
                      </button>
                      <button onClick={() => setDeleting(c)} className="text-red-600 hover:underline">
                        Excluir
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {editing && (
        <CompanyFormModal
          initial={editing === "new" ? null : editing}
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
          title="Excluir cliente"
          message={`Tem certeza que deseja excluir "${deleting.name}"? Sessões e faturas antigas continuam referenciando o nome, mas o cadastro será removido.`}
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

function CompanyFormModal({
  initial,
  onClose,
  onSave,
}: {
  initial: CompanyRequest | null;
  onClose: () => void;
  onSave: (req: CompanyRequest) => Promise<void>;
}) {
  const [name, setName] = useState(initial?.name ?? "");
  const [abn, setAbn] = useState(initial?.abn ?? "");
  const [phone, setPhone] = useState(initial?.phone ?? "");
  const [email, setEmail] = useState(initial?.email ?? "");
  const [hourlyRate, setHourlyRate] = useState(initial?.hourlyRate?.toString() ?? "");
  const [saving, setSaving] = useState(false);

  return (
    <Modal title={initial ? "Editar cliente" : "Novo cliente"} onClose={onClose}>
      <form
        className="space-y-3"
        onSubmit={async (e) => {
          e.preventDefault();
          setSaving(true);
          await onSave({
            id: initial?.id ?? crypto.randomUUID(),
            name: name.trim(),
            abn: abn.trim() || null,
            phone: phone.trim() || null,
            email: email.trim() || null,
            hourlyRate: hourlyRate ? Number(hourlyRate) : null,
            createdAtMillis: initial?.createdAtMillis ?? Date.now(),
          });
          setSaving(false);
        }}
      >
        <Field label="Nome">
          <Input value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        </Field>
        <Field label="ABN">
          <Input value={abn} onChange={(e) => setAbn(e.target.value)} />
        </Field>
        <Field label="Telefone">
          <Input value={phone} onChange={(e) => setPhone(e.target.value)} />
        </Field>
        <Field label="E-mail">
          <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Valor/h padrão (AUD)">
          <Input type="number" step="0.01" min="0" value={hourlyRate} onChange={(e) => setHourlyRate(e.target.value)} />
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
