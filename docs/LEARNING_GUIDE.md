# Learning guide: how TenantFlow works

A map of the real code, in the order you would follow a request. Paths are relative to the
repository root. Backend Java lives under
`services/tenantflow-backend/src/main/java/io/github/aryan52/tenantflow/` (shortened to
`…/tenantflow/` below); the frontend lives under `frontend/src/`.

## 1. Folders and what they own

**Backend** (Spring Boot 4, Java 21) — one package per feature, each with a
controller (HTTP), a service (rules) and repositories (database):

| Package | Responsibility |
|---|---|
| `auth/`, `refreshtoken/`, `passwordreset/`, `oauth/`, `security/` | Sign-in, tokens, the security filter, membership checks |
| `tenant/` | Workspaces (tenants), the dashboard |
| `membership/` | Members and roles |
| `project/`, `task/` | Projects, tasks, comments, My Work |
| `planning/` | Milestones, dependencies, recurrence, calendar, portfolio, workload |
| `attachment/`, `storage/` | Files: validation, metadata, where the bytes live |
| `activity/`, `notification/`, `preferences/` | History, in-app notifications, reminders |
| `analytics/`, `report/`, `search/`, `automation/` | Read-only insight, CSV export, search, rules |
| `entity/`, `repository/` | Shared JPA entities and repositories (users, tenants, projects, tasks) |
| `error/` | `GlobalExceptionHandler`: every error becomes the same JSON shape |
| `config/` | `SecurityConfig`, CORS, scheduling, typed `*Properties` |
| `src/main/resources/db/migration/` | Flyway SQL migrations `V1__…` to `V16__…` |

**Frontend** (React 19, TypeScript, Vite):

| Folder | Responsibility |
|---|---|
| `api/` | One file per backend area (`work.ts`, `planning.ts`, `attachments.ts`…), all using `api/client.ts` |
| `contexts/` | App-wide state: `AuthContext` (who is signed in), `TenantContext` (current workspace), theme, toasts |
| `pages/` | One component per route (`App.tsx` lists the routes) |
| `components/` | Reusable pieces: `ui/` (buttons, modal…), `tasks/`, `planning/`, `attachments/`, `layout/` (shell, sign-in) |
| `hooks/` | Small reusable logic, e.g. `useLoad` (load data, cancel stale requests) |
| `styles/` | CSS. `tokens.css` defines every colour/size for light and dark; other files only use those tokens |

## 2. From a click to a controller

Example: changing a task's status in My Work.

1. `components/tasks/TaskRow.tsx` calls `taskApi.updateStatus(...)` from `api/work.ts`.
2. `api/work.ts` calls `client.patch('/api/tenants/{t}/projects/{p}/tasks/{id}/status', { status })`.
3. `api/client.ts` (an axios instance) adds `Authorization: Bearer <token>`; on a `401` it
   silently refreshes the token once and retries.
4. On the server, `security/JwtAuthenticationFilter` validates the token and puts an
   `AuthenticatedUser` into Spring Security's context.
5. Spring routes the request to `task/TaskController#updateTaskStatus`. Its
   `@PreAuthorize("@tenantSecurity.hasAccess(#tenantId)")` runs first (see section 6).

## 3. Controller → service → repository

`TaskController#updateTaskStatus` only translates HTTP into a method call:

```java
return taskService.updateTaskStatus(tenantId, projectId, taskId, request);
```

`task/TaskService#updateTaskStatus` holds the rules, inside one `@Transactional` method:
find the task *within this workspace and project*, refuse to complete it while
`DependencyService#requireUnblocked` finds unfinished blockers, save it, then record history
(`TaskActivity`), create the next occurrence of a recurring task, and run automations.
Persistence is `repository/TaskRepository` — a Spring Data interface; method names such as
`findByIdAndProject_Id` become SQL automatically, and `@Query` methods hold the rest.

Rule of thumb in this codebase: controllers have no logic, services have no HTTP types,
repositories have no rules.

## 4. Entities vs DTOs

* **Entity** (`entity/Task.java`): a JPA class mapped to the `tasks` table. It has relations
  (`Project`, `User`), lifecycle hooks (`@PrePersist` sets timestamps and `completedAt`) and
  an optimistic-locking `@Version`. Entities never leave the server.
* **DTO** (`task/dto/TaskResponse`, `CreateTaskRequest`, `UpdateTaskRequest`, `TaskPlanning`):
  Java `record`s that define the JSON contract. Requests carry validation annotations
  (`@NotBlank`, `@Size`) that run because controllers use `@Valid`. `TaskResponse.from(task)`
  converts an entity to the API shape, so internal fields (such as storage keys) never leak.

## 5. Authentication

* `auth/AuthService` checks the password with BCrypt (`config/PasswordEncoderConfig`) and
  returns a short-lived JWT from `security/JwtService`.
* A long-lived refresh token (`refreshtoken/RefreshTokenService`) is set as an HttpOnly cookie
  by `RefreshCookieUtil`. `POST /api/auth/refresh` rotates it (`RefreshTokenService#rotate`):
  each use replaces it, and presenting an already-used token again revokes all of that
  user's sessions and writes an audit event (theft detection). Only a hash of each token is stored.
* Each JWT carries the user's `tokenVersion`; `JwtAuthenticationFilter` rejects tokens whose
  version no longer matches, so changing the password signs out every session.
* `config/SecurityConfig` makes the API stateless, leaves only sign-in endpoints public and
  requires authentication for everything else under `/api/**`.

## 6. Authorization and tenant (workspace) isolation

Two layers, both on the server:

1. **Membership** — `security/TenantSecurity#hasAccess(tenantId)` / `#hasRole(...)` are called
   from `@PreAuthorize` on every workspace endpoint. Not a member → `403`.
2. **Scoped lookups** — services never load a record by id alone. They use queries such as
   `ProjectRepository#findByIdAndTenant_Id` or `TaskRepository#findByIdAndProject_Tenant_Id`.
   An id from another workspace therefore behaves like an id that does not exist → `404`,
   revealing nothing.

Role checks for destructive actions (deleting tasks, milestones) use `hasRole(..., 'ADMIN',
'OWNER')`; owner-or-uploader rules live in the service (`AttachmentService#requireUploaderOrAdmin`).
Cross-workspace tests: `planning/PlanningIntegrationTests`, `planning/CollaborationIntegrationTests`,
`attachment/FilesystemAttachmentIntegrationTests`.

## 7. Flyway migrations

Every schema change is a new file in `src/main/resources/db/migration/` named
`V<number>__<description>.sql`. On start-up Flyway compares those files with the
`flyway_schema_history` table and runs the new ones, in order, once.

* Never edit a migration that has run anywhere — add a new one (e.g. `V16__planning_collaboration_automation.sql` only adds tables and nullable columns).
* Starting the backend **migrates whatever database it is connected to**. See
  `docs/OPERATIONS.md` → "Local development database safety".
* Tests: `migration/FlywayPostgresMigrationTests` runs all migrations on a throwaway
  PostgreSQL (Testcontainers), and `PostgresSchemaValidationTests` checks the entities match.

## 8. Files: upload and retrieval

* **Upload** — `attachment/AttachmentController#upload` → `AttachmentService#upload` →
  `validateAndStore(...)`: size limit, safe file name, real type detected from the first bytes
  (`FileTypePolicy`), then the bytes stream into storage while their SHA-256 is computed.
  Metadata (name, type, size, hash, **storage key**) goes into the `attachments` table.
  The key is `tenants/<tenant>/projects/<project>/<attachmentId>`.
* **Storage** — `storage/ObjectStorage` has three implementations chosen by
  `tenantflow.storage.type` in `storage/StorageConfig`: `FilesystemObjectStorage` (local
  default, folder from `TENANTFLOW_STORAGE_DIR`, absolute, independent of the working
  directory), `S3ObjectStorage` (Docker/production, MinIO or AWS S3), `InMemoryObjectStorage`
  (tests). The filesystem version validates every key and refuses paths outside its root.
* **Download/preview** — `AttachmentController#download` streams the bytes with the stored
  content type, always as an attachment with `nosniff`. The frontend
  (`components/attachments/FilePreview.tsx`) fetches them through the API into TenantFlow's
  own viewer: PDFs are drawn page by page with PDF.js (`components/viewer/`, loaded only when
  a PDF is opened), images from a local `blob:` URL, text as plain text.
* **Missing bytes** — if the record exists but storage has no bytes, storage throws
  `StoredObjectMissingException` → `404` with a clear message. The record and history are
  kept; the uploader or an admin can put the original back with
  `PUT …/attachments/{id}/content` (`AttachmentService#restoreContent`), which refuses to
  overwrite existing bytes and records whether the content is identical (same SHA-256).

## 9. How the frontend talks to the backend

* All requests go through `api/client.ts`. In development `VITE_API_URL`
  (`frontend/.env.development`) points at `http://localhost:8095`; in Docker, nginx proxies
  `/api` to the backend.
* Errors use one JSON shape (`error/ApiErrorResponse`); `apiError(err, fallback)` in
  `api/work.ts` turns any failure into a readable message (and per-field errors for forms).
* Pages load data with `hooks/useLoad.ts`, which aborts the previous request when the
  workspace or filters change — so a slow response for an old workspace can never overwrite
  the current one. `App.tsx` also re-mounts workspace pages on every workspace switch.
* `contexts/AuthContext` restores the session on start-up. The splash is painted by
  `index.html` before any JavaScript loads, and `AppLoading` in `App.tsx` continues the same
  screen while the session is restored; `contexts/TenantContext` loads the user's workspaces
  and remembers the current one.

## 10. Running it safely

```bash
# Backend tests (H2 + Testcontainers; never touch your databases). Needs Docker running.
cd services/tenantflow-backend && ./mvnw test

# Frontend checks
cd frontend && npx tsc -b && npx vitest run && npm run lint && npm run build
```

To run the app locally (Windows; details in `docs/OPERATIONS.md`, "Local development"):
put `TENANTFLOW_JWT_SECRET` in the git-ignored `.env` at the repository root once, then

```powershell
docker compose up -d postgres
powershell -NoProfile -File scripts\dev-backend.ps1   # backend  http://localhost:8095
cd frontend; npm run dev                                # frontend http://localhost:5173
```

The backend runs with the `local` profile (`application-local.properties`), which reads
secrets from `.env`; `config/JwtProperties` stops start-up with a clear message if the JWT
secret is missing or too short. Starting the backend applies pending Flyway migrations to
`localhost:5432/tenantflow` - back it up first when a build brings new ones.

Good next files to read: `task/TaskService.java`, `planning/DependencyService.java`,
`attachment/AttachmentService.java`, `security/TenantSecurity.java`, `frontend/src/App.tsx`,
`frontend/src/pages/MyWork.tsx`.
