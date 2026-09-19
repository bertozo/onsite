import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const NAV_ITEMS = [
  { to: "/", label: "Painel", end: true },
  { to: "/sessions", label: "Sessões" },
  { to: "/planning", label: "Planejamento" },
  { to: "/reports", label: "Relatórios" },
  { to: "/invoices", label: "Faturas" },
  { to: "/companies", label: "Clientes" },
  { to: "/sites", label: "Locais" },
  { to: "/job-types", label: "Serviços" },
  { to: "/settings", label: "Equipe e conta" },
];

export function Layout() {
  const { state, switchAccount, logout } = useAuth();
  if (state.status !== "loggedIn") return null;

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-60 shrink-0 flex-col border-r border-slate-200 bg-white">
        <div className="flex items-center gap-2 px-5 py-5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-violet-600 text-sm font-bold text-white">OS</div>
          <span className="text-lg font-semibold text-slate-900">OnSite</span>
        </div>
        <nav className="flex-1 space-y-0.5 px-3">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `block rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive ? "bg-violet-50 text-violet-700" : "text-slate-600 hover:bg-slate-100"
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-slate-200 p-3 text-xs text-slate-400">v0.1 · gestão web</div>
      </aside>

      <div className="flex min-h-screen flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
          <div className="flex items-center gap-2">
            {state.memberships.length > 1 ? (
              <select
                value={state.activeAccountId}
                onChange={(e) => switchAccount(e.target.value)}
                className="rounded-lg border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-700"
              >
                {state.memberships.map((m) => (
                  <option key={m.accountId} value={m.accountId}>
                    {m.accountName} ({m.role === "OWNER" ? "dono" : "trabalhador"})
                  </option>
                ))}
              </select>
            ) : (
              <span className="text-sm font-medium text-slate-700">{state.activeAccountName}</span>
            )}
            {state.activeRole === "WORKER" && (
              <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">acesso de trabalhador</span>
            )}
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-slate-500">{state.email}</span>
            <button onClick={logout} className="rounded-lg px-2.5 py-1.5 text-sm font-medium text-slate-500 hover:bg-slate-100">
              Sair
            </button>
          </div>
        </header>
        <main className="flex-1 overflow-y-auto p-6">
          {/* Remounts the active page on account switch: every page fetches its own data
              once on mount, so this is what makes them refetch against the new X-Account-Id. */}
          <Outlet key={state.activeAccountId} />
        </main>
      </div>
    </div>
  );
}
