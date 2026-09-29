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

### 2026-09-28 · Step 9: MINIBRAIN_CONTEXT export

- **Decision:** `GET /api/skills/{key}/context?goal=` served by `AiContextQuery`, built on `SkillDetailsQuery`
  (selects and trims, no SQL of its own). Newest 5 evidence, open questions only, up to 7 related skills ordered by
  usefulness (outgoing REQUIRES, PART_OF, LEADS_TO, RELATED_TO, then incoming). `goal` omitted when blank.
- **Contract extension:** `relatedSkills[]` carries `relation` + `direction` (not in the brief example) so the AI
  knows how a neighbour relates. schemaVersion stays 1 (first version of this contract).
- **Copy = instructions + JSON:** the Skill card copies `frontend/src/ai/session-prompt.md` (brief §17 rules and the
  MINIBRAIN_UPDATE format) followed by the context, so one paste starts a session.
- **Known gap:** the temporary loader still rejects `proposedStatus` / `openQuestionsResolved` that the session
  prompt asks for; the real import (steps 10-13) will accept them.
- **Session prompt teaches, not only tests** (update 2026-09-28): the AI explains each topic fully (what, why,
  example, mistakes, trade-offs, links to my skills), gives English originals for terms, then verifies.
  On "Export MiniBrain" it first writes bilingual EN/RU study notes, then the JSON. Storing those notes
  (`LearningSession`, brief §50) is planned into the import design (steps 10-13).

### 2026-09-28 · Steps 10-11: import preview (module `importing`)

- **Decision:** `POST /api/imports/preview` takes raw text (file content or pasted chat text) and returns an
  `ImportPreview`: items grouped by the brief §20 sections, each with a verdict (READY / ALREADY_PRESENT / INVALID),
  issues (brief §21 codes + ORPHAN_SKILL, REDUNDANT_RELATION, UNKNOWN_OPEN_QUESTION) and a default selection.
  Nothing is written.
- **Per-item validation:** one bad line never blocks the file. Warnings keep an item applicable; STATUS_DOWNGRADE,
  REDUNDANT_RELATION and suggested skills are not pre-selected; ORPHAN_SKILL is (my call: warning, not block).
- **Parsing:** `format/UpdateDocument` mirrors the JSON with enum values as Strings (an unknown status invalidates
  one item, not the file). Pasted chat text: the fenced ```json block containing MINIBRAIN_UPDATE is extracted.
- **New layers:** `importing/application` (use cases over several queries; Apply in step 13 joins it) and
  `importing/format` (external contract). No `importing/domain`: no domain rules of its own yet.
  `Change` is a sealed interface so Apply can switch over all kinds exhaustively.
- **Relations for the AI:** MINIBRAIN_CONTEXT gets `knownSkills` (key + name of every skill; not the graph);
  the session prompt requires every new skill to be linked and existing keys to be reused exactly.

### 2026-09-28 · Steps 12-13: Import dialog and Apply

- **Decision:** an **Import** button on the map opens a native `<dialog>`: paste text or pick a file → preview
  grouped by section with checkboxes (only READY items can be ticked) → `POST /api/imports/apply {text, selectedIds}`.
- **Apply is stateless:** the server re-runs the preview on the same text and applies only chosen items that are
  still READY, in one `@Transactional` (all or nothing), skills first, relations last. A client never sends changes,
  only ids. Re-applying the same text changes nothing (everything is ALREADY_PRESENT).
- **Suggested skill ticked = unlocked** as a DISCOVERED skill (brief §14), with the reason as description.
- `scripts/load-update.ps1` removed. No Revision yet (step 14).

### 2026-09-28 · Step 13b: session notes (module `learning`)

- **Decision:** tables `learning_session` (topic, notes_en, notes_ru) + `learning_session_skill`. The notes are taken
  from the pasted chat text: the `## English` / `## Русский` sections above the fenced MINIBRAIN_UPDATE block
  (`importing/format/ChatNotes`). They show up as one "Study notes" preview item; Apply saves them last, linked to
  every skill the import touched. Identical notes are ALREADY_PRESENT (re-import is a no-op).
- **Why from the chat text, not inside the JSON:** long markdown with code inside a JSON string is where AIs break
  escaping; and the AI would write the notes twice. Cost: a bare `.json` file carries no notes.
- **Fence-safe extraction:** the JSON regex uses `(?:(?!```).)` so a ```json example inside the notes cannot be
  mistaken for the update block.
- **UI:** the Skill card only gets a "Study notes (N)" button; notes are read in a centred `<dialog>` with an
  EN/RU switch, rendered by `react-markdown` (no raw HTML).
- Bilingual knowledge content (names, evidence, ...) is split out as step 13c.
- **Revised the same day (real session):** the AI put the notes inside the JSON (`"notes": {"en", "ru"}`) on its own,
  with correct escaping of long markdown, tables and code, and I copied only the JSON block. So the JSON `notes`
  field (or `session.notes`) is now the primary source and the session prompt asks for it; the chat-text sections
  stay as a fallback. A leading "## English" / "## Русский" heading inside a note is dropped.
  Bonus: a `.json` file now carries its notes too.
- **Bug found in live use:** notes were linked only to skills of *applied* changes, so re-importing a file whose
  changes were ALREADY_PRESENT saved the notes with no skill. Now they link to every existing skill the document
  refers to (applied or already present). The one orphaned session in my DB was linked by hand (backup taken).

### 2026-09-28 · Step 13c: bilingual knowledge (EN / RU)

- **Storage:** extra columns (`skill.name_ru`, `skill.description_ru`, `evidence.text_ru`, `open_question.text_ru`,
  migration V6). The existing columns are English and stay the canonical text for matching; `*_ru` may be NULL.
  Exactly two languages, so no generic translation table (every query would become JOINs).
- **Format:** MINIBRAIN_UPDATE `schemaVersion: 2`: knowledge texts are `{ "en", "ru" }` objects
  (`importing/format/LocalizedText`); a plain string still works and means English, so v1 files keep importing.
  A text given only in Russian is stored as the primary text. `openQuestionsResolved` matches either language.
- **Read side:** `KnowledgeGraph.Node`, `SkillDetails` (+ evidence, questions, relations) carry `*Ru` fields;
  MINIBRAIN_CONTEXT stays English (v1).
- **UI:** EN / RU switch in the toolbar (remembered in localStorage, falls back to the browser language).
  UI texts come from a plain dictionary in `i18n.ts` (no library), passed via React context; knowledge texts use
  `pick(lang, en, ru)` with English fallback. Server-side preview labels and issue messages stay English.
- **Session prompt** now asks for schemaVersion 2 with bilingual texts.

### 2026-09-28 · Step 13d: Manage window and translation round-trip

- **Decision:** a "Manage" button opens a window whose first section exports texts without a Russian version:
  copy all for AI, download as a `.md` file (instructions + JSON), or copy one skill.
  `GET /api/translations/missing[?skill=]` returns `MINIBRAIN_TRANSLATION_REQUEST` (only missing texts, by skill).
- **Answer comes back through Import:** MINIBRAIN_UPDATE v2 gets a `translations` section
  (`{skill, name, description, evidence[], openQuestions[]}` with `{en, ru}` pairs). `en` must match the stored
  English text exactly (that is how the evidence / question is found); unknown text = UNKNOWN_TEXT error.
  Filling a missing translation is pre-selected; replacing an existing one is REPLACES_TRANSLATION, opt-in.
- Only EN → RU for now (Russian-only texts are stored as the primary text, so the reverse gap barely exists).
- The window is where later maintenance goes (backups, `current.json` export).
- **Import inside Manage:** the Manage window has an "Import the AI answer" button that runs the same import flow (`ImportBody`) in place and returns to Manage with a refreshed list.

### 2026-09-28 · Step 14: Revision history (module `revision`)

- **Decision:** append-only tables `revision` (source INITIAL / IMPORT / MANUAL, topic, time) and `revision_change`
  (type, skill key, from/to status, text, related key, relation type, `occurred_at`). Plain columns, not a JSON blob,
  so "growth over time" stays simple SQL for the future game charts. Skills are referenced by key, not id.
  Not Event Sourcing: current state stays in the knowledge tables (brief §22).
- **Who writes:** the import (one revision per Apply, same transaction; nothing when nothing changed) and the manual
  REST endpoints (one revision per call, `@Transactional` on the controller method; an idempotent re-resolve writes
  nothing). Rule from CLAUDE.md: knowledge changes create a Revision.
- **Initial state:** migration V7 rebuilds one INITIAL revision from existing rows with their real timestamps.
  Status history before V7 is unknown, so SKILL_CREATED carries the status at migration time.
- **No module cycle:** `revision` stores statuses / relation types as plain names and depends on no other module;
  `skill` and `importing` write into it.
- **UI:** "History" section in the Manage window (native `<details>` per revision).

### 2026-09-29 · Step 15: `current.json` export

- **Decision:** `GET /api/exports/current` returns `MINIBRAIN_CURRENT` schemaVersion 1: flat lists `skills`,
  `relations`, `evidence`, `openQuestions`, `learningSessions`, linked by skill keys. Texts as `{en, ru}`
  (`LocalizedText`, same as UPDATE v2). Stored values only: timestamps as stored, no calculated fields, no UI state.
- **No revision history in the file:** current.json answers "what do I know now" (brief §25); after a future restore
  the history starts with a RESTORE revision.
- **Deterministic:** every list is sorted by stored values (key, time, text), never by numeric id, so the same
  knowledge gives the same file even after a restore into a fresh database. Only `exportedAt` changes.
- **Browser download only** (Manage → "Full export", `minibrain-current-<date>.json`). The backend writes nothing to
  `exports/`; snapshots and restore with preview are a later slice.
- **Where:** module `importing` (`format/CurrentState`, `query/CurrentStateQuery`, `web/ExportController`). Restore
  will read the same contract, and `skill` must not read `learning` tables. No new module (brief §50).

### 2026-09-29 · Step 16: Suggested Skills and fog of war

- **Model:** own table `suggested_skill` (V8), not a SUGGESTED status: a suggestion is not a skill (brief §14), so it
  has no status, evidence or questions and never needs exceptions in skill code. The source is a plain key
  (`source_skill_key`), not a foreign key: it may be missing.
- **Import:** a ticked suggestion is stored in the fog instead of becoming a skill, and suggestions are pre-selected
  now (storing is harmless). MINIBRAIN_UPDATE `suggestedSkills[]` got optional `from` and a bilingual `reason`
  (plain strings still load). A dismissed suggestion counts as ALREADY_PRESENT, so it is not offered again.
- **Decisions on the map:** `POST /api/suggestions/{key}/unlock` creates a DISCOVERED skill (reason = description)
  plus "source LEADS_TO new skill" when the source exists, deletes the suggestion, writes a MANUAL revision.
  `.../dismiss` sets `dismissed_at`. Storing or dismissing a suggestion writes no revision: it is not knowledge.
- **Read side:** `/api/graph` carries open `suggestions`; current.json v2 carries all of them (dismissed too).
- **UI:** a `fog:<key>` node (translucent dashed orb with "?") just outside its source skill, a faint dotted line
  to it, and a suggestion card with Unlock / Hide. Fog of war is presentation only: an SVG sheet in flow
  coordinates with a hole = convex hull of all known nodes, widened and blurred. No fog between areas, only
  beyond the outermost skills, so suggestions sitting outside look hidden in it.
- **Context knows the fog (follow-up):** MINIBRAIN_CONTEXT v2 lists open suggestions in `suggestedSkills`
  (key + name, separate from `knownSkills`); the session prompt forbids suggesting them again and says to return a
  topic that was actually taught as a `newSkills` entry with the same key. Creating a skill through the import
  deletes the open suggestion with that key. (A skill created by hand via `POST /api/skills` does not; no UI for it.)
- No animated smoke.

### 2026-09-29 · Step 17: search, focus mode, status filter

- **Search is client-side** over the already loaded graph: English name, Russian name or key, case-insensitive,
  suggestions included. No `SearchSkills` endpoint while the map has tens / hundreds of skills. Picking a result
  equals clicking the node (select, center, card, focus). Esc in the search clears only the search.
- **Focus is automatic on selection** (brief §32): bright = the selected node, its direct relations both ways, its
  area hub, the suggestions growing from it; only lines touching it stay bright. Exit = Esc / click on the empty map.
- **Status filter in the legend:** toggle one or more statuses, the rest is dimmed, never hidden. With focus on, a
  node must pass both.
- **One mechanism:** `litIds()` in the `toFlow` adapter returns the bright ids (or null = all); everything else gets
  the `is-dim` class. Presentation only: no backend change, no revision.

### 2026-09-29 · Step 18: hybrid layout (AUTO / PINNED)

- **Storage:** table `node_position(node_id, x, y)` (V9), `GET` / `PUT /{id}` / `DELETE /api/layout/positions`.
  `node_id` = skill key or `area:<name>` (sigil); no foreign key, a pin of a vanished node is unused. A separate
  resource, not part of `/api/graph` (brief §31); layout is not knowledge: no revision, not in current.json.
- **Model:** the radial layout stays AUTO; `applyPins()` (pure, `layout.ts`) puts pinned nodes on top. A pinned hub
  carries its unpinned skills along (the whole area moves); a pinned skill stays where it was put. Suggestions and
  the core cannot be dragged; suggestions follow their source.
- **Drag:** React Flow reports drag steps through `onNodesChange`; each step updates the positions query cache (the
  area moves live), the drop saves it. React Flow's measured node sizes are kept and merged back into the rebuilt
  nodes: without them it treats the dragged node as uninitialized (warning #015) and the cursor jumps.
- **Reset:** Manage → "Map layout" → back to automatic. Not done: unpin a single node, auto-arrange a cluster.

### 2026-09-29 · Game layer: design, then G1 (module `game`)

- **Design:** [`docs/game-design.md`](game-design.md) (brainstorm with the owner). Only demonstrated understanding
  gives XP; the "no XP / achievements" rule is lifted for this topic. Streaks and decay stay out.
- **Game state is computed, never stored:** `GameQuery` replays every `revision_change` oldest first into per-skill
  progress (`GameReplay`), then derives XP, levels, titles and area ranks (`GameRules`). One path for the current
  state and, later, for history. A lower status honestly lowers XP. Chosen over an XP ledger (Event Sourcing, rules
  frozen) and a hybrid (stored achievements).
- **Module `game`:** `domain` (pure rules + replay, statuses as plain names like `revision`), `query` (`GameState`),
  `web` (`GET /api/game`). Depends on the revision table only; nothing depends on it yet.
- **Numbers (G1):** status 0/10/30/60/100, evidence +5 (max 5 per skill), resolved question +8; level L starts at
  25 * (L-1)^2 XP; titles by level bands; talent points = level - 1 until G2 adds spending.
- **UI:** hero badge top right (title, level, XP bar, talent points); "Rank N · Name" under every area hub.
  `toFlow` now takes one `view` options object instead of five positional parameters.
- **Specs live in the repo** (`docs/`), and nothing about MiniBrain goes to the Obsidian vault (owner's call).

### 2026-09-29 · Game G2: talent points and vision

- **History knows unlocks:** revision change type `SKILL_UNLOCKED` (V10 rebuilds `revision_change`: SQLite cannot
  change a CHECK constraint; rows and ids are copied). Unlocks made before V10 stay SKILL_CREATED and cost nothing.
- **The unlock use case** moved from `SuggestionController` to `skill/application/SuggestionUnlocker` (returns
  `Optional<Skill>`, records SKILL_UNLOCKED + RELATION_ADDED). The game decides who may call it:
  `POST /api/game/unlock/{key}` checks talent points (`max(0, level - 1 - unlocks)`) and answers 409 without one.
  The old `/api/suggestions/{key}/unlock` is gone, so points cannot be bypassed; dismiss stays where it was.
  Direction stays `game -> skill`.
- **Vision** = `min(200, 60 + 30 * (level - 1))` px, sent in `GameState.player.vision`. The frontend uses it as the fog
  margin; fog topics sit 120 px out, so below level 3 they show only "?" (no name in the node, the card or search).
  The vision gate is UI-only; the server checks points.
- A skill the AI creates in `newSkills` stays free (SKILL_CREATED).

### 2026-09-29 · Game G3: quests

- **Quests are the open questions, nothing stored:** `game/query/QuestsQuery` (one SQL over `open_question JOIN
  skill`, `resolved_at IS NULL`) serves `GET /api/quests`; area via `GameRules.areaOf`, reward `QUESTION_XP` sent by
  the backend so +8 lives in one place. Chosen over putting questions into `/api/game` (fetched on every badge refresh)
  and over N calls of skill details.
- **Order:** SQL sorts by skill key and question id; a stable sort by area in Java keeps that order inside an area
  (the area rule stays in Java, not repeated in SQL).
- **Take quest** copies the AI session with the goal = the question in English (the context is English-first) and
  selects the skill. The clipboard text is built by `frontend/src/aiSession.ts` (`copyAiSession`), shared with the
  Skill card. A quest is finished only by an AI session (`openQuestionsResolved`), never by a button.
- **Next idea recorded (G3b, owner):** mastery gate, a fog topic unlocks only when its source skill is UNDERSTOOD+,
  on top of the talent point (design §6).

### 2026-09-29 · Game G3b: mastery gate

- **Rule:** a fog topic unlocks only when its source skill is UNDERSTOOD or higher (`GameRules.UNLOCK_STATUS`,
  `opensTheFog`), on top of the talent point. A suggestion without a source skill on the map has no gate.
- **Server enforces it:** `POST /api/game/unlock/{key}` answers 409 "master X first" after the point check; it reads the
  suggestion and its source through `skill/persistence` (direction stays `game -> skill`).
- **One threshold:** `GameState.unlockStatus` tells the frontend the status, so the rule is not copied into TypeScript.
  `App` computes the set of gated suggestion keys once; the map draws a bronze padlock on those fog nodes (not on
  nameless far ones) and the suggestion card disables Unlock with a hint naming the skill and the status.

### 2026-09-29 · Quest mode for AI sessions

- **Prompt only, no backend:** `copyAiSession(skillKey, goal, questQuestion?)` appends `ai/quest-prompt.md` when a
  quest is taken. The normal session keeps "explain first"; quest mode overrides it for the goal question only.
- **The AI asks first, in my UI language:** the quest block carries the question as shown in the Quests window
  (`pick(lang, ...)`), while `goal` in the context stays the exact English text so `openQuestionsResolved` matches.
- **`## MINIBRAIN_CONTEXT` heading moved from `session-prompt.md` into the code**, so extra rule blocks go before it
  (the first try put quest mode under that heading and the AI read it as data and ignored it).
- **"Answered by" is evidence text, not a column:** the export adds one `evidenceAdded` item tied to the question,
  saying "answered myself" or "the AI told me the answer". A stored link and different XP wait until a real need.

### 2026-09-29 · Game G4a: constellations

- **G4 split into G4a-d:** constellations need only the current state; achievements, the journal and the chart need a
  timeline in the replay, which G4b introduces and G4c / G4d reuse.
- **Rule in `GameRules.isConstellation`** (at least 3 skills, all UNDERSTOOD+); `GameReplay.constellations()` groups the
  replayed statuses by area, so a status going down loses the constellation. Sent as `GameState.Area.complete`.
- **Map:** `toFlow` maps every node id of a complete area (hub + members) to that area; the hub gets
  `is-constellation`, and so does every line whose two ends are in the same complete area (lines leaving it stay
  normal, so the shape reads). CSS only: a gold ring that breathes, a ripple going out, lines glowing in the same
  4 s rhythm; off under `prefers-reduced-motion`.

### 2026-09-29 · Game G4b: timeline, hero window, XP chart

- **Snapshot per revision:** `GameReplay.snapshot()` copies the state so far; `game/query/HeroQuery.timeline()` takes
  one after the last change of each revision (one import = one moment). Chosen over one snapshot per day (the journal
  could not tell two sessions of a day apart) and over the replay emitting events (rules of "what is an event" spread
  through the replay). G4c / G4d build on `timeline()`.
- **Own endpoint `GET /api/game/hero`**, fetched only while the hero window is open, so `/api/game` (badge, map)
  stays light. `GameQuery` keeps its own SELECT: two copies of one query are cheaper than a shared loader.
- **Chart:** last snapshot per day (server's local zone), x in real time so pauses show, dashed lines where levels
  start, the top line is the next level. Plain SVG in `HeroDialog.tsx`.
- **The hero badge is a button** opening the window; its content became `span`s (only phrasing content inside a
  button).

### 2026-09-29 · Game G4c: achievements

- **Kept once earned (owner):** the date is the first moment on the timeline where the condition holds; a later drop
  does not take it away. Levels and constellations can still be lost; the journal (G4d) will show that.
- **`game/domain/Achievement`:** an enum, one `Predicate<Snapshot>` per value, so the list is a pure domain rule
  tested without a DB; `HeroQuery` only dates it. A new achievement is one line plus its two i18n strings.
- **Snapshot counters:** statuses of all skills, all evidence (uncapped: the cap of 5 is only for XP), resolved
  questions, unlocks, notes. `NOTES_SAVED` has no skill key, so it is counted before the replay skips key-less changes.
- **"First X"** = at least one skill at status X or higher. "Boss slayer" waits for bosses.
- **Hero window:** "Achievements n / 9", earned first (oldest first) with a gilded rim and the date, then locked ones
  dimmed with their condition.

### 2026-09-29 · Game G4d: journal of deeds

- **A deed = the difference of two neighbouring moments** (`game/domain/Deed.between`, pure Java): level up / down,
  rank up / down per area, constellation formed / lost, topics unlocked. Achievements are added at the moment they were
  earned. The first moment is compared with an empty map, so the initial map shows up as the first day's deeds; a jump
  of several levels in one moment is one entry.
- **Losses are shown** (design §3), in ember; achievements in gold.
- **The snapshot keeps the unlocked keys** (not a count) so the journal can name the topic.
- **Order:** newest moment first, deeds inside a moment in their natural order (level, ranks, constellations, topics,
  achievements). Grouped by local day in the hero window.
