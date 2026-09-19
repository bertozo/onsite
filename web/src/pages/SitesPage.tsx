import { useState } from "react";
import { backendApi } from "../lib/backendApi";
import { useAuth } from "../context/AuthContext";
import { useCrud } from "../lib/useCrud";
import type { SiteRequest } from "../lib/types";
import { Button, Card, ConfirmDialog, EmptyState, Field, Input, Modal, PageHeader, Spinner } from "../components/ui";

const api = {
  list: backendApi.listSites,
  create: backendApi.createSite,
  update: backendApi.updateSite,
  remove: backendApi.deleteSite,
};

export function SitesPage() {
  const { state } = useAuth();
  const canManage = state.status === "loggedIn" && state.activeRole === "OWNER";
  const { items, loading, error, create, update, remove } = useCrud<SiteRequest>(api);
  const [editing, setEditing] = useState<SiteRequest | "new" | null>(null);
  const [deleting, setDeleting] = useState<SiteRequest | null>(null);

  return (
    <div>
      <PageHeader
        title="Locais"
        subtitle="Endereços onde os serviços são realizados"
        action={canManage && <Button onClick={() => setEditing("new")}>+ Novo local</Button>}
      />

      {loading && <Spinner className="h-6 w-6 text-violet-600" />}
      {error && <p className="text-sm text-red-600">{error}</p>}
      {!loading && items.length === 0 && <EmptyState title="Nenhum local cadastrado" description="Cadastre os locais de trabalho para usá-los nas sessões." />}

      {items.length > 0 && (
        <Card>
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-400">
              <tr>
                <th className="px-4 py-3">Nome</th>
                <th className="px-4 py-3">Endereço</th>
                <th className="px-4 py-3">Coordenadas</th>
                {canManage && <th className="px-4 py-3" />}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((s) => (
                <tr key={s.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-medium text-slate-800">{s.label}</td>
                  <td className="px-4 py-3 text-slate-500">{s.address || "—"}</td>
                  <td className="px-4 py-3 text-slate-500">
                    {s.latitude !== 0 || s.longitude !== 0 ? `${s.latitude.toFixed(5)}, ${s.longitude.toFixed(5)}` : "—"}
                  </td>
                  {canManage && (
                    <td className="px-4 py-3 text-right">
                      <button onClick={() => setEditing(s)} className="mr-3 text-violet-600 hover:underline">
                        Editar
                      </button>
                      <button onClick={() => setDeleting(s)} className="text-red-600 hover:underline">
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
        <SiteFormModal
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
          title="Excluir local"
          message={`Tem certeza que deseja excluir "${deleting.label}"? Sessões antigas mantêm o nome do local, mas perdem o endereço nos relatórios.`}
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

function SiteFormModal({ initial, onClose, onSave }: { initial: SiteRequest | null; onClose: () => void; onSave: (req: SiteRequest) => Promise<void> }) {
  const [label, setLabel] = useState(initial?.label ?? "");
  const [address, setAddress] = useState(initial?.address ?? "");
  const [latitude, setLatitude] = useState(initial?.latitude?.toString() ?? "0");
  const [longitude, setLongitude] = useState(initial?.longitude?.toString() ?? "0");
  const [saving, setSaving] = useState(false);

  return (
    <Modal title={initial ? "Editar local" : "Novo local"} onClose={onClose}>
      <form
        className="space-y-3"
        onSubmit={async (e) => {
          e.preventDefault();
          setSaving(true);
          await onSave({
            id: initial?.id ?? crypto.randomUUID(),
            label: label.trim(),
            address: address.trim() || null,
            latitude: Number(latitude) || 0,
            longitude: Number(longitude) || 0,
            createdAtMillis: initial?.createdAtMillis ?? Date.now(),
          });
          setSaving(false);
        }}
      >
        <Field label="Nome do local">
          <Input value={label} onChange={(e) => setLabel(e.target.value)} required autoFocus />
        </Field>
        <Field label="Endereço">
          <Input value={address} onChange={(e) => setAddress(e.target.value)} />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Latitude" hint="opcional">
            <Input type="number" step="any" value={latitude} onChange={(e) => setLatitude(e.target.value)} />
          </Field>
          <Field label="Longitude" hint="opcional">
            <Input type="number" step="any" value={longitude} onChange={(e) => setLongitude(e.target.value)} />
          </Field>
        </div>
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
