import { useCallback, useEffect, useState } from "react";
import { ApiError } from "./backendApi";

interface CrudApi<T> {
  list: () => Promise<T[]>;
  create: (item: T) => Promise<void>;
  update: (id: string, item: T) => Promise<void>;
  remove?: (id: string) => Promise<void>;
}

/** Generic list+CRUD state for the entity screens (Clients/Sites/Job types/...). */
export function useCrud<T extends { id: string }>(api: CrudApi<T>) {
  const [items, setItems] = useState<T[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await api.list());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Não foi possível carregar os dados.");
    } finally {
      setLoading(false);
    }
  }, [api]);

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const create = useCallback(
    async (item: T) => {
      await api.create(item);
      await reload();
    },
    [api, reload],
  );

  const update = useCallback(
    async (id: string, item: T) => {
      await api.update(id, item);
      await reload();
    },
    [api, reload],
  );

  const remove = useCallback(
    async (id: string) => {
      if (!api.remove) return;
      await api.remove(id);
      await reload();
    },
    [api, reload],
  );

  return { items, loading, error, reload, create, update, remove };
}
