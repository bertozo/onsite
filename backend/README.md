# onsite-backend

Backend for OnSite (Android/iOS/Web). Kotlin + Ktor monolith, Postgres, auth delegated to
Supabase Auth (this service never sees a password, only verifies the JWT Supabase issues).

## Architecture

See `../on-site-app` for the client. This service is a modular monolith: one deployable,
packages per domain (`identity/`, and future `domain/` for companies/sites/sessions/etc.).
Every table carries `id`, `account_id` (where applicable), `created_at`, `updated_at`, and
will carry `deleted_at` once Phase 1 sync support lands.

Identity model: `accounts` (a personal workspace or a team workspace - both created the same
way, there is no separate "business" signup flow) + `users` (mirrors a Supabase Auth user) +
`memberships` (`user_id` + `account_id` + `role`, `OWNER` or `WORKER`). A user gets a
personal `OWNER` account automatically on first login. An `OWNER` invites a `WORKER` into
their account by email (`invites` table, `POST /v1/accounts/{id}/invites`); the invited user
sees it under `GET /v1/me/invites` and accepts it with `POST /v1/me/invites/{id}/accept`,
which is what actually creates their `WORKER` membership. Every domain write route checks
the caller's role in the *active* account (`X-Account-Id` header, see `AccountContext.kt`) -
a `WORKER` can read the shared catalog (companies/sites/job types/invoices) but not create,
edit or delete it; sessions and planned jobs carry `created_by_user_id` (and planned jobs an
`assigned_user_id`) so a `WORKER`'s own list is scoped to their rows while an `OWNER` sees
everyone's.

A separate bridge (`connections/`) covers the more common Australian trades case: a
subcontractor keeps their own account and invoicing, but links one of their own `Company`
rows (their record of a client) to that client's real account, if the client also uses the
app - without ever becoming a member of it. The client sends a `connection_invites` row by
email; the worker accepts it and picks *which* of their own companies it maps to
(`POST /v1/me/connection-invites/{id}/accept`, body `{companyId}`) - the client never sees
the worker's company list, only the name after linking. Once active, the client can drop a
`PlannedJob` onto the worker's own calendar (`POST /v1/connections/{id}/planned-jobs`,
pre-assigned to the worker, company name resolved server-side) and read that one company's
sessions (`GET /v1/connections/{id}/sessions` - no rate, no other clients' hours). Either
side can revoke (`DELETE /v1/connections/{id}`).

## Local development

> To run the backend itself in a container (alongside Postgres and the web app), see the
> root `../docker-compose.yml` instead - this section is the host-based dev workflow
> (`./gradlew run`, hot classes on rebuild), which `Dockerfile` here does not use.

Requires Docker Desktop running, and the JDK already used by the Android project
(`export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"` on this machine).

```bash
# 1. start local Postgres
docker compose up -d

# 2. copy env defaults (already matches docker-compose.yml)
cp .env.example .env

# 3. run the server (loads .env, applies Flyway migrations on boot)
export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"
set -a; source .env; set +a
./gradlew run
```

There is no real Supabase project yet, so there is nothing to sign up against. Two ways to
get a token in the meantime, both signed with `SUPABASE_JWT_SECRET` and shaped exactly like
a Supabase one:

- `tools/make_dev_jwt.py demo@example.com dev-only-secret-change-me` - offline, prints
  ready-to-run `curl` commands for `POST /v1/me/bootstrap` (creates the personal account on
  first call, idempotent after that) and `GET /v1/me`.
- `POST /v1/dev/auth/login` with `{"email": "demo@example.com"}` - a real HTTP endpoint the
  Android app's login screen calls today, gated by `DEV_AUTH_ENABLED=true`. No password: it
  mints a session for any email. **Never enable this outside a local dev machine.**

### Testing from a physical Android device

A real device on the same USB cable as this machine reaches the backend through
`adb reverse tcp:8080 tcp:8080`, which makes the device's own `localhost:8080` forward to
this machine's `localhost:8080`. The app is already configured to call
`http://127.0.0.1:8080` and allows cleartext traffic to that address only
(`res/xml/network_security_config.xml` in `on-site-app`).

## Switching to a real Supabase project

Modern Supabase projects sign access tokens with an asymmetric ES256 key, not a shared
secret - the backend verifies them against the project's public JWKS endpoint instead of a
configured secret, so there's no key to copy into `.env`.

1. Create the project in the Supabase dashboard (this is an account-creation step only you
   can do).
2. Settings → API has the Project URL; put it in `.env` as `SUPABASE_PROJECT_URL`. The
   backend fetches `<that URL>/auth/v1/.well-known/jwks.json` once at startup and verifies
   tokens against the public key(s) published there (`auth/SupabaseJwks.kt`).
3. Point the Android/iOS/Web clients at that project's URL and publishable (`sb_publishable_...`)
   key for sign-up/sign-in; they call Supabase directly for auth and only send this backend
   the resulting JWT.
4. `SUPABASE_JWT_SECRET`/`SUPABASE_JWT_ISSUER` are unrelated to the real project - they only
   sign/verify the dev-only login's tokens (`auth/DevAuthRoutes.kt`), which stays available
   side-by-side for local testing as long as `DEV_AUTH_ENABLED=true`.
