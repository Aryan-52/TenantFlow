# TenantFlow operations guide

How to run, configure, observe and back up TenantFlow. The application is a modular
monolith: one Spring Boot backend, one static React frontend served by nginx, PostgreSQL
(source of truth), Redis (rate limits and short-lived caches only) and S3-compatible object
storage (attachments).

## Ports

| What | Local dev (no Docker) | Docker Compose (host) | Container |
|---|---|---|---|
| Frontend | 5173 (Vite) | 127.0.0.1:3000 | 8080 (unprivileged nginx) |
| Backend API | 8095 | 127.0.0.1:8081 | 8080 |
| Backend management (metrics, detailed health) | 8091 | not published | 8091 |
| PostgreSQL | 5432 | 127.0.0.1:5432 | 5432 |
| Redis | 6379 | 127.0.0.1:6379 | 6379 |
| MinIO API / console | 9000 / 9001 | 127.0.0.1:9000 / 9001 | 9000 / 9001 |
| Prometheus / Grafana (`observability` profile) | | 127.0.0.1:9090 / 3001 | |

Every published port is bound to 127.0.0.1. Never publish the management port: it serves
Prometheus metrics and detailed health. Load balancers use the status-only `/livez` and
`/readyz` on the API port.

## Local development

One frontend and one backend, always on the same ports:

| Service | Address | Started by |
|---|---|---|
| Frontend (Vite) | http://localhost:5173 | `cd frontend && npm run dev` (launch entry `frontend`) |
| Backend (Spring Boot, profile `local`) | http://localhost:8095 | `scripts/dev-backend.ps1` (launch entry `backend`) |
| PostgreSQL | localhost:5432, database `tenantflow` | `docker compose up -d postgres` |

Both servers refuse to start on another port: Vite has `strictPort`, and the backend script
stops with a message naming the process that holds 8095 or 8091 (and says whether it is
already a TenantFlow backend). Before starting Maven the script also checks Java 21, that
PostgreSQL answers, and that `TENANTFLOW_JWT_SECRET` is set and long enough (length only;
the value is never printed). A failed start-up ends with a non-zero exit code.

**Time zone.** Windows "India Standard Time" makes Java default to the legacy name
`Asia/Calcutta`, which the bundled PostgreSQL rejects ("invalid value for parameter
TimeZone"). The backend replaces that legacy default with `Asia/Kolkata` at start-up
(`TenantflowBackendApplication`), so it connects however it is launched; the datasource also
asks for an `Asia/Kolkata` session. Docker keeps its own default (UTC). The frontend reaches the backend through `VITE_API_URL`
in `frontend/.env.development`.

**One-time setup: secrets.** Copy `.env.example` to `.env` at the repository root (the file is
git-ignored; Docker Compose reads the same file). Set at least:

```
TENANTFLOW_JWT_SECRET=<output of: openssl rand -base64 48>
```

Write values without quotes. The `local` profile imports this file
(`application-local.properties`); a real environment variable with the same name wins over
it. Without the secret the backend stops at start-up with
`TENANTFLOW_JWT_SECRET is not set ...` (the value itself is never printed). Google sign-in
is optional: leave `GOOGLE_OAUTH_CLIENT_ID` / `_SECRET` empty and the button explains that
it is not configured.

**Start (Windows):**

```powershell
docker compose up -d postgres
powershell -NoProfile -File scripts\dev-backend.ps1     # http://localhost:8095
cd frontend; npm ci; npm run dev                          # http://localhost:5173 (second terminal)
```

In the Claude desktop app the same two commands are the `backend` and `frontend` entries
of `.claude/launch.json`. The `local` profile also turns off the Secure cookie flag (plain
HTTP), uses an in-process cache and rate limiter (no Redis needed), points password-reset
and Google sign-in redirects at port 5173, and enables the API documentation.

Attachments are stored on the local disk by default when the backend runs outside
Docker: `~/.tenantflow/storage` (`%USERPROFILE%\.tenantflow\storage` on Windows), which
survives restarts and needs no extra service. Override the folder with
`TENANTFLOW_STORAGE_DIR`. To use MinIO locally instead, start it with
`docker compose up -d minio` and run the backend with `TENANTFLOW_STORAGE_TYPE=s3
TENANTFLOW_STORAGE_ACCESS_KEY=tenantflow-local TENANTFLOW_STORAGE_SECRET_KEY=tenantflow-local-secret`.

| `TENANTFLOW_STORAGE_TYPE` | Where attachment bytes live |
|---|---|
| `filesystem` (default) | A local folder (`TENANTFLOW_STORAGE_DIR`). One backend instance, or a shared volume |
| `s3` (compose and `prod` default) | An S3-compatible bucket (AWS S3, MinIO) |
| `memory` | Tests only: bytes are lost on restart |

Files uploaded while an earlier build used in-memory storage have database records but
no bytes. They now report "This file's contents are no longer available in storage";
delete them and upload them again.

The API documentation is at http://localhost:8095/swagger-ui.html (JSON at `/v3/api-docs`)
only while `TENANTFLOW_API_DOCS_ENABLED=true`. It is off by default, and the `prod` profile
always forces it off.

## Full stack with Docker Compose

```bash
cp .env.example .env          # then fill in at least TENANTFLOW_JWT_SECRET
docker compose config --quiet # validate
docker compose up -d --build
docker compose ps             # backend and frontend report (healthy)
docker compose --profile observability up -d   # optional Prometheus + Grafana
```

Open http://localhost:3000. The backend runs with `SPRING_PROFILES_ACTIVE=prod`
(see `application-prod.properties`), as a non-root user (uid 10001), and shuts down
gracefully: on `docker compose stop` it stops accepting requests and finishes in-flight
ones within 20 s (the compose grace period is 30 s).

Start-up order: PostgreSQL must be healthy before the backend starts. The backend starts
without Redis (rate limiting falls back to per-instance memory and caching to the
database) and without object storage (attachment endpoints return 503 until it is
reachable). The frontend waits for a healthy backend.

## Configuration

All secrets come from environment variables; nothing secret is committed. The full list,
with defaults, is in `services/tenantflow-backend/src/main/resources/application.properties`.

| Variable | Required in production | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | yes | JSON logs, no SQL logging, API docs off, pool sizing |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | yes | PostgreSQL 17 (use `sslmode=require` when the network is untrusted) |
| `TENANTFLOW_JWT_SECRET` | yes | HMAC key, at least 32 bytes: `openssl rand -base64 48`. Rotating it signs everyone out |
| `TENANTFLOW_COOKIE_SECURE` | keep `true` (default) | Refresh-token cookie is HTTPS-only; `false` only for plain-HTTP localhost |
| `TENANTFLOW_CORS_ALLOWED_ORIGINS` | yes, if the browser calls the API cross-origin | Comma-separated origins |
| `TENANTFLOW_PASSWORD_RESET_URL` | yes | Frontend `/reset-password?token={token}` URL |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_SSL` | recommended | Shared rate limits across instances and the tenant stats cache |
| `TENANTFLOW_STORAGE_TYPE` | `s3` under `prod` (default) | `filesystem` (local default), `s3`, or `memory` (tests only) |
| `TENANTFLOW_STORAGE_DIR` | only for `filesystem` | Folder for attachment bytes (default `~/.tenantflow/storage`) |
| `TENANTFLOW_REMINDERS_ENABLED` / `_INTERVAL` | no (`true` / `PT15M`) | In-app due-date reminders and the scheduled "overdue" automation. Idempotent: safe after restarts and on several instances |
| `TENANTFLOW_STORAGE_*` (S3) | yes, for `s3` | Bucket, region, endpoint (empty on AWS), path-style, keys (empty on AWS: use the IAM role) |
| `MANAGEMENT_PORT` | no (8091) | Internal metrics and health port |
| `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` | no (10 / 2) | Keep instances x max size below PostgreSQL `max_connections` |
| `GOOGLE_OAUTH_CLIENT_ID` / `_SECRET` | no | Enables "Continue with Google" |
| `TENANTFLOW_EMAIL_LOG_RESET_LINKS` | never `true` outside local dev | Logs reset links, which are credentials |

## Local development database safety

Starting the backend runs Flyway, so **every pending migration is applied to the database
the backend connects to** - with the `local` profile that is `localhost:5432/tenantflow`.
Before the first start of a build that brings new migrations, back the database up (see
"Backup and restore" below). The Docker Compose backend (port 8081) uses the same database;
an older image still starts after a newer migration (Flyway ignores "future" migrations),
but rebuild it from the same code so both run against the schema they expect.

To try something risky, point a single run at a throwaway copy instead of changing any
configuration file - the variable applies to that terminal only:

```powershell
docker run -d --name tenantflow-scratch-db -p 127.0.0.1:15432:5432 -e POSTGRES_DB=tenantflow -e POSTGRES_USER=tenantflow -e POSTGRES_PASSWORD=<local-only> postgres:17
# restore a pg_dump into it (see "Backup and restore"), then:
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:15432/tenantflow'; powershell -NoProfile -File scripts\dev-backend.ps1
```

## Health, metrics and logs

* `GET /livez` (process alive) and `GET /readyz` (alive and database reachable) on the API
  port return only `{"status":"UP"}`. Redis and storage outages do not fail readiness,
  because both degrade gracefully.
* `http://<instance>:8091/actuator/prometheus` exposes request rates and latencies,
  JVM, Hikari pool, cache hit/miss (`tenantflow.cache.errors` for Redis failures),
  rate-limit rejections (`tenantflow.ratelimit.rejected`) and in-memory fallbacks
  (`tenantflow.ratelimit.fallback`). Storage failures show up as HTTP 503s. The bundled
  Grafana dashboard ("TenantFlow backend") is provisioned automatically.
* Every response carries `X-Request-Id`, and the same id is in error bodies (`requestId`)
  and every log line (MDC), so a user-reported error can be traced. Under `prod` the
  logs are JSON (ECS) on stdout.
* Passwords, tokens, reset links and secrets are never logged.

## Database migrations

Flyway applies `db/migration/V*__*.sql` automatically on start-up. Hibernate only
*validates* the schema (`ddl-auto=validate`) and never changes it.

* Never edit, rename or delete an applied migration; add a new `V<n+1>__description.sql`.
  CI fails a pull request that modifies an existing migration.
* New migrations are tested on a disposable PostgreSQL 17 via Testcontainers, both from an
  empty database and as an upgrade of a V7 database that already holds data
  (`FlywayPostgresMigrationTests`). Run them locally with
  `./mvnw test -Dtest='FlywayPostgresMigrationTests,PostgresSchemaValidationTests'`.
  They need Docker and never touch a configured database.
* Before upgrading production: back up (below), deploy one instance first (it migrates),
  and check `flyway_schema_history`.

## Backup and restore (PostgreSQL)

PostgreSQL holds all durable state except attachment bytes, which live in object storage.

```bash
# Backup: custom format, compressed, consistent snapshot
docker exec tenantflow-postgres pg_dump -U tenantflow -d tenantflow -Fc -f /tmp/tenantflow.dump
docker cp tenantflow-postgres:/tmp/tenantflow.dump ./tenantflow-$(date +%Y%m%d-%H%M).dump

# Restore into a NEW, empty database (never over a live one), then point the app at it
docker exec tenantflow-postgres createdb -U tenantflow tenantflow_restore
docker cp ./tenantflow-YYYYMMDD-HHMM.dump tenantflow-postgres:/tmp/restore.dump
docker exec tenantflow-postgres pg_restore -U tenantflow -d tenantflow_restore --no-owner /tmp/restore.dump
```

Test restores regularly. On AWS RDS use automated snapshots plus point-in-time recovery.
Back up the attachment bucket with S3 versioning (or MinIO `mc mirror`); with
`filesystem` storage, back up `TENANTFLOW_STORAGE_DIR` together with the database. A database
restore can reference objects that were deleted after the backup, which then download as
404, so keep bucket versioning retention at least as long as database backup retention.

## Production deployment (Vercel + Render + Neon)

The browser only ever talks to the Vercel domain. `frontend/vercel.json` forwards `/api/*`,
`/oauth2/*` and `/login/oauth2/*` to the Render backend and sends every other path to
`index.html` (React Router). Because API and sign-in share the frontend's origin, the
`SameSite=Lax` refresh cookie and Google sign-in work without third-party cookies, and CORS
is not involved.

**Vercel** (project root directory: `frontend`). `vercel.json` sets the framework (Vite),
`npm ci`, the build command `npm run vercel-build` and the output directory `dist`.
`vercel-build` first runs `scripts/check-vercel-config.js`, which stops the deployment
while the backend host in `vercel.json` is still `REPLACE-WITH-YOUR-RENDER-SERVICE`, or if
the rewrites are out of order. Replace it with your Render service host. Leave
`VITE_API_URL` unset (relative URLs).

**Render** (Docker, root directory `services/tenantflow-backend`, health check path `/readyz`):

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PORT` | `8080` (the port the image listens on) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<neon-host>/<db>?sslmode=require` (Neon's direct host, not `-pooler`) |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | Neon role |
| `TENANTFLOW_JWT_SECRET` | new random value, 32+ characters |
| `TENANTFLOW_CORS_ALLOWED_ORIGINS` | `https://<app>.vercel.app` |
| `TENANTFLOW_PASSWORD_RESET_URL` | `https://<app>.vercel.app/reset-password?token={token}` |
| `TENANTFLOW_OAUTH_FRONTEND_REDIRECT_URI` | `https://<app>.vercel.app/oauth-callback` |
| `GOOGLE_OAUTH_REDIRECT_URI` | `https://<app>.vercel.app/login/oauth2/code/google` (with Google sign-in) |
| `GOOGLE_OAUTH_CLIENT_ID` / `_SECRET` | optional; Google sign-in stays off without them |
| `SERVER_TOMCAT_REMOTEIP_TRUSTED_PROXIES` | `.*` - see below |
| `TENANTFLOW_CACHE_TYPE` / `TENANTFLOW_RATE_LIMIT_STORE` | `simple` / `memory` (single instance, no Redis) |
| `TENANTFLOW_STORAGE_BUCKET`, `_ENDPOINT`, `_REGION`, `_ACCESS_KEY`, `_SECRET_KEY` | S3-compatible bucket (R2: region `auto`) |

*Client addresses behind the proxy.* Requests reach Render from Vercel's servers, so without
`SERVER_TOMCAT_REMOTEIP_TRUSTED_PROXIES=.*` every user shares Vercel's address in the per-IP
sign-in limits and one person can block everyone's login
(`ratelimit/ProxiedClientIpIntegrationTests`). With it, the address Vercel reports for the
browser is used. Trade-off: someone calling the `onrender.com` URL directly can choose the
address they report, so per-IP limits can be evaded there. With your own API domain instead
(`VITE_API_URL=https://api.example.com`, frontend on `app.example.com`), drop the proxy
setting and this trade-off disappears.

*Storage.* Under `prod` the backend refuses to start with storage that would lose files or
an incomplete S3 setup, and names the setting to fix (`StorageProperties`). Render's own
disk is wiped on every deploy; `filesystem` is only accepted with a persistent disk and
`TENANTFLOW_STORAGE_FILESYSTEM_PERSISTENT=true`.

## Production deployment notes (AWS example)

* **Compute:** run the backend image on ECS Fargate (or EKS), at least 2 tasks behind an
  ALB. Health check `/readyz` on port 8080. Keep 8091 reachable only from Prometheus.
  Serve the frontend image, or `frontend/dist` from S3 + CloudFront, on the same origin as
  `/api` (or set `TENANTFLOW_CORS_ALLOWED_ORIGINS`).
* **TLS:** terminate at the ALB. The app honours `X-Forwarded-*`
  (`server.forward-headers-strategy=native`). Keep `TENANTFLOW_COOKIE_SECURE=true`.
* **Database:** RDS PostgreSQL 17, encrypted, Multi-AZ, credentials from Secrets Manager
  injected as environment variables.
* **Redis:** ElastiCache (Redis/Valkey) with TLS and AUTH (`REDIS_SSL=true`,
  `REDIS_PASSWORD`). With several instances, rate limits are only global while Redis is up.
* **Storage:** a private S3 bucket (Block Public Access on, SSE enabled). Leave
  `TENANTFLOW_STORAGE_ENDPOINT` and the keys empty, set `TENANTFLOW_STORAGE_PATH_STYLE=false`,
  and grant the task role `s3:PutObject`, `s3:GetObject` and `s3:DeleteObject` on that
  bucket only. Downloads always go through the backend's authorization check.
* **Not for production:** the bundled MinIO image (`bitnamilegacy/minio`, frozen), the
  compose JWT and Grafana placeholder defaults, and `TENANTFLOW_COOKIE_SECURE=false`.

## Continuous integration

`.github/workflows/ci.yml` runs on pushes to `main` and on pull requests:

* backend `./mvnw verify` (all suites, including the Testcontainers ones)
* migration checks
* frontend lint, tests and build
* dependency review (pull requests)
* Trivy scans of the source tree and both images (HIGH/CRITICAL with a fix fail the build)

Actions are pinned to commit SHAs and the workflow only has `contents: read`. Accepted
scanner findings go in `.trivyignore`, each with a reason.
