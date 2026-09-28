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
