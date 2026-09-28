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
| 10 | `MINIBRAIN_UPDATE` parser (design together with: bilingual content, session notes as `LearningSession`, see Backlog) | ⬜ |
| 11 | Validation | ⬜ |
| 12 | Preview / Change Set | ⬜ |
| 13 | Apply | ⬜ |
| 14 | Revision | ⬜ |
| 15 | `current.json` export | ⬜ |
| 16 | Suggested Skills / Fog concept | ⬜ |
| 17 | Focus mode / search | ⬜ |
| 18 | Layout improvements (radial auto layout done early in 7b; left: AUTO/PINNED, drag, saved positions) | ⬜ |

## Backlog (my wishes, not scheduled yet)

Decided direction is noted; details still get discussed when a wish is picked up.

- **English and Russian, for UI and knowledge.** Interface texts in both languages with a switch.
  Knowledge content too (skill names, descriptions, evidence, questions): the AI prepares it in both
  languages in `MINIBRAIN_UPDATE`. Open: schema shape (e.g. `name: {en, ru}`), schemaVersion bump, migration.
- **Status filter on the canvas: dim, never hide.** Skills not matching the selected status(es) are dimmed;
  relations stay readable. Related: step 17 (focus mode).
- **"Needs review" = the brief's `active` flag (§15).** A button on the Skill card marks a skill I want to
  revisit because I no longer remember it well. Manual mark, not spaced-repetition scheduling.
  Open: button wording, how the map shows it (brief suggests subtle pulsing). Per brief: no Revision.
- **Make it a game, not a second Obsidian.** What I can do with new knowledge, levels / progression,
  charts of growth over time. Needs a brainstorm first. This lifts the CLAUDE.md "no XP / achievements"
  rule for this topic, because I explicitly asked for it.
- **Session notes saved in MiniBrain.** The AI already writes bilingual (EN/RU) study notes before the JSON
  (session prompt, step 9). Store them: brief §50 module `learning` with `LearningSession` (topic, date, notes EN/RU,
  touched skills), shown on the Skill card. Design it inside the import (steps 10-13), e.g. `session.notes: {en, ru}`.

