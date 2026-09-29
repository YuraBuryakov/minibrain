package dev.minibrain.game.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

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
    private int unlocks;

    public void apply(Event e) {
        if (e.skillKey() == null) return;
        if ("SKILL_UNLOCKED".equals(e.type())) unlocks++;
        switch (e.type()) {
            case "SKILL_CREATED", "SKILL_STATUS_CHANGED", "SKILL_UNLOCKED" -> {
                if (e.toStatus() != null) progress(e.skillKey()).status = e.toStatus();
            }
            case "EVIDENCE_ADDED" -> progress(e.skillKey()).evidence++;
            case "QUESTION_RESOLVED" -> progress(e.skillKey()).resolved++;
            default -> { // relations, translations, notes, added questions: no XP
            }
        }
    }

    /** Talent points spent so far. */
    public int unlocks() {
        return unlocks;
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

    /** The state accumulated so far: one moment of the timeline (docs/game-design.md §3). */
    public record Snapshot(int xp, int level, SortedMap<String, Integer> areaXp, Set<String> constellations) {
    }

    /** {@code areaXp()} and {@code constellations()} build new collections, so a snapshot never changes afterwards. */
    public Snapshot snapshot() {
        int xp = totalXp();
        return new Snapshot(xp, GameRules.levelFor(xp), areaXp(), constellations());
    }

    /** Keys of the areas that form a constellation right now (spec §8). */
    public Set<String> constellations() {
        var statuses = new HashMap<String, List<String>>();
        skills.forEach((key, p) -> statuses.computeIfAbsent(GameRules.areaOf(key), a -> new ArrayList<>()).add(p.status));
        var complete = new TreeSet<String>();
        statuses.forEach((area, list) -> {
            if (GameRules.isConstellation(list)) complete.add(area);
        });
        return complete;
    }

    // A change for a skill never seen before (should not happen) still counts, starting from DISCOVERED.
    private Progress progress(String key) {
        return skills.computeIfAbsent(key, k -> new Progress());
    }
}
