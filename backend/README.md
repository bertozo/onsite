# onsite-backend

Backend for OnSite (Android/iOS/Web). Kotlin + Ktor monolith, Postgres, auth delegated to
Supabase Auth (this service never sees a password, only verifies the JWT Supabase issues).

## Architecture

See `../on-site-app` for the client. This service is a modular monolith: one deployable,
packages per domain (`identity/`, and future `domain/` for companies/sites/sessions/etc.).
Every table carries `id`, `account_id` (where applicable), `created_at`, `updated_at`, and
will carry `deleted_at` once Phase 1 sync support lands.

Identity model: `accounts` (a personal workspace or a business workspace) + `users` (mirrors
a Supabase Auth user) + `memberships` (`user_id` + `account_id` + `role`, `OWNER` or
`WORKER`). A user gets a personal `OWNER` account automatically on first login.

## Local development

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

1. Create the project in the Supabase dashboard (this is an account-creation step only you
   can do).
2. Settings → API → JWT Settings has the JWT secret; put it in `.env` as
   `SUPABASE_JWT_SECRET`, replacing the dev value.
3. Point the Android/iOS/Web clients at that project's URL and anon key for sign-up/sign-in;
   they call Supabase directly for auth and only send this backend the resulting JWT.
4. `SUPABASE_JWT_ISSUER` stays `supabase` unless Supabase changes that default.
