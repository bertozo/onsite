# onsite-web

Management web app for OnSite (React + TypeScript + Vite + Tailwind CSS 4). A thin client
over `onsite-backend`'s REST API — no offline storage or GPS/timer tracking (that stays
exclusive to the Android app); sessions here are logged manually. Meant for desktop use by
an account owner: clients, sites, services, sessions, reports, invoices, planning and team
management.

## Architecture

- `src/lib/supabaseAuth.ts` — talks directly to Supabase Auth's REST API (GoTrue), same
  project and publishable key as the Android client
  (`android/app/src/main/java/com/xbertz/onsite/backend/SupabaseAuth.kt`). No Supabase SDK
  dependency. Adds refresh-token support (`refreshSession`) that the Android client doesn't
  have, since a browser tab session tends to live much longer than a phone session.
- `src/lib/backendApi.ts` — REST client for `onsite-backend`, mirroring
  `BackendApi.kt`'s endpoints one for one. Every call is authenticated with a Supabase access
  token (refreshed proactively when close to expiry) and, once an active account is chosen,
  an `X-Account-Id` header.
- `src/lib/sessionStore.ts` — the signed-in session in `localStorage` (there is no
  browser equivalent of Android's `EncryptedSharedPreferences`).
- `src/lib/profileStore.ts` — the "from" details printed on generated invoices
  (business name/ABN/bank details). Kept in `localStorage` only, per browser, exactly like
  Android's `Profile` table: neither is ever synced to the backend (there is no
  `/v1/me/profile` route).
- `src/context/AuthContext.tsx` — the sign-in/sign-up/password-reset state machine, mirroring
  Android's `AuthViewModel` minus the local-sync steps (this app has no offline cache to push
  or wipe, so switching accounts is just changing which `X-Account-Id` is sent).
- `src/lib/reportLogic.ts` / `src/lib/invoiceLogic.ts` — ported from the Android client's
  `report/ReportSummary.kt` and `invoice/InvoiceData.kt` (period presets, weekly/labeled
  totals, CSV export, day+rate invoice line grouping) so the numbers match between clients.
- `src/pages/` — one page per nav item (Dashboard, Sessions, Planning, Reports, Invoices,
  Clients, Sites, Job types, Settings). Clients/Sites/Job types share `lib/useCrud.ts`,
  a small list+CRUD hook.
- Invoices have no generated PDF binary (Android renders one natively via `PdfDocument`);
  instead `InvoiceDetailModal` renders a printable view (`#invoice-print`, see the
  `@media print` rules in `index.css`) and `window.print()` covers "save as PDF".

## Local development

> To run the web app in a container (alongside the backend and Postgres), see the root
> `../docker-compose.yml` instead - this section is the host-based dev workflow (`npm run
> dev`, hot-reload), which `Dockerfile` here does not use.

Needs `onsite-backend` running locally (see `../backend/README.md`):

```bash
cd ../backend
docker compose up -d
cp .env.example .env   # then set SUPABASE_PROJECT_URL to the real project if you want
                        # full Supabase sign-up/sign-in to work end to end, not just dev login
export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"
set -a; source .env; set +a
./gradlew run
```

Then:

```bash
cp .env.example .env
npm install
npm run dev
```

`VITE_ENABLE_DEV_AUTH=true` (the `.env.example` default) shows a "dev login" shortcut on the
login screen that calls the backend's `POST /v1/dev/auth/login` (email only, no password) —
useful for local testing without a confirmed Supabase account. Never enable it against a
real deployment.

## Known gaps (first pass)

- No column picker for the invoice/report table (Android lets you choose which columns
  print); this always prints the full set.
- No per-line rate override in the invoice review step; adjust a session's own rate on the
  Sessions screen before generating instead.
- No CI-facing test suite yet (`npm run build` type-checks and bundles; there's no unit or
  e2e coverage).
