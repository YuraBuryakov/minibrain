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
