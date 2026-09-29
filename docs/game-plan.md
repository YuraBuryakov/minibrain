# Game layer G1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** First visible slice of the game: XP, character level, title and area ranks computed from the revision
history, shown in a hero badge and under the area hubs.

**Architecture:** New backend module `game`. `game/domain` holds pure rules and a replay of revision changes (no
Spring, no SQL); `game/query/GameQuery` reads `revision_change` in time order, replays it and returns a `GameState`
read model; `GameController` serves it at `GET /api/game`. The frontend mirrors `GameState`, shows a `HeroBadge`
panel and passes area ranks into the `toFlow` adapter, which puts a rank line under each hub label.

**Tech Stack:** Java 21, Spring Boot, Spring JDBC (`JdbcClient`), SQLite, JUnit 5 + AssertJ + MockMvc;
React + TypeScript + Vite, TanStack Query, React Flow.

**Spec:** [`docs/game-design.md`](game-design.md) (sections 3-5, 11, 14, 16). Slices G2-G5 get their own plan
section when they start.

## Global Constraints

- Only demonstrated understanding gives XP; nothing game-specific is stored (spec §3).
- XP: status DISCOVERED / LEARNING / UNDERSTOOD / APPLIED / MASTERED = 0 / 10 / 30 / 60 / 100; evidence +5, at most
  5 counted per skill; resolved question +8 (spec §4).
- Level `L` starts at `25 * (L - 1)^2` XP (spec §5). Same curve for areas (rank = area level).
- Titles by level: 1-2 STUDENT, 3-5 JOURNEYMAN, 6-9 SCHOLAR, 10-14 ARCHITECT, 15+ MAGISTER.
- Rank names: 1 Novice, 2 Apprentice, 3 Adept, 4 Expert, 5+ Master (RU: Новичок, Ученик, Адепт, Эксперт, Мастер).
- `game/domain` depends on nothing in the project (no Spring, JDBC, JSON). Statuses are plain names (like `revision`).
- G1 talent points = level - 1 (unlock spending arrives in G2).
- No em dashes anywhere (code, comments, UI strings, commits). No `Co-Authored-By` trailer.
- Tests run through the `test-runner` agent. Live checks on a scratch DB copy (`MINIBRAIN_DB_URL`), never the real DB.
- Claude commits only after the owner reviewed the slice; branch `feat-game`.

## File structure

| File | Responsibility |
|---|---|
| `backend/src/main/java/dev/minibrain/game/domain/GameRules.java` | Numbers and pure formulas: XP, levels, titles, area of a key |
| `backend/src/main/java/dev/minibrain/game/domain/GameReplay.java` | Replays revision changes into per-skill progress; XP per skill, area, total |
| `backend/src/main/java/dev/minibrain/game/query/GameState.java` | Read model returned to the UI |
| `backend/src/main/java/dev/minibrain/game/query/GameQuery.java` | SQL over `revision_change` + replay -> `GameState` |
| `backend/src/main/java/dev/minibrain/game/web/GameController.java` | `GET /api/game` |
| `backend/src/test/java/dev/minibrain/game/domain/GameRulesTests.java` | Unit tests for rules and replay |
| `backend/src/test/java/dev/minibrain/game/query/GameQueryTests.java` | Import -> `/api/game` integration test |
| `frontend/src/api.ts` | `GameState` type + `fetchGame()` |
| `frontend/src/HeroBadge.tsx` | Title, level, XP bar, talent points |
| `frontend/src/toFlow.ts` | Options object; rank on hub nodes |
| `frontend/src/skillMapParts.tsx` | Rank line under hub labels |
| `frontend/src/App.tsx` | Game query, hero panel, pass ranks to `toFlow` |
| `frontend/src/i18n.ts`, `frontend/src/skillMap.css` | Strings, styles |

---

### Task 1: Game rules and replay (pure domain)

**Files:**
- Create: `backend/src/main/java/dev/minibrain/game/domain/GameRules.java`
- Create: `backend/src/main/java/dev/minibrain/game/domain/GameReplay.java`
- Test: `backend/src/test/java/dev/minibrain/game/domain/GameRulesTests.java`

**Interfaces:**
- Produces: `GameRules.statusXp(String)`, `GameRules.skillXp(String status, int evidence, int resolved)`,
  `GameRules.levelFor(int xp)`, `GameRules.xpForLevel(int level)`, `GameRules.titleFor(int level)`,
  `GameRules.areaOf(String key)`, enum `GameRules.Title { STUDENT, JOURNEYMAN, SCHOLAR, ARCHITECT, MAGISTER }`;
  `GameReplay.Event(String type, String skillKey, String toStatus)`, `GameReplay.apply(Event)`,
  `GameReplay.totalXp()`, `GameReplay.areaXp(): SortedMap<String, Integer>`.

- [ ] **Step 1: Write the failing tests**

```java
package dev.minibrain.game.domain;

import dev.minibrain.game.domain.GameReplay.Event;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameRulesTests {

    @Test
    void xpComesFromStatusCappedEvidenceAndResolvedQuestions() {
        assertThat(GameRules.skillXp("DISCOVERED", 0, 0)).isZero();
        assertThat(GameRules.skillXp("UNDERSTOOD", 2, 1)).isEqualTo(30 + 10 + 8);
        assertThat(GameRules.skillXp("MASTERED", 9, 0)).isEqualTo(100 + 25); // evidence capped at 5
    }

    @Test
    void levelCurveStartsLevelLAt25TimesLMinusOneSquared() {
        assertThat(GameRules.levelFor(0)).isEqualTo(1);
        assertThat(GameRules.levelFor(24)).isEqualTo(1);
        assertThat(GameRules.levelFor(25)).isEqualTo(2);
        assertThat(GameRules.levelFor(99)).isEqualTo(2);
        assertThat(GameRules.levelFor(100)).isEqualTo(3);
        assertThat(GameRules.xpForLevel(4)).isEqualTo(225);
    }

    @Test
    void titlesFollowLevelBands() {
        assertThat(GameRules.titleFor(2)).isEqualTo(GameRules.Title.STUDENT);
        assertThat(GameRules.titleFor(3)).isEqualTo(GameRules.Title.JOURNEYMAN);
        assertThat(GameRules.titleFor(9)).isEqualTo(GameRules.Title.SCHOLAR);
        assertThat(GameRules.titleFor(10)).isEqualTo(GameRules.Title.ARCHITECT);
        assertThat(GameRules.titleFor(15)).isEqualTo(GameRules.Title.MAGISTER);
    }

    @Test
    void replaySumsSkillsIntoAreasAndLosesXpWhenAStatusGoesDown() {
        var replay = new GameReplay();
        replay.apply(new Event("SKILL_CREATED", "ddd", "LEARNING"));
        replay.apply(new Event("SKILL_CREATED", "ddd.aggregate", "UNDERSTOOD"));
        replay.apply(new Event("EVIDENCE_ADDED", "ddd.aggregate", null));
        replay.apply(new Event("QUESTION_RESOLVED", "ddd.aggregate", null));
        replay.apply(new Event("SKILL_CREATED", "spring.boot", "APPLIED"));
        replay.apply(new Event("RELATION_ADDED", "ddd.aggregate", null)); // ignored

        assertThat(replay.areaXp()).containsEntry("ddd", 10 + 30 + 5 + 8).containsEntry("spring", 60);
        assertThat(replay.totalXp()).isEqualTo(113);

        replay.apply(new Event("SKILL_STATUS_CHANGED", "spring.boot", "LEARNING"));
        assertThat(replay.totalXp()).isEqualTo(63);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (via `test-runner`): `cd backend && mvn -q test -Dtest=GameRulesTests`
Expected: compilation FAIL, `GameRules` / `GameReplay` do not exist.

- [ ] **Step 3: Implement `GameRules`**

```java
package dev.minibrain.game.domain;

/**
 * Game rules (docs/game-design.md §4-5): pure formulas, no Spring, no SQL. Every tuning number lives here.
 * Statuses are plain names ("UNDERSTOOD"), like in the revision history the game is computed from.
 */
public final class GameRules {

    public static final int EVIDENCE_XP = 5;
    public static final int EVIDENCE_CAP = 5; // evidence counted per skill: the status carries the weight
    public static final int QUESTION_XP = 8;
    public static final int LEVEL_STEP = 25;

    public enum Title { STUDENT, JOURNEYMAN, SCHOLAR, ARCHITECT, MAGISTER }

    private GameRules() {
    }

    public static int statusXp(String status) {
        return switch (status == null ? "" : status) {
            case "LEARNING" -> 10;
            case "UNDERSTOOD" -> 30;
            case "APPLIED" -> 60;
            case "MASTERED" -> 100;
            default -> 0; // DISCOVERED or unknown
        };
    }

    public static int skillXp(String status, int evidence, int resolvedQuestions) {
        return statusXp(status) + Math.min(evidence, EVIDENCE_CAP) * EVIDENCE_XP + resolvedQuestions * QUESTION_XP;
    }

    /** Level L starts at 25 * (L - 1)^2 XP: 0, 25, 100, 225, 400, ... */
    public static int xpForLevel(int level) {
        return LEVEL_STEP * (level - 1) * (level - 1);
    }

    public static int levelFor(int xp) {
        int level = 1;
        while (xpForLevel(level + 1) <= xp) level++;
        return level;
    }

    public static Title titleFor(int level) {
        if (level >= 15) return Title.MAGISTER;
        if (level >= 10) return Title.ARCHITECT;
        if (level >= 6) return Title.SCHOLAR;
        if (level >= 3) return Title.JOURNEYMAN;
        return Title.STUDENT;
    }

    /** "ddd.aggregate" -> "ddd"; the hub skill "ddd" is its own area. Same rule as the map layout. */
    public static String areaOf(String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }
}
```

- [ ] **Step 4: Implement `GameReplay`**

```java
package dev.minibrain.game.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Replays knowledge history (revision changes, oldest first) into per-skill progress (docs/game-design.md §3).
 * Only what gives XP is tracked; every other change type is ignored.
 */
public final class GameReplay {

    /** One revision change, as the game sees it. {@code toStatus} only for creation and status changes. */
    public record Event(String type, String skillKey, String toStatus) {
    }

    private static final class Progress {
        String status = "DISCOVERED";
        int evidence;
        int resolved;

        int xp() {
            return GameRules.skillXp(status, evidence, resolved);
        }
    }

    private final Map<String, Progress> skills = new HashMap<>();

    public void apply(Event e) {
        if (e.skillKey() == null) return;
        switch (e.type()) {
            case "SKILL_CREATED", "SKILL_STATUS_CHANGED" -> {
                if (e.toStatus() != null) progress(e.skillKey()).status = e.toStatus();
            }
            case "EVIDENCE_ADDED" -> progress(e.skillKey()).evidence++;
            case "QUESTION_RESOLVED" -> progress(e.skillKey()).resolved++;
            default -> { // relations, translations, notes, added questions: no XP
            }
        }
    }

    public int totalXp() {
        return skills.values().stream().mapToInt(Progress::xp).sum();
    }

    /** Area key -> XP, sorted by area key. */
    public SortedMap<String, Integer> areaXp() {
        var areas = new TreeMap<String, Integer>();
        skills.forEach((key, p) -> areas.merge(GameRules.areaOf(key), p.xp(), Integer::sum));
        return areas;
    }

    // A change for a skill never seen before (should not happen) still counts, starting from DISCOVERED.
    private Progress progress(String key) {
        return skills.computeIfAbsent(key, k -> new Progress());
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run (via `test-runner`): `cd backend && mvn -q test -Dtest=GameRulesTests`
Expected: 4 tests PASS.

---

### Task 2: GameState read model and `GET /api/game`

**Files:**
- Create: `backend/src/main/java/dev/minibrain/game/query/GameState.java`
- Create: `backend/src/main/java/dev/minibrain/game/query/GameQuery.java`
- Create: `backend/src/main/java/dev/minibrain/game/web/GameController.java`
- Test: `backend/src/test/java/dev/minibrain/game/query/GameQueryTests.java`

**Interfaces:**
- Consumes: Task 1 (`GameReplay`, `GameRules`).
- Produces: `GET /api/game` -> `{ "player": { "xp", "level", "levelStartXp", "nextLevelXp", "title", "talentPoints" },
  "areas": [ { "key", "xp", "rank" } ] }`; `GameQuery.get(): GameState`.

- [ ] **Step 1: Write the failing test**

The in-memory test DB is shared between test classes, so the test compares before / after and uses its own area
prefix `gq`.

```java
package dev.minibrain.game.query;

import dev.minibrain.importing.application.ImportApplier;
import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreviewer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class GameQueryTests {

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    ImportApplier applier;

    @Autowired
    GameQuery game;

    @Autowired
    MockMvc mvc;

    @Test
    void anImportRaisesXpOfItsAreaAndTheCharacter() throws Exception {
        int before = game.get().player().xp();
        String update = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "newSkills": [ { "key": "gq", "name": "Game", "status": "UNDERSTOOD" },
                                 { "key": "gq.xp", "name": "Xp", "status": "LEARNING" } ],
                  "changes": [ { "skill": "gq.xp", "evidenceAdded": ["Explained it"] } ] }
                """;
        applier.apply(update, previewer.preview(update).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet()));

        GameState after = game.get();
        assertThat(after.player().xp() - before).isEqualTo(30 + 10 + 5);
        assertThat(after.areas()).contains(new GameState.Area("gq", 45, 2));
        assertThat(after.player().nextLevelXp()).isGreaterThan(after.player().xp());
        assertThat(after.player().talentPoints()).isEqualTo(after.player().level() - 1);

        mvc.perform(get("/api/game"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areas[?(@.key == 'gq')].rank").value(2))
                .andExpect(jsonPath("$.player.title").isString());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run (via `test-runner`): `cd backend && mvn -q test -Dtest=GameQueryTests`
Expected: compilation FAIL, `GameQuery` / `GameState` do not exist.

- [ ] **Step 3: Implement `GameState`**

```java
package dev.minibrain.game.query;

import dev.minibrain.game.domain.GameRules;

import java.util.List;

/**
 * The game as the UI shows it (docs/game-design.md §5, §11), computed from the revision history on every request.
 * Not a durable contract: nothing is exported or stored.
 */
public record GameState(Player player, List<Area> areas) {

    /** {@code levelStartXp} / {@code nextLevelXp}: XP where the current and the next level start (the XP bar). */
    public record Player(int xp, int level, int levelStartXp, int nextLevelXp, GameRules.Title title, int talentPoints) {
    }

    /** {@code rank} = the area's level on the same curve. */
    public record Area(String key, int xp, int rank) {
    }
}
```

- [ ] **Step 4: Implement `GameQuery`**

```java
package dev.minibrain.game.query;

import dev.minibrain.game.domain.GameReplay;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import static dev.minibrain.game.domain.GameRules.levelFor;
import static dev.minibrain.game.domain.GameRules.titleFor;
import static dev.minibrain.game.domain.GameRules.xpForLevel;

/**
 * Replays every revision change, oldest first (occurred_at is fixed-width UTC text, so text order = time order).
 * ponytail: full replay per request; fine for thousands of changes, cache by the newest revision id if it gets slow.
 */
@Component
public class GameQuery {

    private final JdbcClient jdbc;

    public GameQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public GameState get() {
        var replay = new GameReplay();
        jdbc.sql("SELECT type, skill_key, to_status FROM revision_change ORDER BY occurred_at, id")
                .query((rs, rowNum) -> {
                    replay.apply(new GameReplay.Event(rs.getString("type"), rs.getString("skill_key"), rs.getString("to_status")));
                    return null;
                })
                .list();

        int xp = replay.totalXp();
        int level = levelFor(xp);
        var player = new GameState.Player(xp, level, xpForLevel(level), xpForLevel(level + 1), titleFor(level), level - 1);
        var areas = replay.areaXp().entrySet().stream()
                .map(e -> new GameState.Area(e.getKey(), e.getValue(), levelFor(e.getValue())))
                .toList();
        return new GameState(player, areas);
    }
}
```

- [ ] **Step 5: Implement `GameController`**

```java
package dev.minibrain.game.web;

import dev.minibrain.game.query.GameQuery;
import dev.minibrain.game.query.GameState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GameController {

    private final GameQuery game;

    public GameController(GameQuery game) {
        this.game = game;
    }

    @GetMapping("/api/game")
    public GameState game() {
        return game.get();
    }
}
```

- [ ] **Step 6: Run the whole backend suite**

Run (via `test-runner`): `cd backend && mvn -q test`
Expected: all tests PASS (40 before + 4 `GameRulesTests` + 1 `GameQueryTests` = 45).

---

### Task 3: Hero badge and area ranks on the map

**Files:**
- Modify: `frontend/src/api.ts` (after `fetchGraph`)
- Create: `frontend/src/HeroBadge.tsx`
- Modify: `frontend/src/toFlow.ts` (signature -> options object; `rank` in hub / sigil data)
- Modify: `frontend/src/skillMapParts.tsx` (`SkillNodeData`, `AreaNodeData`, rank line)
- Modify: `frontend/src/App.tsx` (game query, hero panel, `toFlow` call)
- Modify: `frontend/src/i18n.ts`, `frontend/src/skillMap.css`

**Interfaces:**
- Consumes: `GET /api/game` from Task 2.
- Produces: `fetchGame(): Promise<GameState>`; `toFlow(graph, view: FlowView)` with
  `FlowView = { selectedKey: string | null; lang: Lang; statuses?: ReadonlySet<SkillStatus>; pins?: ReadonlyMap<string, Point>; ranks?: ReadonlyMap<string, number> }`.

- [ ] **Step 1: `api.ts`: mirror `GameState`**

```ts
// Mirrors dev.minibrain.game.query.GameState: the game, computed from the revision history on every request.
export type Title = 'STUDENT' | 'JOURNEYMAN' | 'SCHOLAR' | 'ARCHITECT' | 'MAGISTER'
export type GameState = {
  player: { xp: number; level: number; levelStartXp: number; nextLevelXp: number; title: Title; talentPoints: number }
  areas: { key: string; xp: number; rank: number }[]
}

export async function fetchGame(): Promise<GameState> {
  const response = await fetch('/api/game')
  if (!response.ok) throw new Error(`GET /api/game failed: ${response.status}`)
  return response.json()
}
```

- [ ] **Step 2: `i18n.ts`: strings (EN block, then the same keys in the RU block)**

EN:
```ts
  'hero.label': 'Hero',
  'hero.level': 'Level {level}',
  'hero.xp': '{xp} / {next} XP',
  'hero.points': 'Talent points: {n}',
  'hero.title.STUDENT': 'Student',
  'hero.title.JOURNEYMAN': 'Journeyman',
  'hero.title.SCHOLAR': 'Scholar',
  'hero.title.ARCHITECT': 'Architect',
  'hero.title.MAGISTER': 'Magister',
  'rank.1': 'Novice',
  'rank.2': 'Apprentice',
  'rank.3': 'Adept',
  'rank.4': 'Expert',
  'rank.5': 'Master',
  'rank.line': 'Rank {n} · {name}',
```
RU:
```ts
  'hero.label': 'Герой',
  'hero.level': 'Уровень {level}',
  'hero.xp': '{xp} / {next} опыта',
  'hero.points': 'Очки таланта: {n}',
  'hero.title.STUDENT': 'Ученик',
  'hero.title.JOURNEYMAN': 'Подмастерье',
  'hero.title.SCHOLAR': 'Знаток',
  'hero.title.ARCHITECT': 'Архитектор',
  'hero.title.MAGISTER': 'Магистр',
  'rank.1': 'Новичок',
  'rank.2': 'Ученик',
  'rank.3': 'Адепт',
  'rank.4': 'Эксперт',
  'rank.5': 'Мастер',
  'rank.line': 'Ранг {n} · {name}',
```

- [ ] **Step 3: `HeroBadge.tsx`**

```tsx
import { useQuery } from '@tanstack/react-query'
import { fetchGame } from './api'
import { useT } from './i18n'

// Hero badge (docs/game-design.md §11): title, level, XP bar to the next level, talent points.
// The game is recomputed by the backend; imports and unlocks invalidate every query, so it refreshes itself.

export function HeroBadge() {
  const t = useT()
  const game = useQuery({ queryKey: ['game'], queryFn: fetchGame })
  if (!game.data) return null
  const p = game.data.player
  const progress = (p.xp - p.levelStartXp) / (p.nextLevelXp - p.levelStartXp)

  return (
    <section className="hero" aria-label={t('hero.label')}>
      <p className="hero__title">{t(`hero.title.${p.title}`)}</p>
      <p className="hero__level">{t('hero.level', { level: p.level })}</p>
      <div className="hero__bar" role="progressbar" aria-valuemin={p.levelStartXp} aria-valuemax={p.nextLevelXp} aria-valuenow={p.xp}>
        <span style={{ width: `${Math.round(progress * 100)}%` }} />
      </div>
      <p className="hero__xp">{t('hero.xp', { xp: p.xp, next: p.nextLevelXp })}</p>
      {p.talentPoints > 0 && <p className="hero__points">{t('hero.points', { n: p.talentPoints })}</p>}
    </section>
  )
}
```

- [ ] **Step 4: `skillMapParts.tsx`: rank line under hub labels**

Change the data types and both hub renderers:

```tsx
export type SkillNodeData = { name: string; status: SkillStatus; hub: boolean; rank?: number }
export type AreaNodeData = { name: string; rank?: number }

// "Rank 3 · Adept" under an area hub; ranks above 5 keep the Master name.
function RankLine({ rank }: { rank?: number }) {
  const t = useT()
  if (!rank) return null
  const name = t(`rank.${Math.min(rank, 5) as 1 | 2 | 3 | 4 | 5}`)
  return <small className="orb__rank">{t('rank.line', { n: rank, name })}</small>
}
```

In `SkillOrb` the label becomes:
```tsx
      <span className={data.hub ? 'orb__label orb__label--area' : 'orb__label'}>
        {data.name}
        {data.hub && <RankLine rank={data.rank} />}
      </span>
```
In `AreaSigil`:
```tsx
      <span className="orb__label orb__label--area">
        {data.name}
        <RankLine rank={data.rank} />
      </span>
```
Add `import { useT } from './i18n'`.

- [ ] **Step 5: `toFlow.ts`: options object and ranks**

Replace the signature (the positional optional parameters become one `view` object):

```ts
export type FlowView = {
  selectedKey: string | null
  lang: Lang
  statuses?: ReadonlySet<SkillStatus>
  pins?: ReadonlyMap<string, Point>
  ranks?: ReadonlyMap<string, number> // area key -> rank (game)
}

export function toFlow(graph: KnowledgeGraph, view: FlowView): { nodes: Node[]; edges: Edge[] } {
  const { selectedKey, lang, statuses = new Set(), pins = new Map(), ranks = new Map() } = view
```
Sigil node data: `data: { name: titleCase(area.name), rank: ranks.get(area.name) }`.
Skill node data: `data: { name: pick(lang, skill.name, skill.nameRu), status: skill.status, hub, rank: hub ? ranks.get(skill.key) : undefined }`
(the hub skill's key is its area key).

- [ ] **Step 6: `App.tsx`: game query, hero panel, new `toFlow` call**

```tsx
import { fetchGame, fetchGraph, fetchPositions, pinPosition, type NodePosition, type RelationType, type SkillStatus } from './api'
import { HeroBadge } from './HeroBadge'
```
Inside `SkillMap`, next to the other queries:
```tsx
  const game = useQuery({ queryKey: ['game'], queryFn: fetchGame })
  const ranks = useMemo(() => new Map((game.data?.areas ?? []).map((a) => [a.key, a.rank])), [game.data])
  const flow = useMemo(
    () => (graph.data ? toFlow(graph.data, { selectedKey: selected, lang, statuses, pins, ranks }) : null),
    [graph.data, selected, lang, statuses, pins, ranks],
  )
```
In the JSX, after the top-left toolbar `Panel`:
```tsx
        <Panel position="top-right">
          <HeroBadge />
        </Panel>
```

- [ ] **Step 7: `skillMap.css`: hero and rank styles (before `/* ---- legend ---- */`)**

```css
/* ---- hero badge (game) ---- */

.hero {
  min-width: 200px;
  padding: 10px 14px;
  border: 1px solid var(--bronze-dim);
  border-radius: 4px;
  background: var(--night);
  color: var(--bone);
}

.hero p {
  margin: 0;
}

.hero__title {
  font-family: 'IM Fell English SC', Georgia, serif;
  font-size: 22px;
  color: var(--parchment);
}

.hero__level,
.hero__xp,
.hero__points {
  font-size: 15px;
  color: var(--bone-dim);
}

.hero__points {
  color: var(--gilded);
}

.hero__bar {
  height: 6px;
  margin: 6px 0 4px;
  border-radius: 3px;
  background: var(--abyss);
  overflow: hidden;
}

.hero__bar span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--bronze), var(--gilded));
}

.orb__rank {
  display: block;
  margin-top: 2px;
  font-family: Georgia, serif;
  font-size: 13px;
  color: var(--bone-dim);
}
```

- [ ] **Step 8: Typecheck, lint, build**

Run (via `test-runner`): `cd frontend && npm run build && npm run lint`
Expected: build succeeds; lint shows only the existing `only-export-components` warnings.

- [ ] **Step 9: Live check on a scratch DB copy**

Copy `data/minibrain.db` to the scratchpad, start with `MINIBRAIN_DB_URL` pointing to it, open the map: the hero
badge shows a title, level and XP bar; every hub shows "Rank N · Name". `GET /api/game` returns the same numbers.

- [ ] **Step 10: Docs, then owner review**

Update `docs/roadmap.md` (a "Game layer" section with G1 done, G2-G5 open) and `docs/decisions.md` (G1 entry:
module `game`, computed from revisions, rules in `GameRules`). Stop for the owner's review; commit only after OK:

```bash
git add backend/src/main/java/dev/minibrain/game backend/src/test/java/dev/minibrain/game frontend/src docs
git commit -m "Game G1: XP, levels, titles and area ranks computed from revisions; hero badge; ranks under hubs"
```

---

# Game layer G2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unlocking a topic from the fog costs a talent point, and the fog hides topics beyond the character's vision.

**Architecture:** Migration V10 rebuilds `revision_change` so its CHECK allows `SKILL_UNLOCKED`. The unlock use case
moves from `SuggestionController` into `skill/application/SuggestionUnlocker` (records `SKILL_UNLOCKED` +
`RELATION_ADDED`). `GameController` gets `POST /api/game/unlock/{key}`: it checks talent points, then calls the
unlocker. `GameReplay` counts unlocks; `GameState.Player` gains `vision`. The frontend uses `vision` as the fog margin
and hides names of fog topics beyond it.

**Spec:** [`docs/game-design.md`](game-design.md) §6-7.

## Global Constraints

- Everything from the G1 constraints above still applies.
- Talent points = `max(0, (level - 1) - unlocks)`; unlock without a point -> HTTP 409.
- Vision = `min(200, 60 + 30 * (level - 1))` px; fog nodes sit `FOG_DISTANCE` = 120 px from their anchor, so a topic
  is readable when `vision >= 120` (level 3+). The vision gate is UI-only.
- A skill the AI creates in `newSkills` stays free (still `SKILL_CREATED`).
- Back up `data/minibrain.db` into `backups/` before V10 runs against it.

## File structure

| File | Change |
|---|---|
| `backend/src/main/resources/db/migration/V10__add_skill_unlocked.sql` | Rebuild `revision_change` with the new CHECK |
| `backend/src/main/java/dev/minibrain/revision/domain/RevisionChange.java` | `SKILL_UNLOCKED` + `skillUnlocked(...)` |
| `backend/src/main/java/dev/minibrain/skill/application/SuggestionUnlocker.java` | New: the unlock use case |
| `backend/src/main/java/dev/minibrain/skill/web/SuggestionController.java` | Loses `/unlock` (dismiss stays) |
| `backend/src/main/java/dev/minibrain/game/domain/GameRules.java` | `vision(level)` |
| `backend/src/main/java/dev/minibrain/game/domain/GameReplay.java` | `SKILL_UNLOCKED` + `unlocks()` |
| `backend/src/main/java/dev/minibrain/game/query/GameState.java`, `GameQuery.java` | `vision`, points after spending |
| `backend/src/main/java/dev/minibrain/game/web/GameController.java` | `POST /api/game/unlock/{key}` |
| tests | `GameRulesTests`, `SuggestionControllerTests` (moved unlock), new `GameUnlockTests` (409) |
| `frontend/src/api.ts`, `SuggestionCard.tsx`, `layout.ts`, `toFlow.ts`, `skillMapParts.tsx`, `App.tsx`, `SkillSearch.tsx`, `i18n.ts` | Cost, disabled state, vision |

---

### Task 1: `SKILL_UNLOCKED` in the history (migration + domain)

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__add_skill_unlocked.sql`
- Modify: `backend/src/main/java/dev/minibrain/revision/domain/RevisionChange.java`

**Interfaces:**
- Produces: `RevisionChange.Type.SKILL_UNLOCKED`, `RevisionChange.skillUnlocked(String key, String status, String name)`.

- [ ] **Step 1: Migration**

```sql
-- Game G2: SKILL_UNLOCKED = a skill opened from the fog with a talent point. SQLite cannot change a CHECK
-- constraint, so the table is rebuilt: new table, copy every row (ids kept), drop, rename, indexes again.
CREATE TABLE revision_change_new (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    revision_id   INTEGER NOT NULL REFERENCES revision (id),
    type          TEXT    NOT NULL CHECK (type IN ('SKILL_CREATED', 'SKILL_STATUS_CHANGED', 'EVIDENCE_ADDED',
                          'QUESTION_ADDED', 'QUESTION_RESOLVED', 'RELATION_ADDED', 'TRANSLATION_ADDED', 'NOTES_SAVED',
                          'SKILL_UNLOCKED')),
    skill_key     TEXT,
    from_status   TEXT,
    to_status     TEXT,
    text          TEXT,
    related_key   TEXT,
    relation_type TEXT,
    occurred_at   TEXT    NOT NULL
);

INSERT INTO revision_change_new (id, revision_id, type, skill_key, from_status, to_status, text, related_key,
                                 relation_type, occurred_at)
SELECT id, revision_id, type, skill_key, from_status, to_status, text, related_key, relation_type, occurred_at
FROM revision_change;

DROP TABLE revision_change;
ALTER TABLE revision_change_new RENAME TO revision_change;

CREATE INDEX revision_change_revision_id ON revision_change (revision_id);
CREATE INDEX revision_change_skill_key ON revision_change (skill_key);
```

- [ ] **Step 2: `RevisionChange`**: add `SKILL_UNLOCKED` to the `Type` enum (last) and the factory:

```java
    /** A skill opened from the fog with a talent point (game). Carries the same fields as skillCreated. */
    public static RevisionChange skillUnlocked(String key, String status, String name) {
        return new RevisionChange(Type.SKILL_UNLOCKED, key, null, status, name, null, null, Instant.now());
    }
```

### Task 2: Unlock use case moves to `skill/application`, the game charges a point

**Files:**
- Create: `backend/src/main/java/dev/minibrain/skill/application/SuggestionUnlocker.java`
- Modify: `backend/src/main/java/dev/minibrain/skill/web/SuggestionController.java` (remove `unlock`, keep `dismiss`)
- Modify: `GameRules.java`, `GameReplay.java`, `GameState.java`, `GameQuery.java`, `GameController.java`
- Test: `GameRulesTests.java`, `SuggestionControllerTests.java`, new `backend/src/test/java/dev/minibrain/game/web/GameUnlockTests.java`

**Interfaces:**
- Produces: `SuggestionUnlocker.unlock(String key): Optional<Skill>` (empty = no open suggestion);
  `GameRules.vision(int level): int`; `GameReplay.unlocks(): int`; `GameState.Player(..., int talentPoints, int vision)`;
  `POST /api/game/unlock/{key}` -> 201 skill / 404 no suggestion / 409 no talent point / 409 key taken.

- [ ] **Step 1: Tests first**

`GameRulesTests`, add:
```java
    @Test
    void visionGrowsThirtyPerLevelUpTo200() {
        assertThat(GameRules.vision(1)).isEqualTo(60);
        assertThat(GameRules.vision(3)).isEqualTo(120);
        assertThat(GameRules.vision(6)).isEqualTo(200);
        assertThat(GameRules.vision(12)).isEqualTo(200);
    }

    @Test
    void anUnlockCreatesTheSkillAndIsCounted() {
        var replay = new GameReplay();
        replay.apply(new Event("SKILL_UNLOCKED", "ddd.value-object", "DISCOVERED"));
        replay.apply(new Event("SKILL_STATUS_CHANGED", "ddd.value-object", "LEARNING"));
        assertThat(replay.unlocks()).isEqualTo(1);
        assertThat(replay.totalXp()).isEqualTo(10);
    }
```
`SuggestionControllerTests`: the update gets enough XP for points (a MASTERED skill = 100 XP = level 3 alone), and
unlock moves to the game endpoint:
```java
              "newSkills": [ { "key": "sug.base", "name": "Base", "status": "LEARNING" },
                             { "key": "sug.xp", "name": "Xp", "status": "MASTERED" } ],
```
```java
        mvc.perform(post("/api/game/unlock/sug.next")).andExpect(status().isCreated());
        // ...
        mvc.perform(post("/api/game/unlock/sug.meh")).andExpect(status().isNotFound());
```
and the revision check becomes "SKILL_UNLOCKED + RELATION_ADDED":
```java
        assertThat(revisions.findRecent(1).getFirst().changes()).extracting(RevisionChange::type)
                .containsExactly(RevisionChange.Type.SKILL_UNLOCKED, RevisionChange.Type.RELATION_ADDED);
```
New `GameUnlockTests` (own context with a mocked `GameQuery`, so the point balance is known):
```java
package dev.minibrain.game.web;

import dev.minibrain.game.domain.GameRules;
import dev.minibrain.game.query.GameQuery;
import dev.minibrain.game.query.GameState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class GameUnlockTests {

    @MockitoBean
    GameQuery game;

    @Autowired
    MockMvc mvc;

    @Test
    void unlockingWithoutATalentPointIsRefused() throws Exception {
        when(game.get()).thenReturn(new GameState(
                new GameState.Player(0, 1, 0, 25, GameRules.Title.STUDENT, 0, 60), List.of()));

        mvc.perform(post("/api/game/unlock/any.topic")).andExpect(status().isConflict());
    }
}
```

- [ ] **Step 2: `SuggestionUnlocker`** (logic moved from `SuggestionController.unlock`)

```java
package dev.minibrain.skill.application;

import dev.minibrain.revision.domain.RevisionChange;
import dev.minibrain.revision.persistence.RevisionRepository;
import dev.minibrain.revision.persistence.RevisionRepository.Source;
import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.persistence.SkillRelationRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import dev.minibrain.skill.persistence.SuggestedSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Unlock (brief §14): an open suggestion becomes a DISCOVERED skill (its reason becomes the description) and, when
 * its source exists, "source LEADS_TO new skill". History: SKILL_UNLOCKED (+ RELATION_ADDED). Who may unlock (talent
 * points) is the game's decision, not this service's. A skill with the same key -> DuplicateKeyException (409).
 */
@Service
public class SuggestionUnlocker {

    private final SuggestedSkillRepository suggestions;
    private final SkillRepository skills;
    private final SkillRelationRepository relations;
    private final RevisionRepository revisions;

    public SuggestionUnlocker(SuggestedSkillRepository suggestions, SkillRepository skills,
                              SkillRelationRepository relations, RevisionRepository revisions) {
        this.suggestions = suggestions;
        this.skills = skills;
        this.relations = relations;
        this.revisions = revisions;
    }

    /** Empty when there is no open suggestion with this key. */
    @Transactional
    public Optional<Skill> unlock(String key) {
        return suggestions.findOpen(key).map(s -> {
            Skill skill = skills.create(s.key(), s.name(), s.nameRu(), s.reason(), s.reasonRu(), SkillStatus.DISCOVERED);
            List<RevisionChange> history = new ArrayList<>(List.of(RevisionChange.skillUnlocked(key, SkillStatus.DISCOVERED.name(), s.name())));
            if (s.sourceSkill() != null) {
                skills.findByKey(s.sourceSkill()).ifPresent(source -> {
                    relations.add(source.id(), skill.id(), RelationType.LEADS_TO);
                    history.add(RevisionChange.relationAdded(source.key(), RelationType.LEADS_TO.name(), key));
                });
            }
            suggestions.delete(key);
            revisions.record(Source.MANUAL, null, history);
            return skill;
        });
    }
}
```
`SuggestionController`: delete the `unlock` method and the fields / imports only it used; update its class comment
to "Dismissing suggested skills; unlocking costs a talent point and lives in the game module".

- [ ] **Step 3: Game rules, replay, state, query, controller**

`GameRules`:
```java
    /** Clear margin (px) around known land in the fog: 60 at level 1, +30 per level, at most 200 (spec §7). */
    public static int vision(int level) {
        return Math.min(200, 60 + 30 * (level - 1));
    }
```
`GameReplay`: `SKILL_UNLOCKED` joins the `SKILL_CREATED` / `SKILL_STATUS_CHANGED` case, is counted in a field
`unlocks`, exposed as `public int unlocks()` ("talent points spent so far").
`GameState.Player` gains `int vision` as the last component (clear fog margin in px).
`GameQuery`: talent points `Math.max(0, level - 1 - replay.unlocks())`, and `vision(level)` as the last argument.
`GameController` (constructor gains `SuggestionUnlocker unlocker`):
```java
    /** Spends a talent point to open a topic from the fog (spec §6). */
    @PostMapping("/api/game/unlock/{key}")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Skill unlock(@PathVariable String key) {
        if (game.get().player().talentPoints() < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no talent point: reach the next level");
        }
        return unlocker.unlock(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no open suggestion: " + key));
    }
```

- [ ] **Step 4: Run the backend suite** (via `test-runner`): `cd backend && mvn -q test`, all PASS.

### Task 3: Frontend: cost, disabled unlock, vision

**Files:** `frontend/src/api.ts`, `layout.ts`, `toFlow.ts`, `skillMapParts.tsx`, `App.tsx`, `SkillSearch.tsx`,
`SuggestionCard.tsx`, `i18n.ts`.

- [ ] **Step 1: `api.ts`**: `player` gains `vision: number`; unlock goes to the game endpoint, 409 becomes a known error:
```ts
export async function unlockSuggestion(key: string): Promise<void> {
  const response = await fetch(`/api/game/unlock/${encodeURIComponent(key)}`, { method: 'POST' })
  if (response.status === 409) throw new Error('409')
  if (!response.ok) throw new Error(`Unlock ${key} failed: ${response.status}`)
}
```
- [ ] **Step 2: `layout.ts`**: export `FOG_DISTANCE`.
- [ ] **Step 3: `toFlow.ts`**: `FlowView` gains `vision?: number` (default `Infinity` = everything readable); fog node data
  gets `hidden: vision < FOG_DISTANCE`.
- [ ] **Step 4: `skillMapParts.tsx`**: `FogNodeData = { name: string; hidden: boolean }`; `FogOrb` renders its label only
  when not hidden and adds class `orb--fog-far` (dimmer) when hidden.
- [ ] **Step 5: `App.tsx`**: pass `vision` to `toFlow`, `margin={vision}` to `FogOfWar` (`FogOfWar` takes `margin?: number`,
  default 90 as today), `hideFog` to `SkillSearch`, and `points` / `hidden` to `SuggestionCard`.
- [ ] **Step 6: `SkillSearch.tsx`**: prop `hideFog: boolean`; when true, suggestions are left out of the matches (a hidden
  topic must not leak its name through search).
- [ ] **Step 7: `SuggestionCard.tsx`**: props `points: number` and `hidden: boolean`. Hidden: title `?`, text `fog.hidden`,
  no reason, no unlock. Visible: button `fog.unlockCost`, disabled when `points < 1` with `fog.noPoints`; a 409 shows
  `fog.noPoints`.
- [ ] **Step 8: `i18n.ts`**: `history.SKILL_UNLOCKED` (Unlocked from the fog / Открыт из тумана), `fog.hidden` (Too far in
  the fog to make out. Grow a level to see further. / Слишком далеко в тумане. Подними уровень, чтобы видеть дальше.),
  `fog.unlockCost` (Unlock (1 talent point) / Открыть (1 очко таланта)), `fog.noPoints` (No talent points. The next one
  comes with the next level. / Нет очков таланта. Следующее даст новый уровень.).
- [ ] **Step 9: Typecheck + lint + build** (via `test-runner`).
- [ ] **Step 10: Live check on a scratch DB copy**: V10 applies; unlock spends a point (badge 5 -> 4); History shows
  "Открыт из тумана".
- [ ] **Step 11: Docs** (roadmap G2 done, decisions entry), owner review, then commit.


---

# Game layer G3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A "Quests (N)" window lists every open question on the map, grouped by area; a row selects its skill,
"Take quest" copies the AI session context with the goal = the question.

**Architecture:** New read model `game/query/QuestsQuery` (one SQL over `open_question JOIN skill`, area via
`GameRules.areaOf`) served at `GET /api/quests`. The frontend mirrors it, extracts the clipboard logic of
`StudyWithAi` into `copyAiSession(key, goal)` (shared by the card and the quests window) and adds `QuestsDialog`
with a toolbar button.

**Spec:** [`docs/game-design.md`](game-design.md) §10.

## Global Constraints

- Everything from the G1 / G2 constraints above still applies.
- A quest is finished only through an AI session (`openQuestionsResolved`), never by a button: no XP for clicks.
- Nothing is stored: the quest list is the open questions, read on every request.
- Order: area, then skill key, then question id (oldest first).
- Reward shown per row = `GameRules.QUESTION_XP` (+8), sent by the backend so the number lives in one place.
- Area label on the frontend: the hub skill's name if a skill with key = area exists, else the area key.

## File structure

| File | Change |
|---|---|
| `backend/src/main/java/dev/minibrain/game/query/Quest.java` | New: one row of the read model |
| `backend/src/main/java/dev/minibrain/game/query/QuestsQuery.java` | New: SQL over open questions |
| `backend/src/main/java/dev/minibrain/game/web/GameController.java` | `GET /api/quests` |
| `backend/src/test/java/dev/minibrain/game/query/QuestsQueryTests.java` | New: import -> `/api/quests` |
| `frontend/src/api.ts` | `Quest` type + `fetchQuests()` |
| `frontend/src/aiSession.ts` | New: `copyAiSession(key, goal)` (moved out of `SkillCard`) |
| `frontend/src/SkillCard.tsx` | `StudyWithAi` uses `copyAiSession` |
| `frontend/src/QuestsDialog.tsx` | New: button + window |
| `frontend/src/App.tsx` | Button in the toolbar, selection callback |
| `frontend/src/i18n.ts`, `frontend/src/skillMap.css` | Strings, styles |

---

### Task 1: `GET /api/quests` read model

**Files:**
- Create: `backend/src/main/java/dev/minibrain/game/query/Quest.java`
- Create: `backend/src/main/java/dev/minibrain/game/query/QuestsQuery.java`
- Modify: `backend/src/main/java/dev/minibrain/game/web/GameController.java`
- Test: `backend/src/test/java/dev/minibrain/game/query/QuestsQueryTests.java`

**Interfaces:**
- Produces: `record Quest(String area, String skillKey, String skillName, String skillNameRu, String question,
  String questionRu, int xp)`, `QuestsQuery.all(): List<Quest>`, `GET /api/quests` -> `Quest[]`.

- [ ] **Step 1: Write the failing test** (own key prefix `qq`, shared in-memory DB: filter by prefix)

```java
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class QuestsQueryTests {

    @Autowired ImportPreviewer previewer;
    @Autowired ImportApplier applier;
    @Autowired QuestsQuery quests;
    @Autowired MockMvc mvc;

    @Test
    void listsOnlyOpenQuestionsGroupedByAreaThenSkill() throws Exception {
        apply("""
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "newSkills": [ { "key": "qq", "name": "Quests", "status": "LEARNING" },
                                 { "key": "qq.b", "name": "Bravo", "status": "LEARNING" },
                                 { "key": "qq.a", "name": "Alpha", "status": "LEARNING" } ],
                  "changes": [ { "skill": "qq.b", "openQuestionsAdded": ["Why B?"] },
                               { "skill": "qq.a", "openQuestionsAdded": ["Why A?", "Done A?"] } ] }
                """);
        apply("""
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "changes": [ { "skill": "qq.a", "openQuestionsResolved": ["Done A?"] } ] }
                """);

        var mine = quests.all().stream().filter(q -> q.area().equals("qq")).toList();
        assertThat(mine).extracting(Quest::skillKey, Quest::question)
                .containsExactly(tuple("qq.a", "Why A?"), tuple("qq.b", "Why B?"));
        assertThat(mine).allMatch(q -> q.xp() == GameRules.QUESTION_XP);

        mvc.perform(get("/api/quests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.question == 'Why B?')].skillName").value("Bravo"));
    }

    private void apply(String update) {
        applier.apply(update, previewer.preview(update).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet()));
    }
}
```

- [ ] **Step 2: Run it, expect compilation FAIL** (via `test-runner`: `cd backend && mvn -q test -Dtest=QuestsQueryTests`)

- [ ] **Step 3: Implement**

```java
/** One open question seen as a quest (docs/game-design.md §10). *Ru: null = use English. */
public record Quest(String area, String skillKey, String skillName, String skillNameRu,
                    String question, String questionRu, int xp) {
}
```

```java
/** Every open question on the map as a quest, ordered by area, skill, then oldest first. */
@Component
public class QuestsQuery {

    private final JdbcClient jdbc;

    public QuestsQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Quest> all() {
        return jdbc.sql("""
                        SELECT s.key, s.name, s.name_ru, q.text, q.text_ru
                        FROM open_question q JOIN skill s ON s.id = q.skill_id
                        WHERE q.resolved_at IS NULL
                        ORDER BY s.key, q.id
                        """)
                .query((rs, rowNum) -> new Quest(areaOf(rs.getString("key")), rs.getString("key"),
                        rs.getString("name"), rs.getString("name_ru"), rs.getString("text"), rs.getString("text_ru"),
                        QUESTION_XP))
                .list()
                .stream()
                .sorted(Comparator.comparing(Quest::area)) // stable: keeps skill / id order inside an area
                .toList();
    }
}
```

`GameController`: inject `QuestsQuery`, add

```java
    @GetMapping("/api/quests")
    public List<Quest> quests() {
        return quests.all();
    }
```

- [ ] **Step 4: Run the test, expect PASS; run the whole suite** (via `test-runner`)

### Task 2: Quests window and Take quest (frontend)

**Files:** `api.ts`, new `aiSession.ts`, `SkillCard.tsx`, new `QuestsDialog.tsx`, `App.tsx`, `i18n.ts`, `skillMap.css`

- [ ] **Step 1: `api.ts`**: `export type Quest = { area, skillKey, skillName, skillNameRu, question, questionRu, xp }`
  and `fetchQuests()` (same shape as `fetchGame`).
- [ ] **Step 2: `aiSession.ts`**: move the text building + `navigator.clipboard.writeText` from `StudyWithAi` into
  `export async function copyAiSession(key: string, goal: string): Promise<void>` (throws on failure);
  `StudyWithAi` calls it and keeps its own `copied / failed` state.
- [ ] **Step 3: `QuestsDialog.tsx`**: `QuestsButton({ graph, onSelect })` in the `ManageButton` pattern (`<dialog>`,
  body mounted only while open). The button label `Quests (N)` uses `useQuery(['quests'])`, so imports refresh it
  (Import already invalidates all queries). Body: groups by `area` (label from the hub skill in `graph`, else the
  key), each row: skill name (click -> `onSelect(skillKey)` and close), question (by `lang`, `pick`), `+8 XP`,
  "Take quest" button -> `copyAiSession(skillKey, question in English)` + `onSelect`, status per row. Empty list ->
  a hint that questions come from AI sessions.
- [ ] **Step 4: `App.tsx`**: `<QuestsButton graph={graph.data} onSelect={setSelected} />` next to Import.
- [ ] **Step 5: strings EN / RU, styles; `npm run build` + oxlint via `test-runner`; live check on a scratch DB copy.**

Open point for Step 3: the goal copied is the question in English (the AI context is English-first); the Russian
text is only shown in the window.


# Game layer G4b Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clicking the hero badge opens a hero window: summary, a chart of character XP over real time (one point
per day, level thresholds as dashed lines) and the area ranks as bars (constellations in gold).

**Architecture:** The replay gets a timeline: `GameReplay.snapshot()` reads the state accumulated so far; the new
read model `game/query/HeroQuery` replays the history and takes a snapshot after each revision, then folds them to
one point per day. Served at `GET /api/game/hero` (fetched only while the window is open). G4c / G4d reuse the
per-revision snapshots for achievement dates and journal events. `GameQuery` and `/api/game` stay as they are.

**Spec:** [`docs/game-design.md`](game-design.md) §3, §11; decisions of 2026-09-29 (G4b brainstorm).

## Global Constraints

- Everything from the G1-G3 constraints above still applies.
- Nothing is stored: the timeline is recomputed from `revision_change` on every request.
- A snapshot is taken after each revision (`revision_id` changes); its moment = `occurred_at` of the revision's last
  change. One point per day = the last snapshot of that day, day in the server's local zone.
- X axis in real time (distance proportional to dates), plain SVG, no chart library.
- The window has only summary, chart and ranks. Achievements (G4c) and the journal (G4d) come later: no placeholders.
- UI texts in both languages (`i18n.ts`); no em dashes anywhere.

## File structure

| File | Change |
|---|---|
| `backend/src/main/java/dev/minibrain/game/domain/GameReplay.java` | `Snapshot` record + `snapshot()` |
| `backend/src/main/java/dev/minibrain/game/query/HeroView.java` | New: read model of the hero window |
| `backend/src/main/java/dev/minibrain/game/query/HeroQuery.java` | New: replay with a snapshot per revision, XP by day |
| `backend/src/main/java/dev/minibrain/game/web/GameController.java` | `GET /api/game/hero` |
| `backend/src/test/java/dev/minibrain/game/domain/GameRulesTests.java` | Snapshot test |
| `backend/src/test/java/dev/minibrain/game/query/HeroQueryTests.java` | New: import -> today's point = player XP |
| `frontend/src/api.ts` | `HeroView` type + `fetchHero()` |
| `frontend/src/HeroBadge.tsx` | Badge becomes a button, opens the window |
| `frontend/src/HeroDialog.tsx` | New: summary, `XpChart`, area rank bars |
| `frontend/src/App.tsx` | Pass the graph to `HeroBadge` (area names) |
| `frontend/src/i18n.ts`, `frontend/src/skillMap.css` | Strings, styles |

---

### Task 1: Snapshot of the replay

**Files:**
- Modify: `backend/src/main/java/dev/minibrain/game/domain/GameReplay.java`
- Test: `backend/src/test/java/dev/minibrain/game/domain/GameRulesTests.java`

**Interfaces:**
- Produces: `GameReplay.Snapshot(int xp, int level, SortedMap<String, Integer> areaXp, Set<String> constellations)`,
  `GameReplay.snapshot()`.

- [ ] **Step 1: Write the failing test** (append to `GameRulesTests`)

```java
    @Test
    void aSnapshotFreezesTheStateSoFarAndLaterChangesDoNotTouchIt() {
        var replay = new GameReplay();
        replay.apply(new Event("SKILL_CREATED", "ddd.aggregate", "UNDERSTOOD"));
        var first = replay.snapshot();

        replay.apply(new Event("SKILL_CREATED", "ddd.entity", "APPLIED"));
        replay.apply(new Event("SKILL_STATUS_CHANGED", "ddd.aggregate", "LEARNING"));
        var second = replay.snapshot();

        assertThat(first.xp()).isEqualTo(30);
        assertThat(first.level()).isEqualTo(2);
        assertThat(first.areaXp()).containsExactly(java.util.Map.entry("ddd", 30));
        assertThat(second.xp()).isEqualTo(70);
        assertThat(second.areaXp()).containsExactly(java.util.Map.entry("ddd", 70));
        assertThat(second.constellations()).isEmpty();
    }
```

- [ ] **Step 2: Run it, expect a compile failure** (`snapshot()` missing). Via the `test-runner` agent:
  `mvn -q test -Dtest=GameRulesTests` in `backend`.

- [ ] **Step 3: Implement** (in `GameReplay`, next to `constellations()`)

```java
    /** The state accumulated so far: one moment of the timeline (docs/game-design.md §3). */
    public record Snapshot(int xp, int level, SortedMap<String, Integer> areaXp, Set<String> constellations) {
    }

    public Snapshot snapshot() {
        int xp = totalXp();
        return new Snapshot(xp, GameRules.levelFor(xp), areaXp(), constellations());
    }
```

`areaXp()` and `constellations()` build new collections on each call, so a snapshot never changes afterwards.

- [ ] **Step 4: Run `GameRulesTests` again, expect PASS.**

- [ ] **Step 5: Commit** after the owner's review: `Game G4b task 1: replay snapshot`.

---

### Task 2: `GET /api/game/hero` (XP by day)

**Files:**
- Create: `backend/src/main/java/dev/minibrain/game/query/HeroView.java`
- Create: `backend/src/main/java/dev/minibrain/game/query/HeroQuery.java`
- Modify: `backend/src/main/java/dev/minibrain/game/web/GameController.java`
- Test: `backend/src/test/java/dev/minibrain/game/query/HeroQueryTests.java`

**Interfaces:**
- Consumes: `GameReplay.snapshot()` (Task 1).
- Produces: `HeroView(List<XpPoint> xpByDay)`, `HeroView.XpPoint(LocalDate day, int xp)`; JSON
  `{ "xpByDay": [ { "day": "2026-09-29", "xp": 758 } ] }`, days ascending. `HeroQuery.timeline()` (package-private,
  `List<Moment(Instant at, GameReplay.Snapshot state)>`) is what G4c / G4d build on.

- [ ] **Step 1: Write the failing test**

```java
package dev.minibrain.game.query;

import dev.minibrain.importing.application.ImportApplier;
import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreviewer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Shared in-memory DB: own area prefix ("hq"), compare with the live game state instead of fixed totals.
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class HeroQueryTests {

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    ImportApplier applier;

    @Autowired
    GameQuery game;

    @Autowired
    HeroQuery hero;

    @Autowired
    MockMvc mvc;

    @Test
    void todaysPointIsTheCurrentXpAndDaysAreAscending() throws Exception {
        String update = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "newSkills": [ { "key": "hq.chart", "name": "Chart", "status": "UNDERSTOOD" } ] }
                """;
        applier.apply(update, previewer.preview(update).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet()));

        var days = hero.get().xpByDay();
        assertThat(days).isNotEmpty();
        assertThat(days.getLast().day()).isEqualTo(LocalDate.now());
        assertThat(days.getLast().xp()).isEqualTo(game.get().player().xp());
        for (int i = 1; i < days.size(); i++) assertThat(days.get(i).day()).isAfter(days.get(i - 1).day());

        mvc.perform(get("/api/game/hero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xpByDay[-1:].day").value(LocalDate.now().toString()));
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure** (`HeroQuery` missing).

- [ ] **Step 3: Implement**

`HeroView.java`:

```java
package dev.minibrain.game.query;

import java.time.LocalDate;
import java.util.List;

/**
 * The hero window (docs/game-design.md §11): character XP over time, one point per day with changes, days ascending.
 * G4c / G4d add achievements and the journal here. Not a durable contract.
 */
public record HeroView(List<XpPoint> xpByDay) {

    public record XpPoint(LocalDate day, int xp) {
    }
}
```

`HeroQuery.java`:

```java
package dev.minibrain.game.query;

import dev.minibrain.game.domain.GameReplay;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Replays the history like {@link GameQuery}, but takes a snapshot after each revision: the game's timeline.
 * ponytail: full replay per request, same ceiling as GameQuery.
 */
@Component
public class HeroQuery {

    /** One moment of the timeline: the state right after a revision. */
    record Moment(Instant at, GameReplay.Snapshot state) {
    }

    private final JdbcClient jdbc;

    public HeroQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public HeroView get() {
        var byDay = new LinkedHashMap<LocalDate, Integer>(); // moments are in time order: the last one of a day wins
        for (Moment m : timeline()) byDay.put(LocalDate.ofInstant(m.at(), ZoneId.systemDefault()), m.state().xp());
        return new HeroView(byDay.entrySet().stream().map(e -> new HeroView.XpPoint(e.getKey(), e.getValue())).toList());
    }

    List<Moment> timeline() {
        record Row(long revision, GameReplay.Event event, String at) {
        }
        var rows = jdbc.sql("SELECT revision_id, type, skill_key, to_status, occurred_at FROM revision_change ORDER BY occurred_at, id")
                .query((rs, rowNum) -> new Row(rs.getLong("revision_id"),
                        new GameReplay.Event(rs.getString("type"), rs.getString("skill_key"), rs.getString("to_status")),
                        rs.getString("occurred_at")))
                .list();

        var replay = new GameReplay();
        var moments = new ArrayList<Moment>();
        for (int i = 0; i < rows.size(); i++) {
            replay.apply(rows.get(i).event());
            boolean lastOfRevision = i + 1 == rows.size() || rows.get(i + 1).revision() != rows.get(i).revision();
            if (lastOfRevision) moments.add(new Moment(Instant.parse(rows.get(i).at()), replay.snapshot()));
        }
        return moments;
    }
}
```

`GameController`: inject `HeroQuery hero` through the constructor and add

```java
    /** The hero window (docs/game-design.md §11): XP over time. Fetched only while the window is open. */
    @GetMapping("/api/game/hero")
    public HeroView hero() {
        return hero.get();
    }
```

- [ ] **Step 4: Run the whole backend suite via the `test-runner` agent (`mvn -q test`), expect all green.**

- [ ] **Step 5: Commit** after review: `Game G4b task 2: GET /api/game/hero, XP by day from a snapshot per revision`.

---

### Task 3: Hero window (frontend)

**Files:**
- Modify: `frontend/src/api.ts`, `frontend/src/HeroBadge.tsx`, `frontend/src/App.tsx`, `frontend/src/i18n.ts`,
  `frontend/src/skillMap.css`
- Create: `frontend/src/HeroDialog.tsx`

**Interfaces:**
- Consumes: `GET /api/game/hero` (Task 2), `GameState` (`['game']`), `KnowledgeGraph` (area names).
- Produces: `fetchHero(): Promise<HeroView>`, `HeroBadge({ graph })`, `HeroBody({ game, graph })`.

- [ ] **Step 1: `api.ts`**

```ts
// Mirrors dev.minibrain.game.query.HeroView: the hero window. day = "YYYY-MM-DD", ascending.
export type HeroView = { xpByDay: { day: string; xp: number }[] }

export async function fetchHero(): Promise<HeroView> {
  const response = await fetch('/api/game/hero')
  if (!response.ok) throw new Error(`GET /api/game/hero failed: ${response.status}`)
  return response.json()
}
```

- [ ] **Step 2: `HeroBadge.tsx`**: the `<section className="hero">` becomes `<button type="button" className="hero"
  title={t('hero.open')}>` with the same content, opening a `<dialog className="import hero-dialog">` with the same
  open / close pattern as `QuestsButton`. The dialog renders `<HeroBody game={game.data} graph={graph} />` only while
  open. Props: `{ graph: KnowledgeGraph | undefined }`; `App.tsx` passes `graph.data`.

- [ ] **Step 3: `HeroDialog.tsx`**

```tsx
import { useQuery } from '@tanstack/react-query'
import { useContext } from 'react'
import { fetchHero, type GameState, type KnowledgeGraph } from './api'
import { LangContext, pick, useT } from './i18n'

// Hero window (docs/game-design.md §11): summary, XP over time, area ranks. Achievements and journal: G4c / G4d.

const W = 520
const H = 180
const PAD = { left: 44, right: 12, top: 10, bottom: 24 }
const DAY = 86_400_000
const levelStart = (level: number) => 25 * (level - 1) ** 2 // same curve as GameRules.xpForLevel

export function HeroBody({ game, graph }: { game: GameState; graph: KnowledgeGraph | undefined }) {
  const t = useT()
  const lang = useContext(LangContext)
  const hero = useQuery({ queryKey: ['hero'], queryFn: fetchHero })
  const p = game.player
  const areaName = (key: string) => {
    const hub = graph?.nodes.find((n) => n.key === key)
    return hub ? pick(lang, hub.name, hub.nameRu) : key
  }
  const areas = [...game.areas].sort((a, b) => b.xp - a.xp)
  const maxXp = Math.max(1, ...areas.map((a) => a.xp))

  return (
    <div className="hero-window">
      <h2 className="import__title">{t(`hero.title.${p.title}`)}</h2>
      <p className="import__hint">
        {t('hero.level', { level: p.level })} · {t('hero.xp', { xp: p.xp, next: p.nextLevelXp })}
        {p.talentPoints > 0 && ` · ${t('hero.points', { n: p.talentPoints })}`}
      </p>

      <h3 className="hero-window__heading">{t('hero.chart')}</h3>
      {hero.data && hero.data.xpByDay.length > 0 && <XpChart points={hero.data.xpByDay} />}

      <h3 className="hero-window__heading">{t('hero.ranks')}</h3>
      <ul className="hero-ranks">
        {areas.map((a) => (
          <li key={a.key} className={a.complete ? 'is-constellation' : undefined}>
            <span className="hero-ranks__name">
              {areaName(a.key)} · {t(`rank.${Math.min(a.rank, 5) as 1 | 2 | 3 | 4 | 5}`)}
            </span>
            <span className="hero-ranks__bar">
              <span style={{ width: `${(a.xp / maxXp) * 100}%` }} />
            </span>
            <span className="hero-ranks__xp">{a.xp}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

// XP over real time: x proportional to the date; dashed lines where levels start; the top line is the next level.
function XpChart({ points }: { points: { day: string; xp: number }[] }) {
  const t = useT()
  const times = points.map((pt) => Date.parse(pt.day))
  const t0 = times[0]
  const span = Math.max(DAY, times[times.length - 1] - t0) // at least a day, so a single point sits on the left
  const top = Math.max(...points.map((pt) => pt.xp))
  let next = 2
  while (levelStart(next) <= top) next++
  const yMax = levelStart(next)
  const x = (time: number) => PAD.left + ((time - t0) / span) * (W - PAD.left - PAD.right)
  const y = (xp: number) => H - PAD.bottom - (xp / yMax) * (H - PAD.top - PAD.bottom)
  const levels = Array.from({ length: next - 1 }, (_, i) => i + 2) // 2 .. next
  const line = points.map((pt, i) => `${i ? 'L' : 'M'}${x(times[i]).toFixed(1)},${y(pt.xp).toFixed(1)}`).join(' ')

  return (
    <svg className="xp-chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={t('hero.chart')}>
      {levels.map((level) => (
        <g key={level}>
          <line className="xp-chart__level" x1={PAD.left} x2={W - PAD.right} y1={y(levelStart(level))} y2={y(levelStart(level))} />
          <text className="xp-chart__label" x={PAD.left - 6} y={y(levelStart(level)) + 4} textAnchor="end">
            {t('hero.levelShort', { level })}
          </text>
        </g>
      ))}
      <path className="xp-chart__line" d={line} />
      {points.map((pt, i) => (
        <circle key={pt.day} className="xp-chart__dot" cx={x(times[i])} cy={y(pt.xp)} r={3.5}>
          <title>{`${pt.day}: ${pt.xp} XP`}</title>
        </circle>
      ))}
      <text className="xp-chart__label" x={PAD.left} y={H - 6}>{points[0].day}</text>
      <text className="xp-chart__label" x={W - PAD.right} y={H - 6} textAnchor="end">{points[points.length - 1].day}</text>
    </svg>
  )
}
```

With many levels the dashed lines get dense; acceptable now (level ~6 = 5 lines). Thin them out when it bites.

- [ ] **Step 4: `i18n.ts`**: EN `'hero.chart': 'Experience over time'`, `'hero.ranks': 'Area ranks'`,
  `'hero.levelShort': 'L{level}'`, `'hero.open': 'Open the hero window'`; RU `'hero.chart': 'Опыт во времени'`,
  `'hero.ranks': 'Ранги областей'`, `'hero.levelShort': 'Ур. {level}'`, `'hero.open': 'Открыть окно героя'`.

- [ ] **Step 5: `skillMap.css`**: `.hero` as a button (inherit font and color, `text-align: left`, `cursor: pointer`,
  hover border `var(--bronze)`); `.hero-window__heading` (small caps, `var(--bone-dim)`); `.xp-chart` (`width: 100%`),
  `__level` (stroke `var(--bronze-dim)`, dasharray `3 4`), `__line` (stroke `var(--gilded)`, width 2, no fill),
  `__dot` (fill `var(--gilded)`), `__label` (fill `var(--bone-dim)`, 11px); `.hero-ranks` rows as a 3-column grid
  (name, bar, XP), bar track `var(--abyss)`, fill `var(--bronze)`; `.hero-ranks .is-constellation` fill and name in
  `var(--gilded)`.

- [ ] **Step 6: `npm run build` and `npm run lint` in `frontend`, expect clean (the 3 old warnings stay).**

- [ ] **Step 7: Live check** on the real DB (read only): the badge opens the window, the chart shows the days of the
  history with level lines, Messaging's bar is gold. After the owner's OK commit
  `Game G4b: hero window with XP over time and area ranks`, and update `docs/roadmap.md` (G4b ✅) and
  `docs/decisions.md`.


# Game layer G4c Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The hero window lists the achievements of design §9: earned ones with their date, locked ones dimmed with
their condition.

**Architecture:** The replay snapshot gains the counters the conditions need. `game/domain/Achievement` is an enum,
one condition (a predicate over a snapshot) per value. `HeroQuery` walks the timeline (G4b) and dates each
achievement at the first moment its condition holds. `HeroView` gets `achievements`.

**Spec:** [`docs/game-design.md`](game-design.md) §9; decision of 2026-09-29: an achievement, once earned, is kept.

## Global Constraints

- Everything from the G1-G4b constraints above still applies.
- Earned once = kept forever (date = first moment the condition holds). Levels and constellations can still be lost.
- "Boss slayer" is left out until bosses exist.
- "First X" means at least one skill at status X or higher. Evidence for "Proven" counts all evidence (no cap).
- `NOTES_SAVED` has no skill key: count it before the replay's "no skill key" early return.

## File structure

| File | Change |
|---|---|
| `backend/src/main/java/dev/minibrain/game/domain/GameReplay.java` | Counters (statuses, evidence, resolved, unlocks, notes) in `Snapshot` |
| `backend/src/main/java/dev/minibrain/game/domain/Achievement.java` | New: the list of achievements and their conditions |
| `backend/src/main/java/dev/minibrain/game/query/HeroView.java` | `achievements: List<EarnedAchievement(id, earnedAt)>` |
| `backend/src/main/java/dev/minibrain/game/query/HeroQuery.java` | Dates from the timeline |
| `backend/src/test/java/dev/minibrain/game/domain/GameRulesTests.java` | Conditions over snapshots |
| `backend/src/test/java/dev/minibrain/game/query/HeroQueryTests.java` | Every achievement listed, earned ones dated |
| `frontend/src/api.ts`, `frontend/src/HeroDialog.tsx`, `frontend/src/i18n.ts`, `frontend/src/skillMap.css` | Section "Achievements" |

---

### Task 1: Achievements in the backend

**Interfaces:**
- Produces: `GameReplay.Snapshot(int xp, int level, SortedMap<String, Integer> areaXp, Set<String> constellations,
  List<String> statuses, int evidence, int resolved, int unlocks, int notes)`;
  `enum Achievement { FIRST_UNDERSTANDING, HANDS_ON, MASTERY, QUEST_HUNTER, CARTOGRAPHER, PATHFINDER, CONSTELLATION,
  CHRONICLER, PROVEN; boolean earnedBy(Snapshot) }`;
  `HeroView.EarnedAchievement(Achievement id, Instant earnedAt)` (`earnedAt` null = locked), in enum order.

- [ ] **Step 1: Failing tests.** `GameRulesTests`: a replay that reaches UNDERSTOOD, then APPLIED, 1 unlock, 1 notes
  save; assert which achievements `earnedBy` each snapshot. `HeroQueryTests`: `hero.get().achievements()` lists all 9
  in enum order, `FIRST_UNDERSTANDING` has a date (the import in the test creates an UNDERSTOOD skill).
- [ ] **Step 2: Run, expect compile failures.**
- [ ] **Step 3: Implement.** `GameReplay`: count `NOTES_SAVED` before the null-key return; `Snapshot` gets the
  counters (`statuses` = a copy of every skill's status). `Achievement` enum with a `Predicate<Snapshot>` per value
  (thresholds: QUEST_HUNTER 10 resolved, CARTOGRAPHER 5 areas, CHRONICLER 10 notes, PROVEN 25 evidence).
  `HeroQuery.get()`: for each achievement the `at` of the first moment whose snapshot earns it.
- [ ] **Step 4: Whole backend suite via `test-runner`, expect green.**
- [ ] **Step 5: Commit after review:** `Game G4c task 1: achievements dated from the timeline`.

### Task 2: Achievements in the hero window

- [ ] **Step 1:** `api.ts`: `achievements: { id: AchievementId; earnedAt: string | null }[]` in `HeroView`.
- [ ] **Step 2:** `HeroDialog.tsx`: section "Achievements" between the summary and the chart, earned first (by date),
  then locked. Each: name, condition, date (local date) or dimmed.
- [ ] **Step 3:** `i18n.ts`: `achievement.<ID>` (name) and `achievement.<ID>.how` (condition) in EN and RU,
  `hero.achievements` heading. `skillMap.css`: a two-column grid of tiles, earned with a gilded rim, locked dimmed.
- [ ] **Step 4:** `npm run build` + `npm run lint`, live check on the real DB (read only), commit after the owner's
  OK, update `docs/roadmap.md` (G4c ✅) and `docs/decisions.md`.


# Game layer G4d Implementation Plan

**Goal:** The hero window shows a journal of deeds, newest first: level up / down, rank up / down per area,
constellation formed / lost, topic unlocked from the fog, achievement earned.

**Architecture:** `game/domain/Deed.between(before, after)` compares two snapshots of the timeline (pure Java).
`HeroQuery` runs it over neighbouring moments (the first against an empty map) and adds the achievements at the moment
they were earned. `HeroView.journal: List<JournalEntry(at, kind, subject, value)>`. The snapshot keeps the unlocked
keys (not just a count) so the journal can name the topic.

**Spec:** [`docs/game-design.md`](game-design.md) §3 (losses are shown), §11.

### Task 1: Deeds in the backend

- [ ] `Deed` record + `Kind` enum + `between`; `Snapshot.unlocked` becomes `List<String>`; `HeroQuery.journal`.
- [ ] Tests: `GameRulesTests` (gain from empty, then a lost constellation and an unlocked topic), `HeroQueryTests`
  (journal not empty, newest first). Whole suite via `test-runner`. Commit after review.

### Task 2: Journal in the hero window

- [ ] `api.ts`: `journal` in `HeroView`. `HeroDialog.tsx`: section "Journal" after the ranks, grouped by day (local),
  each line one deed; skill names from the graph, area names like the ranks, achievement names from i18n.
  Losses in a muted ember colour. Strings EN / RU in `i18n.ts`, styles in `skillMap.css`.
- [ ] Build + lint, live check on the real DB (read only), commit after the owner's OK, roadmap G4d ✅, decisions.
