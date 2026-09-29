# Handoff 2026-09-29 (evening): game G3 + G3b done

Read at session start (CLAUDE.md), together with `docs/roadmap.md`, `docs/decisions.md`, `docs/game-design.md`,
`docs/game-plan.md`. Answer the owner in Russian. No em dashes, no `Co-Authored-By` trailer.

## State

- Branch `feat-game` = `main` = `origin/main` at `3231fed`, clean, pushed.
- Real DB `data/minibrain.db` at Flyway v10 (no new migrations this session).
- 51 backend tests green; frontend build + oxlint clean (3 old `only-export-components` warnings in `skillMapParts.tsx`).
- The app was left running on the real DB (`dev.ps1 status`).

## Done this session

| What | Commit |
|---|---|
| G3 task 1: `GET /api/quests` (`game/query/QuestsQuery`, `Quest`), G3 plan in `game-plan.md` | ec71f1a |
| G3: Quests window (`QuestsDialog.tsx`), Take quest, shared `aiSession.ts` (`copyAiSession`) | 823dc47 |
| G3b: mastery gate (`GameRules.UNLOCK_STATUS` / `opensTheFog`, 409 in unlock, `GameState.unlockStatus`, padlock on gated fog nodes, disabled Unlock with hint) | 6062f84 |
| Backlog: quest mode for AI sessions; "I read it" buttons (notes window + Skill card) | 3231fed |

Owner live-tested G3 (real import resolved a quest) and G3b (on a scratch DB copy with a seeded LEARNING skill).

## Next (owner decides, nothing started)

- **G4:** constellations, achievements, journal, XP chart, hero window (brainstorm + plan first).
- **Quest mode for the AI session** (roadmap backlog): no spoilers until I answer; the answer closes the question on
  import and is tied to the new knowledge. Open questions listed there.
- **"I read it" buttons** (roadmap backlog, ties into design §13a): notes window button unlocks the Skill card button.
- Design §13a (XP for reading in English) is effectively the same topic as the read buttons: brainstorm them together.

## Gotchas (still valid)

- Tests only via the `test-runner` agent. Shared in-memory test DB: unique key prefixes (`qq`, `gu` used now).
- Changing the unlock rules breaks `SuggestionControllerTests` if its source skill is below UNDERSTOOD (fixed once).
- Bash heredocs with backticks / `\\n` / quotes break: write edit scripts with the Write tool into the scratchpad,
  keep CRLF (`newline=''`, detect `\r\n`) when editing files.
- Live checks that write: scratch DB copy via `MINIBRAIN_DB_URL`, restart on the real DB afterwards.
