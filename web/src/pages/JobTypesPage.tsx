import { useState } from "react";
import { backendApi } from "../lib/backendApi";
import { useAuth } from "../context/AuthContext";
import { useCrud } from "../lib/useCrud";
import type { JobTypeRequest } from "../lib/types";
import { Button, Card, ConfirmDialog, EmptyState, Field, Input, Modal, PageHeader, Spinner } from "../components/ui";

const api = {
  list: backendApi.listJobTypes,
  create: backendApi.createJobType,
  update: backendApi.updateJobType,
  remove: backendApi.deleteJobType,
};

export function JobTypesPage() {
  const { state } = useAuth();
  const canManage = state.status === "loggedIn" && state.activeRole === "OWNER";
  const { items, loading, error, create, update, remove } = useCrud<JobTypeRequest>(api);
  const [editing, setEditing] = useState<JobTypeRequest | "new" | null>(null);
  const [deleting, setDeleting] = useState<JobTypeRequest | null>(null);

  return (
    <div>
      <PageHeader title="Serviços" subtitle="Tipos de serviço prestado" action={canManage && <Button onClick={() => setEditing("new")}>+ Novo serviço</Button>} />

      {loading && <Spinner className="h-6 w-6 text-violet-600" />}
      {error && <p className="text-sm text-red-600">{error}</p>}
      {!loading && items.length === 0 && <EmptyState title="Nenhum serviço cadastrado" description="Cadastre os tipos de serviço para usá-los nas sessões." />}

      {items.length > 0 && (
        <Card>
          <ul className="divide-y divide-slate-100">
            {items.map((j) => (
              <li key={j.id} className="flex items-center justify-between px-4 py-3">
                <span className="font-medium text-slate-800">{j.name}</span>
                {canManage && (
                  <div>
                    <button onClick={() => setEditing(j)} className="mr-3 text-sm text-violet-600 hover:underline">
                      Editar
                    </button>
                    <button onClick={() => setDeleting(j)} className="text-sm text-red-600 hover:underline">
                      Excluir
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        </Card>
      )}

      {editing && (
        <Modal title={editing === "new" ? "Novo serviço" : "Editar serviço"} onClose={() => setEditing(null)}>
          <JobTypeForm
            initial={editing === "new" ? null : editing}
            onCancel={() => setEditing(null)}
            onSave={async (req) => {
              if (editing === "new") await create(req);
              else await update(req.id, req);
              setEditing(null);
            }}
          />
        </Modal>
      )}

      {deleting && (
        <ConfirmDialog
          title="Excluir serviço"
          message={`Tem certeza que deseja excluir "${deleting.name}"?`}
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

function JobTypeForm({ initial, onCancel, onSave }: { initial: JobTypeRequest | null; onCancel: () => void; onSave: (req: JobTypeRequest) => Promise<void> }) {
  const [name, setName] = useState(initial?.name ?? "");
  const [saving, setSaving] = useState(false);

  return (
    <form
      className="space-y-3"
      onSubmit={async (e) => {
        e.preventDefault();
        setSaving(true);
        await onSave({ id: initial?.id ?? crypto.randomUUID(), name: name.trim(), createdAtMillis: initial?.createdAtMillis ?? Date.now() });
        setSaving(false);
      }}
    >
      <Field label="Nome do serviço">
        <Input value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
      </Field>
      <div className="flex justify-end gap-2 pt-2">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" disabled={saving}>
          {saving && <Spinner className="h-4 w-4" />}
          Salvar
        </Button>
      </div>
    </form>
  );
}
