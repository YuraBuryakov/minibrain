# Decisions log

Short records of decisions and *why*. Newest at the bottom.
Format: date · decision · reason · alternatives considered.

---

### 2026-09-28 · Initial stack

- **Decision:** Java 21 + Spring Boot 3.5 + Maven; Spring JDBC; SQLite; React + TS + Vite later.
- **Why:** local-first, one process, SQL stays visible; the model is small.
- **Alternatives:** JPA (hides persistence boundaries — reconsider later), PostgreSQL from day one (unnecessary for a local app).

### 2026-09-28 · Monorepo layout `backend/` + `frontend/`

- **Decision:** one repository, backend and frontend as sibling folders; runtime data folders (`data/`, `exports/`, `imports/`, `backups/`) at the root.
- **Why:** one product, one history; data folders match the brief (§53).

### 2026-09-28 · Deferred: Flyway, Spring Modulith, frontend

- **Decision:** not added in the skeleton.
- **Why:** "if a feature is not needed for the first useful workflow, do not build it yet".
  Flyway arrives with the first table (step 1), Modulith with the second module, frontend at step 7.

### 2026-09-28 · Claude commits

- **Decision:** Claude runs `git commit` after I review and approve a slice; work goes on `feat-<topic>` branches, merged into `main`.
- **Why:** less manual routine; review stays mine, the approval gate is unchanged.

### 2026-09-28 · Step 1: `skill` table conventions

- **Flyway:** only `flyway-core` (11.7.2, managed by Spring Boot); SQLite support is built in.
- **Status:** `TEXT` + `CHECK (status IN (...))`. The DB rejects bad values even if written outside Java.
- **id:** `INTEGER PRIMARY KEY AUTOINCREMENT`. Stable external identity is `key`, so UUID adds nothing.
- **Timestamps:** ISO-8601 UTC `TEXT`, fixed width `yyyy-MM-ddTHH:mm:ss.SSSZ`, so text order = time order
  (`Instant.toString()` is not fixed width).
- **No service layer yet:** controller calls the repository directly; add one when real logic appears.

### 2026-09-28 · Step 2: reading Skills

- **Decision:** `GET /api/skills` (all, ordered by `key`) and `GET /api/skills/{key}` (404 if missing). One shared row mapper.
- **Why:** API addresses Skills by stable `key`, never by numeric `id`. Ordering by `key` gives stable output.
- **Deferred:** paging and filtering. Not needed while the map holds tens of Skills.

### 2026-09-28 · Step 3: Evidence

- **Decision:** separate `evidence` table and `EvidenceRepository`; API nested under the Skill:
  `POST|GET /api/skills/{key}/evidence`. `Skill` itself does not carry an evidence list.
- **Why:** no invariant links Skill and Evidence yet, only ownership. The Skill card (step 8) will compose them.
- **SQLite foreign keys** are off by default; enabled with the `foreign_keys` pragma via Hikari data-source properties.
- **Deferred:** Evidence types (brief: v2), edit/delete, Revision (step 14).

### 2026-09-28 · Repositories stay concrete classes (no interfaces yet)

- **Decision:** no `SkillRepository` interface + `Jdbc...` implementation for now.
- **Why:** a future Hibernate switch changes the model (records cannot be JPA entities) and Spring Data brings its own
  interface shape, so an interface guessed today would not survive. Callers already depend only on domain methods;
  extract the interface when a second (JPA) implementation actually appears.
- **Alternative:** ports & adapters now (interface per repository). Rejected as speculative.

### 2026-09-28 · Step 4: Open Questions

- **Decision:** `open_question` table with nullable `resolved_at` (NULL = open); resolved questions are kept, not deleted.
  API: `POST|GET /api/skills/{key}/open-questions`, `POST .../{id}/resolve` (idempotent, keeps first resolution time).
- **Why:** the history of closed gaps is part of "how I become better".
- **`UNIQUE (skill_id, text)`:** `MINIBRAIN_UPDATE` resolves questions by text, so duplicates within a Skill would be ambiguous.
- **Not linked to Evidence on resolve:** Evidence is added via its own endpoint; the import Apply (step 13) will do both
  in one transaction.

### 2026-09-28 · Constraint violations → 409

- **Decision:** `sql-error-codes.xml` maps SQLite vendor code 19 (`SQLITE_CONSTRAINT`) to `DataIntegrityViolationException`;
  one `@RestControllerAdvice` turns it into `409 Conflict` (ProblemDetail, generic text, no SQL leaked).
- **Why:** Spring has no SQLite error codes, so every SQLite error was `UncategorizedSQLException` → 500.
  sqlite-jdbc sets no SQLState and uses code 19 for all constraints, so UNIQUE / FK / CHECK cannot be told apart
  by code; through the API only UNIQUE can realistically fire (FK and CHECK are guarded in Java).

### 2026-09-28 · Step 5: Skill relations

- **Decision:** `skill_relation` with composite primary key `(from_skill_id, to_skill_id, type)`, no surrogate id;
  `CHECK (from <> to)`; index on `to_skill_id` for incoming lookups. API speaks keys only:
  `POST /api/skills/{key}/relations {type, to}`, `GET` returns outgoing + incoming as `{from, type, to}`.
- **Why:** a relation is fully identified by the triple; the composite key forbids duplicates for free.
  The Skill card (step 8) needs both directions.
- **Deferred:** "REQUIRES makes RELATED_TO redundant" is a warning, not a constraint (step 11 Validation);
  RELATED_TO symmetry (A→B and B→A are two rows); cycle detection; delete.

### 2026-09-28 · Step 6: graph query as a separate read model (light CQRS)

- **Decision:** `GET /api/graph` served by `KnowledgeGraphQuery` with its own SQL and its own types
  (`KnowledgeGraph.Node {key, name, status}`, `KnowledgeGraph.Edge {from, type, to}`). No repositories involved.
- **Why:** brief §47-48: the graph UI reads query models, not the write model. A node is not a Skill and will
  grow UI fields (e.g. evidence / open question counters) without touching `Skill`.
- **Alternative:** compose from `SkillRepository.findAll()` + relations. Rejected: couples the map to the write model.
- **Not included:** node positions / layout (separate layer, step 18), counters, `schemaVersion` (API response,
  not a durable contract).

### 2026-09-28 · Package structure: modules by feature, thin layers inside (DDD-lite)

- **Decision:** `dev.minibrain.<module>.{domain, persistence, web, query}`; `shared/web` for cross-module
  HTTP concerns (`ApiExceptionHandler`). Only `skill` exists today.
- **Why:** top level matches brief §50 modules (later checkable by Spring Modulith); inside a module the domain,
  SQL, HTTP and read side are visibly separate. `query/` keeps the step 6 read/write split.
- **DDD stance:** DDD is the model, not the folders. The model is still mostly data (CRUD); richer domain
  (e.g. `SkillKey` value object, status/evidence rules, import Apply invariants) is added when rules appear.
- **Alternatives:** package per sub-feature without layers (hides the domain boundary); full hexagonal with ports
  and application services (pass-through layers and one-implementation interfaces today).
- **Cost:** repositories and domain types are `public` across sub-packages.

### 2026-09-28 · Step 7: minimal Skill Map

- **Decision:** `frontend/` from Vite `react-ts` template; `@xyflow/react` (React Flow 12) + `@tanstack/react-query`.
  Vite dev proxy `/api` → `localhost:8080`, so the backend has no CORS config.
- **Adapter:** `toFlow(graph)` is the only place with React Flow types; `api.ts` mirrors the backend read model.
- **Temporary layout:** status columns (DISCOVERED → MASTERED, left to right), edges right → left handles with arrows.
  Edges pointing to a lower-status column curve around nodes; real hybrid AUTO/PINNED layout is step 18.
- **TanStack Query now, not later:** listed in the brief and needed from step 8 (Skill card, refetch after changes).
- **No frontend tests yet:** the build (`tsc -b`) typechecks the adapter; add Vitest when frontend logic grows.

### 2026-09-28 · Initial map via AI prompt + temporary loader

- **Decision:** `docs/ai/initial-map-prompt.md` makes an AI interview me and output the brief's `MINIBRAIN_UPDATE`
  format unchanged (skills in `newSkills`, their evidence/questions in `changes`, `newRelations`).
  `scripts/load-update.ps1` loads it through the existing REST API.
- **Why:** reusing the real format means the step 10-13 import will accept the same files; the loader needs no
  backend changes and does not pull steps 10-13 forward.
- **Loader limits (ponytail):** validates the whole file before writing, but has no preview, no Revision, is not
  atomic, drops `reason`, and rejects `proposedStatus` / `openQuestionsResolved`. Deleted when step 13 lands.

### 2026-09-28 · Step 7b: radial skill tree, dark fantasy style

- **Decision:** radial layout (Path of Exile style): core in the centre, areas on a ring, skills fan outwards from
  their area hub. Area = key prefix (`ddd.aggregate` → `ddd`); a skill whose key equals the area is the hub,
  otherwise the frontend draws an area sigil. Core, sigils and branch lines are presentation-only.
- **Why:** status columns split topics apart; areas already exist in the keys, so no backend or domain change.
- **Style:** dark "abyss and embers" palette, status shown as the glow of an orb (the one loud element),
  relations styled by type, labels hidden on edges. Fonts self-hosted via `@fontsource` (local-first, no CDN).
- **Code:** `layout.ts` (pure positions), `skillMapParts.tsx` (custom nodes / trimmed straight edge),
  `toFlow.ts` (adapter), `skillMap.css`. Legend is a native `<details>`.
- **Left for step 18:** drag, AUTO/PINNED, saved positions. React Flow attribution stays visible (license).

### 2026-09-28 · Step 8: Skill card

- **Decision:** `GET /api/skills/{key}/details` served by `SkillDetailsQuery` (read side, brief §47 GetSkillDetails):
  skill + evidence + questions (open first) + relations seen from this skill (`outgoing`, other skill's key/name/status).
  Frontend: one `useQuery(['skill', key])`, read-only card as a right-side panel.
- **Why:** same light-CQRS split as the graph (step 6); one request, one shape for one screen.
- **Alternative:** compose four existing endpoints on the client. Rejected: the card's shape would live in the client.
- **UX:** click an orb to open, `Esc` / close button / click on empty map to close; related skills in the card are
  links; the selected skill is panned into view left of the card (instant with reduced motion).
  Selection state is a `useState` in `App`, no state library.
- **Not included:** editing (knowledge changes go through imports), focus mode (step 17), skill in the URL.

### 2026-09-28 · Backlog direction (from my wishes)

- **Bilingual EN/RU** covers knowledge content too; the AI writes both languages into `MINIBRAIN_UPDATE`.
- **Status filter dims** non-matching skills instead of hiding them.
- **"Needs review" uses the brief's `active` flag**, no new concept.
- **Gamification** (progression, levels, growth charts) is wanted: brainstorm before any design or code.
  The "no XP / achievements" hard rule no longer applies to this topic.
