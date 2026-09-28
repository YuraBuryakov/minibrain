# MiniBrain game layer: design

Status: approved in the brainstorm of 2026-09-29, not built yet. Build order: slices G1-G5 at the end.
Related backlog ideas: AI chat inside MiniBrain, "ask the AI for a new branch" (both later, see "Later").

## 1. Goal

MiniBrain should feel like a game, not like a second Obsidian. The game makes growth visible and gives each gain a
consequence on the map. It never rewards time, clicks or imports as such: **only demonstrated understanding counts**
(brief §1: "show the history of how I become better").

The CLAUDE.md rule "no XP / achievements" is lifted for this topic by the owner. Spaced repetition and daily streaks
stay out (streaks create guilt; decay over time is spaced repetition).

## 2. The loop

```text
demonstrate understanding (evidence, a higher status, a resolved question = quest done)
  -> XP for the skill, its area and the character
  -> levels: character level, area ranks
  -> talent points -> unlock topics from the fog -> their open questions become new quests
  -> vision grows -> fog topics become readable
  -> areas complete into constellations -> titles, later a boss
  -> everything visible in the hero panel, the journal and the chart
  -> the AI teaches deeper as the area rank grows
```

## 3. Core principle: the game state is computed

Nothing game-specific is stored, except facts that cannot be derived (a boss result, later). XP, levels, ranks,
constellations, achievements, talent points, the journal and the XP history are **computed on every request by
replaying the revision history** (`revision` + `revision_change`, complete since the INITIAL revision).

- One code path gives both the current state and the history (state at any moment = replay up to that moment).
- Rules can be tuned later; everything is recomputed, nothing drifts from the real knowledge.
- Honest: when a status goes down, XP goes down; a level, rank or constellation can be lost again.
- Not Event Sourcing: knowledge tables stay the source of the current knowledge; the game only reads history.

## 4. XP

Per skill, from its state at a given moment:

| Source | XP |
|---|---|
| Status DISCOVERED / LEARNING / UNDERSTOOD / APPLIED / MASTERED | 0 / 10 / 30 / 60 / 100 |
| Each evidence | +5, at most 5 counted per skill (+25) |
| Each resolved question (a finished quest) | +8 |

- Area XP = sum of its skills (area = key prefix, `ddd.aggregate` -> `ddd`; the hub skill `ddd` belongs to it).
- Character XP = sum of all skills.
- Suggestions (fog) give nothing.
- All numbers are constants in one place (`GameRules`).

## 5. Levels, ranks, titles

- Level `n` needs `25 * n^2` XP (25, 100, 225, 400, 625, ...), same curve for the character and for areas.
  Level 1 = 0 XP.
- **Area rank** = area level, named: 1 Novice, 2 Apprentice, 3 Adept, 4 Expert, 5+ Master (RU: Новичок, Ученик,
  Адепт, Эксперт, Мастер). Shown under the hub ("DDD · Adept") and as rings around it.
- **Character title** by level: 1-2 Student, 3-5 Journeyman, 6-9 Scholar, 10-14 Architect, 15+ Magister
  (RU: Ученик, Подмастерье, Знаток, Архитектор, Магистр). With at least one constellation the best one is added:
  "Scholar of DDD" (best = highest area XP among constellations).

## 6. Talent points and unlocking

- `talent points = (character level - 1) - unlocks spent`. It can drop below zero when a level is lost after
  spending; the UI then shows 0 and unlocking waits until the level is back.
- Unlocking a suggestion costs 1 point. Without points the Unlock button is disabled and says at which level the
  next point comes.
- A skill created by the AI in `newSkills` (a topic actually learned) is free, including a topic that waited in the
  fog (its suggestion leaves the fog, as today).
- An unlock must be distinguishable in history: it records `SKILL_UNLOCKED` (skill key, to_status DISCOVERED,
  name) instead of SKILL_CREATED, plus RELATION_ADDED for the link from its source as today. The History in Manage
  gets a label for the new type. SQLite cannot alter a CHECK constraint, so a migration rebuilds `revision_change` (create new table,
  copy, drop, rename). Unlocks made before that stay recorded as SKILL_CREATED and cost nothing.
- The unlock endpoint moves to the game module (it checks points); the unlock itself stays a `skill` service.

## 7. Vision (fog grows back with level)

- The clear margin around known land grows with the character level: about 60 px at level 1 up to 200 px at level
  10 (constant table / formula in `GameRules`, sent to the frontend in the game state).
- A fog node **inside vision** is readable (name) and can be unlocked. **Outside vision** it shows only `?`, no name,
  and cannot be unlocked yet. With fog nodes 120 px from their source, topics become readable around level 3.

## 8. Constellations

- An area is complete when it has at least 3 skills and every skill is UNDERSTOOD or higher (suggestions ignored).
- On the map: a gold ring and a soft glow on the hub, brighter lines inside the area.
- Lost again when a status goes down.

## 9. Achievements

Computed; the date is the moment in the replay when the condition first became true. Defined in one list in code,
more will be added during development.

| Achievement | Condition |
|---|---|
| First understanding | first skill UNDERSTOOD |
| Hands on | first APPLIED |
| Mastery | first MASTERED |
| Quest hunter | 10 questions resolved |
| Cartographer | 5 areas on the map |
| Pathfinder | first topic unlocked from the fog |
| Constellation | first complete area |
| Chronicler | 10 study sessions with notes |
| Proven | 25 evidence |
| Boss slayer | first boss seal (when bosses exist) |

Locked achievements are shown dimmed with their condition, so it is clear what to aim for.

## 10. Quests (all open questions, backlog item D)

- A "Quests (N)" button in the toolbar opens a window with every open question on the map, grouped by area. Each row:
  skill, question text, reward (+8 XP).
- Clicking a row selects the skill on the map (focus + card).
- **Take quest** copies the AI session context for that skill with the session goal pre-filled with the question.
- A quest is finished only through an AI session (`openQuestionsResolved`), never by a button: no XP for clicks.
- Later: the backlog "pending verification questions" become a second quest kind in the same window.

## 11. Hero panel, journal, chart

- **Hero badge** in a map corner: title, level, XP bar to the next level, talent points. Click opens the hero window.
- **Hero window** sections: summary, achievements, journal, chart.
- **Journal of deeds**: computed feed of game events with dates: level up, rank up, constellation formed / lost,
  topic unlocked, achievement earned. The technical History in Manage stays as it is.
- **Chart**: character XP over time (line, one point per day with changes) and current area ranks (bars). Plain SVG,
  no chart library.

## 12. AI teaches by rank

- MINIBRAIN_CONTEXT v3 adds `player` (level, title) and the focus area rank.
- Session prompt rule: rank 1-2 explain from scratch in simple words with examples; rank 3 connect to other skills
  and give application tasks; rank 4-5 discuss trade-offs, edge cases and alternatives like with a colleague.
- Context assembly (`AiContextQuery`) moves from `skill` to `learning` (preparing a learning session is learning's job),
  because `skill` must not depend on `game`. The HTTP path stays the same.

## 13. Later, together with the AI chat

- **Pathfinder points**: one per 5 character levels, spent on "a new branch": the AI proposes an area root and 3-5 fog
  topics for a subject I name.
- **Boss of an area**: available from area rank 3. A button on the hub copies an exam prompt over the whole area; the
  AI returns `bossResult` in MINIBRAIN_UPDATE; a passed exam is recorded as revision change `BOSS_PASSED` (the only
  stored game fact) and gives the area a seal.

## 14. Architecture

Backend, new module `game` (brief §50 says no module before it is needed; it is needed now):

```text
game/
├── domain/   GameRules: weights, level curve, ranks, titles, vision, achievements, replay. Pure Java, no Spring.
├── query/    GameQuery: loads revision changes in order, runs the replay, returns GameState (+ its records).
└── web/      GameController: GET /api/game; POST /api/game/unlock/{key} (checks points, calls the skill unlock).
```

- Dependencies: `game -> skill`, `game -> revision`; nothing depends on `game` except `learning` (context rank).
- The unlock logic moves out of `SuggestionController` into a `skill` service used by the game controller; the old
  unlock endpoint goes away so points cannot be bypassed.
- `GameState` (read model): player (xp, level, title, xp to next, talent points, vision), areas (key, xp, rank,
  complete), achievements (id, earned at or null), journal (date, kind, subject), xp history (date, xp).

Frontend:

- `api.ts` mirrors `GameState`; one `['game']` query, invalidated with the graph after imports and unlocks.
- New: `HeroBadge`, `HeroDialog` (achievements, journal, chart), `QuestsDialog`.
- `toFlow` / `skillMapParts`: rank label and rings on hubs, constellation glow; fog nodes outside vision without a
  name; `FogOfWar` margin from the game state.
- `SuggestionCard`: cost, points left, disabled state; calls the game unlock endpoint.

## 15. Testing

- `GameRules` unit tests (no Spring): XP per state, level curve edges, evidence cap, constellation rule, a replay
  with a status going down (level lost), achievement dates, talent points after unlocks.
- `GameQuery` / controller tests with an in-memory DB: an import + unlock sequence gives the expected state; unlock
  without points -> 409.
- Frontend: typecheck + lint; live check on a scratch DB copy.

## 16. Slices

| Slice | Content |
|---|---|
| G1 | Module `game`: rules, replay, `GET /api/game`; hero badge; area ranks under hubs |
| G2 | `SKILL_UNLOCKED` migration; unlock costs a point; vision and hidden names in the fog |
| G3 | Quests window + Take quest |
| G4 | Constellations on the map, achievements, journal, chart, hero window |
| G5 | AI teaches by rank: context v3, context assembly moves to `learning` |
| later | Pathfinder points / new branch, bosses (with the AI chat) |
