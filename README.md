# onsite

Monorepo for the OnSite project.

- `android/` — Android app (Kotlin, Jetpack Compose)
- `backend/` — API server (Kotlin, Ktor)
- `ios/` — iOS app (planned)
- `web/` — Web app (React, TypeScript, Vite) — management/desktop client, no offline storage or GPS tracking
- `docs/` — architecture diagrams, and `logging.md` (log levels, the shared request id, what never goes in a log line)

## Running the full stack locally (Docker)

```bash
docker compose up --build
```

Builds and starts Postgres, the backend and the web app together:

- Web: http://localhost:5173
- Backend: http://localhost:8080

This is a prod-like run (no hot-reload — rebuild with `--build` after code changes), meant
for trying the whole system end to end rather than day-to-day development. It can't run
alongside the per-service dev setups in `backend/README.md` (`backend/docker-compose.yml`)
or `web/README.md`, since both bind the same host ports (5432/8080/5173) — stop one before
starting the other. Override the Supabase project, the dev-login toggle, or the backend URL
baked into the web build via a root `.env` file (see `.env.example`).
