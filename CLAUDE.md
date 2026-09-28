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
→ I review and understand it → I run it → I commit → next task
```

For every task Claude:

1. Restates the small goal.
2. Names the important trade-offs; offers 2–3 options when it really matters — **I choose**.
3. Proposes the simplest implementation.
4. Writes only the code required for this slice.
5. Briefly explains unfamiliar concepts.
6. Stops when the slice is done and waits for my review/commit.
7. At the end of a step updates `docs/roadmap.md`, `docs/decisions.md` and the project `status.md`.

Hard rules:

- Never implement future roadmap steps unasked; never refactor unrelated code.
- Before a new abstraction or interface, explain why it is needed.
  If one simple class solves it, do not create five interfaces.
- No microservices, auth, Docker, Event Sourcing, CQRS frameworks, command buses,
  XP/achievements, spaced repetition — unless I explicitly ask.
- Claude does not run `git commit` — commits are mine.

## Project layout

```text
backend/     Spring Boot (Java 21, Maven, Spring JDBC, SQLite)
frontend/    React + TypeScript + Vite   (not created yet — roadmap step 7)
docs/        brief, roadmap, decisions log
data/        minibrain.db (runtime, not in Git)
exports/     current.json + snapshots/ (portable, may be in Git)
imports/     MINIBRAIN_UPDATE files to import
backups/     local backups (not in Git)
```

## Conventions

- Base package: `dev.minibrain`. Feature packages later: `skill`, `learning`, `importing`, `revision`.
- Persistence: Spring JDBC (`JdbcClient`), SQL is visible. No JPA for now.
- Schema changes go through migrations (Flyway, added in step 1).
- Skill has DB `id` and stable external `key` (e.g. `ddd.aggregate`). JSON contracts use `key`, never numeric ids.
- Raw JSON (`JsonNode`, `ObjectMapper`) must not leak into the domain. React Flow types must not leak into the backend.
- Knowledge changes create a Revision. Presentation changes (move node, focus, zoom) do not.
- Every durable JSON contract has `schemaVersion`.

## Running

```bash
cd backend
mvn spring-boot:run      # http://localhost:8080
mvn test
```
