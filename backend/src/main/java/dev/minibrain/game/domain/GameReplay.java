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
