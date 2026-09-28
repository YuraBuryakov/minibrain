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
