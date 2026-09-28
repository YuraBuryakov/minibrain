# CLAUDE.md — how we work on MiniBrain

MiniBrain is a local-first web app that shows **how I become better**: a Skill Map
(game skill tree / Obsidian graph) backed by evidence of demonstrated understanding.

- Product & architecture brief: [`docs/brief.md`](docs/brief.md)
- Progress: [`docs/roadmap.md`](docs/roadmap.md)
- Decisions already made: [`docs/decisions.md`](docs/decisions.md)

Read the roadmap and decisions at the start of each session.

## This is a learning project

The development process itself is part of my Java architecture learning.
Claude is a **pair-programmer**, not an autonomous project generator.

Loop for every step:

```text
task → discuss options → understand trade-offs → Claude writes a small piece
→ I review and understand it → I run it → Claude commits → next task
```

For every task Claude:

1. Restates the small goal.
2. Names the important trade-offs; offers 2–3 options when it really matters — **I choose**.
3. Proposes the simplest implementation.
4. Writes only the code required for this slice.
5. Briefly explains unfamiliar concepts.
6. Stops when the slice is done and waits for my review; after my OK Claude commits.
7. At the end of a step updates `docs/roadmap.md`, `docs/decisions.md` and the project `status.md`.

Hard rules:

- Never implement future roadmap steps unasked; never refactor unrelated code.
- Before a new abstraction or interface, explain why it is needed.
  If one simple class solves it, do not create five interfaces.
- No microservices, auth, Docker, Event Sourcing, CQRS frameworks, command buses,
  XP/achievements, spaced repetition — unless I explicitly ask.
- Claude runs `git commit` after I have reviewed and approved the slice. Work happens on a branch (`feat-<topic>`), not directly on `main`.

## Project layout

```text
backend/     Spring Boot (Java 21, Maven, Spring JDBC, SQLite)
frontend/    React + TypeScript + Vite, React Flow, TanStack Query
docs/        brief, roadmap, decisions log
data/        minibrain.db (runtime, not in Git)
exports/     current.json + snapshots/ (portable, may be in Git)
imports/     MINIBRAIN_UPDATE files to import
backups/     local backups (not in Git)
```

## Conventions

- Base package: `dev.minibrain`. Package structure: see "Where new classes go" below.
- Persistence: Spring JDBC (`JdbcClient`), SQL is visible. No JPA for now.
- Schema changes go through migrations (Flyway, added in step 1).
- Skill has DB `id` and stable external `key` (e.g. `ddd.aggregate`). JSON contracts use `key`, never numeric ids.
- Raw JSON (`JsonNode`, `ObjectMapper`) must not leak into the domain. React Flow types must not leak into the backend.
- Knowledge changes create a Revision. Presentation changes (move node, focus, zoom) do not.
- Every durable JSON contract has `schemaVersion`.

## Where new classes go

Modules by feature at the top, thin layers inside (DDD-lite, see `docs/decisions.md`).

```text
backend/src/main/java/dev/minibrain/
├── <module>/                 skill, importing, learning today; revision later (brief §50)
│   ├── domain/               the model: records, enums, value objects, domain rules
│   ├── persistence/          *Repository: JdbcClient + SQL, row mapping (write side + simple reads)
│   ├── query/                read models for screens: *Query + its result records (own SQL)
│   ├── web/                  *Controller + request records (nested in the controller)
│   ├── application/          use-case services spanning several repositories/queries (importing: preview, apply)
│   └── format/               1:1 mirrors of external JSON contracts (importing: MINIBRAIN_UPDATE)
└── shared/web/               HTTP concerns for all modules (ApiExceptionHandler)

backend/src/main/resources/db/migration/   V<n>__<what>.sql, never edit an applied one
backend/src/test/java/dev/minibrain/...    same package as the class under test
```

| New thing | Goes to | Example |
|---|---|---|
| Entity / record of the model, enum, value object | `<module>/domain` | `Skill`, `SkillStatus`, future `SkillKey` |
| Table access (insert, update, find) | `<module>/persistence` | `EvidenceRepository` |
| Data shaped for one screen / API view | `<module>/query` | `KnowledgeGraph`, `KnowledgeGraphQuery` |
| REST endpoint | `<module>/web` | `SkillController` |
| Request body | nested `record` inside its controller | `CreateSkillRequest` |
| Exception → HTTP status mapping | `shared/web` | `ApiExceptionHandler` |
| Use case over several repositories / queries | `<module>/application` | `ImportPreviewer` |
| Record mirroring an incoming JSON file | `<module>/format` | `UpdateDocument` |
| New table or column | new Flyway migration | `V5__add_skill_active.sql` |

Dependency direction: `web → application → persistence / query → domain`. A module may read another module's
`query` / `persistence` (importing reads skill); never the other way round. `domain` depends on nothing in the project
(no Spring, no JDBC, no JSON). `query` never returns `domain` write types just to save a class.

Rules of thumb:

- Start inside an existing layer. Add a new layer (e.g. `application/` for use-case services) only when a
  real need appears, e.g. one operation spans several repositories in a transaction (import Apply, step 13).
- A new top-level module only when a brief §50 module actually starts (`learning`, `importing`, `revision`).
- No interface with a single implementation (see decisions: repositories stay concrete classes).
- Shared helpers stay in the package that uses them (e.g. `TIMESTAMP` in `persistence`); move to `shared/`
  only when a second module needs them.

## Running

**Claude starts and stops the app on my command** (I do not run it by hand):

| I say | Claude runs |
|---|---|
| "запусти" / "start" | `powershell -ExecutionPolicy Bypass -File dev.ps1 start` (backend + frontend, waits until ready, opens browser) |
| "останови" / "stop" | `powershell -ExecutionPolicy Bypass -File dev.ps1 stop` (kills by port, whole process tree) |
| "статус" / "status" | `powershell -ExecutionPolicy Bypass -File dev.ps1 status` |

Importing a `MINIBRAIN_UPDATE`: the **Import** button on the map (paste text or pick a file, preview, apply).
AI prompt for building the initial map: `docs/ai/initial-map-prompt.md` (output is `MINIBRAIN_UPDATE` JSON).
Session prompt (copied by the Skill card): `frontend/src/ai/session-prompt.md`. Permanent rules to paste into
ChatGPT / Claude settings: `docs/ai/chat-custom-instructions.md`.

Logs: `logs/backend.log`, `logs/frontend.log`. `start` uses the real `data/minibrain.db`; for demos with fake
data Claude uses a scratch DB via `MINIBRAIN_DB_URL`, never the real one.

Manual equivalents:

```bash
cd backend
mvn spring-boot:run      # http://localhost:8080
mvn test

cd frontend
npm install              # first time only
npm run dev              # http://localhost:5173, /api is proxied to :8080
npm run build            # typecheck + production build
```

Frontend layout: `src/api.ts` mirrors backend read models (fetch + types); `src/layout.ts` computes positions
(pure, no React Flow); React Flow appears only in `src/toFlow.ts` (adapter) and `src/skillMapParts.tsx`
(custom nodes / edges). Styles: palette tokens in `src/index.css`, map styles in `src/skillMap.css`.
