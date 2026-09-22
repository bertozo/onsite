# Logging

The rule this whole setup exists to enforce: **a failure leaves a trace.** Before this, a
rejected request, a failed sync and a dropped session were all completely silent - the only
log statement in the backend was the 500 handler, and the Android app had exactly one
`Log.e` call. Most of what follows is about making the quiet paths audible, not about
formatting.

## The request id

One id follows a call across all three codebases:

1. The Android client (`BackendApi.kt`) and the web client (`lib/backendApi.ts`) mint a
   `X-Request-Id` per call and send it.
2. The backend takes it if it looks sane, mints one otherwise (`plugins/Logging.kt`), puts
   it in the MDC as `requestId`, echoes it back on the response header and includes it in
   every error body from `plugins/StatusPages.kt`.
3. Both clients log the id next to their own failure line, preferring the echoed one.

So a user complaint becomes: ask for the id (or find their line in the shared Android log
file), then `grep` it in the backend log and read the whole call from both sides.

## Levels

The same four everywhere, with the same meaning:

| Level | Means | Example |
| --- | --- | --- |
| `DEBUG` | Detail only useful while debugging. Off in release builds (Android) and production (web); `LOG_LEVEL=DEBUG` turns it on in the backend. | each sync step and its duration |
| `INFO` | A thing happened that shapes everything after it. | startup config, sign-in, account switch, one line per request, one line per sync |
| `WARN` | Something failed and the app carried on - **most of this app's failures**. | a rejected request (4xx), a swallowed load, a blocked notification |
| `ERROR` | This service or app is broken; someone has to look. | unhandled exception in a route, a photo that could not be saved |

`ERROR` is deliberately rare. A 403 is not an error, it is the backend working; if every
refusal were an `ERROR` then nothing would be.

## Shape of a line

Key=value after a short human phrase, so a line reads without a parser and greps without a
regex:

```
2026-09-22 22:02:10.194 INFO  [my-own-id-123] ktor.application - GET /v1/clients status=200 durationMs=53 userId=484dbf1c-… accountId=d48cf1d5-…
2026-09-22 22:02:10.291 WARN  [ea9d5119-…] c.x.o.backend.http - rejected status=400 reason=X-Account-Id must be a UUID
```

One event per line. No multi-line messages except a stack trace, which belongs to the
throwable and not to the message text.

## Never in a log line

Access tokens or refresh tokens, passwords, the JWT itself, ABN, BSB, bank account numbers,
and a user's email address. Ids are the substitute: `userId`, `accountId`, and the remote
row id are enough to find anything, and none of them is sensitive on their own.

Two guardrails, because a rule nobody enforces is a rule nobody follows:

- `AppLog` (Android) rewrites anything JWT-shaped to `<token>` before it reaches the file
  that users share by email.
- `StatusPages` takes a separate `logReason` where the response body has to name something
  the log should not (the "email already registered" conflict is the one case today).

## Backend (Ktor)

- `plugins/Logging.kt` installs `CallId` + `CallLogging`: one line per request with method,
  path, status, duration, and - once the call authenticated and resolved an account -
  `userId` and `accountId`. Those two come from call attributes rather than the MDC, since
  the routes hand their work to `Dispatchers.IO` and an MDC value would not survive the
  thread hop back.
- `plugins/StatusPages.kt` logs every error response: mapped 4xx at `WARN`, anything
  unmapped at `ERROR` with a stack trace.
- `logback.xml` reads two environment variables:
  - `LOG_LEVEL` (default `INFO`) - any logback level. `DEBUG` also surfaces Ktor and
    Hikari internals, which is why `io.netty` and `com.zaxxer.hikari` are pinned to `INFO`.
  - `LOG_FORMAT` (default `console`) - `console` for a terminal, `json` for one JSON object
    per line via `logstash-logback-encoder`, with `requestId` as a field. Use `json`
    anywhere logs are collected; the id stops being a substring to regex out.

  Both are read straight from the environment there (logback is configured before
  `AppConfig` exists) and mirrored into `AppConfig` only so the startup line can state what
  is in effect. Set them in a root `.env` (see `.env.example`), which `docker-compose.yml`
  passes through.

## Android

- `log/AppLog.kt` writes to logcat **and** to a rotating file in `filesDir/logs`
  (two files, 192 KB each). Nobody using this app is attached to adb, so the file is the
  only way a failure on a roof last Tuesday ever reaches us.
- **Settings -> Diagnostics -> Share app logs** flattens those files into
  `cacheDir/logs/onsite-log.txt`, headed by app version and device, and hands it to the
  share sheet.
- `Result.logFailure(tag, action)` is the house style for a call whose failure must not stop
  the caller: it keeps the `runCatching { … }.getOrDefault(…)` shape and leaves a trace.
  A bare `runCatching` with no `logFailure` in a review is the thing to object to.
  Cancellation is not logged - `runCatching` catches it too, and a screen going away is
  not a failure.
- `BackendApi` sets `expectSuccess = true`, so a non-2xx raises and is logged by the
  client's `HttpResponseValidator` with its status and request id. Logging alone was not
  enough here: the calls that parse no response body used to treat a 403 as success.
- `SyncEngine` logs one line per run - `sync done durationMs=812 client[up=2 down=12] …` -
  plus which step failed when one does. Counts are per entity type because "nothing moved"
  and "nothing moved for planned jobs" are different bugs.

## Web

- `lib/log.ts` is a level-aware console wrapper. `debug` is dev-only; `warn` and `error`
  stay on in production, since they are what a user can screenshot.
- `lib/backendApi.ts` logs every non-2xx and every unreachable-backend failure with the
  request id, and parses the backend's `{error, requestId}` body so `ApiError.message` is
  the message rather than raw JSON and `ApiError.requestId` carries the id.
- `logAndFallback(what, fallback)` replaces the `.catch(() => undefined)` pattern on the
  screens: same degraded UI, but the console says which data is missing and why.

## Health and metrics

Logs answer "what happened in this one call". Two other endpoints answer the questions logs
answer badly, and neither needs a monitoring stack to be worth having:

- `GET /health` - liveness. The process is up; it touches nothing else, so a database
  outage never gets the app restarted (restarting it would not bring Postgres back).
- `GET /health/ready` - readiness. Actually asks the database, because "backend up,
  Postgres unreachable" is this app's real failure mode and it answers `ok` to any check
  that doesn't look. `docker-compose.yml` polls this one, and `web` waits for it.
- `GET /metrics` - Prometheus text format, **only registered when `METRICS_TOKEN` is set**
  (a scrape endpoint hands out every route name and its traffic volume), and then only to
  a caller sending `Authorization: Bearer <token>`. Carries request rate/latency per route
  template, JVM/GC, and `hikaricp_*`. The pool gauges are the reason it exists: routes run
  blocking transactions against a pool of 10, so `hikaricp_connections_pending` climbs
  minutes before anything reaches a log, where it only shows up once requests already
  spent 30s timing out.

All three are kept out of the request log - a probe every ten seconds and a scrape every
fifteen would bury everything about actual users. A failing readiness check logs itself.

**Metric labels are not log fields.** Never tag a metric with `userId`, `accountId` or
`requestId`: each distinct value is a new time series, and that is how a Prometheus falls
over. Metrics get the route template (`/v1/clients/{id}`), ids stay in the log line.

Nothing scrapes any of this yet. When there is a real deployment, point whatever it offers
(or Grafana Cloud's free tier) at `/metrics`, and start with three alerts rather than a
dashboard: 5xx rate, `hikaricp_connections_pending` sustained above zero, and readiness
down. `service`/`env`/`version` are already on every JSON log line and every metric, so
both sides can be filtered to one deployment of one build.

## Adding a log line

Ask what question the line answers six weeks from now, at 7am, with only the log. If there
isn't one, don't add it - and if a code path can fail without answering one, that is the
line worth adding.
