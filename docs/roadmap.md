# Roadmap

A direction, not a fixed plan. Re-evaluate after every few steps.
Before each step: *what is the smallest useful version?*
After each step: *did we solve a real current problem, or build infrastructure for an imagined future?*

| # | Step | Status |
|---|------|--------|
| 0 | Repository skeleton: Spring Boot boots, SQLite datasource configured, docs, CLAUDE.md | ✅ done |
| 1 | Flyway + first migration (`skill` table) + create one Skill | ✅ done |
| 2 | Read Skill data back | ✅ done |
| 3 | Evidence | ✅ done |
| 4 | Open Questions | ✅ done |
| 5 | Relations | ✅ done |
| 6 | Basic graph query | ✅ done |
| 7 | Minimal React Skill Map (create `frontend/`) | ✅ done |
| 7b | Game-like look: radial area clusters + dark fantasy style | ✅ done |
| 8 | Select node + Skill card | ✅ done |
| 9 | AI Context export (`MINIBRAIN_CONTEXT`) | ✅ done |
| 10 | `MINIBRAIN_UPDATE` parser (`POST /api/imports/preview`) | ✅ done |
| 11 | Validation (per-item issues, brief §21 + orphan / redundant relation) | ✅ done |
| 12 | Preview / Change Set (Import dialog: paste or file, checkboxes) | ✅ done |
| 13 | Apply (one transaction; temporary loader removed) | ✅ done |
| 13b | Session notes as `LearningSession` (EN/RU study notes, reading window from the Skill card) | ✅ done |
| 13c | Bilingual knowledge (EN/RU columns, MINIBRAIN_UPDATE v2) + UI language switch | ✅ done |
| 13d | Manage window: export untranslated texts for AI (all / file / per skill), `translations` import | ✅ done |
| 14 | Revision (module `revision`, history in Manage, initial state rebuilt) | ✅ done |
| 15 | `current.json` export (download from Manage; snapshots / restore later) | ✅ done |
| 16 | Suggested Skills / Fog (stored suggestions, `?` nodes, unlock / dismiss, fog of war beyond known land) | ✅ done |
| 17 | Focus mode / search (search by EN/RU name or key, automatic focus, status filter in the legend) | ✅ done |
| 18 | Hybrid layout: drag to pin (saved in DB), a hub carries its area, reset in Manage | ✅ done |

## Game layer (design: [`game-design.md`](game-design.md), plan: [`game-plan.md`](game-plan.md))

| Slice | What | Status |
|---|---|---|
| G1 | Module `game`: XP, levels, titles, area ranks computed from revisions; hero badge; ranks under hubs | ✅ done |
| G2 | `SKILL_UNLOCKED`; unlocking costs a talent point; vision and hidden names in the fog | ✅ done |
| G3 | Quests window (all open questions) + Take quest | ✅ done |
| G3b | Mastery gate: a fog topic unlocks only when its source skill is UNDERSTOOD+ (design §6) | ⬜ |
| G4 | Constellations, achievements, journal of deeds, XP chart, hero window | ⬜ |
| G5 | AI teaches by rank (MINIBRAIN_CONTEXT v3, context assembly moves to `learning`) | ⬜ |
| later | Pathfinder points / new branch, area bosses (with the AI chat) | ⬜ |
| idea | XP for reading a skill in English (design §13a, options open, brainstorm first) | ⬜ |

## Backlog (my wishes, not scheduled yet)

Decided direction is noted; details still get discussed when a wish is picked up.

- ~~English and Russian, for UI and knowledge.~~ Done in steps 13c-13d. Left: translating server-side preview messages.
- ~~Status filter on the canvas: dim, never hide.~~ Done in step 17 (legend).
- **"Needs review" = the brief's `active` flag (§15).** A button on the Skill card marks a skill I want to
  revisit because I no longer remember it well. Manual mark, not spaced-repetition scheduling.
  Open: button wording, how the map shows it (brief suggests subtle pulsing). Per brief: no Revision.
- **Make it a game, not a second Obsidian.** What I can do with new knowledge, levels / progression,
  charts of growth over time. Needs a brainstorm first. This lifts the CLAUDE.md "no XP / achievements"
  rule for this topic, because I explicitly asked for it.
- **Session notes saved in MiniBrain.** The AI already writes bilingual (EN/RU) study notes before the JSON
  (session prompt, step 9). Store them: brief §50 module `learning` with `LearningSession` (topic, date, notes EN/RU,
  touched skills). Import carries it as e.g. `session.notes: {en, ru}`.
  **UI decided:** the Skill card stays as it is (not overloaded). It only gets a button that opens a centred
  reading window (same native `<dialog>` as Import) with the session notes of that skill, EN/RU switchable.
- **Pending verification questions carried over.** At the end of a session the AI asks short checks
  (e.g. "Why is exposing the whole Payment aggregate worse than PaymentResult?"). Unanswered ones travel into
  MiniBrain (e.g. `pendingChecks` in `MINIBRAIN_UPDATE`), are shown on the Skill card, and go into the next
  MINIBRAIN_CONTEXT; the AI starts the next session with them, then explains and gives new material.
  Answering them is what unlocks new things (ties into the gamification brainstorm).
  Open: difference from Open Questions (a check tests me, a question is a gap), schema, what exactly gets unlocked.

- **AI chat inside MiniBrain (next after step 16, brainstorm first).** Learn right in the app: a chat with the
  selected skill as context, answers turn into MINIBRAIN_UPDATE (new knowledge, new skills) without copy-paste.
  I pay for ChatGPT Plus and Claude Max. Open: subscriptions give no API key (API is billed separately);
  a local `claude -p` (Claude Code, Max login) might drive the chat; API key vs subscription; where the chat
  history lives; how the update is proposed and previewed.
- **Ask the AI for a new branch.** I tell the AI what I would like to learn next (an idea, a new area of knowledge)
  and it proposes a whole new branch of the map, e.g. an `architecture` root with its first skills and relations.
  Probably arrives as suggestions (step 16 fog) under a new area; the AI chat would be the natural place to ask.
- **All open questions in one place.** A questions section across all skills; clicking a question opens the Skill
  card it belongs to. Important to tie into the game (answering questions = progress / unlocks), so design it
  together with the gamification brainstorm.
