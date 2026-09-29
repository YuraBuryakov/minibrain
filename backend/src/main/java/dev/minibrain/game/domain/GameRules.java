package dev.minibrain.game.domain;

import java.util.Collection;

/**
 * Game rules (docs/game-design.md §4-5): pure formulas, no Spring, no SQL. Every tuning number lives here.
 * Statuses are plain names ("UNDERSTOOD"), like in the revision history the game is computed from.
 */
public final class GameRules {

    public static final int EVIDENCE_XP = 5;
    public static final int EVIDENCE_CAP = 5; // evidence counted per skill: the status carries the weight
    public static final int QUESTION_XP = 8;
    public static final int LEVEL_STEP = 25;
    public static final String UNLOCK_STATUS = "UNDERSTOOD"; // mastery gate: the source skill must reach it (spec §6)
    public static final int CONSTELLATION_MIN_SKILLS = 3;

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

    /** Clear margin (px) around known land in the fog: 60 at level 1, +30 per level, at most 200 (spec §7). */
    public static int vision(int level) {
        return Math.min(200, 60 + 30 * (level - 1));
    }

    /** Mastery gate (spec §6): a topic in the fog opens only from a source skill at UNLOCK_STATUS or higher. */
    public static boolean opensTheFog(String sourceStatus) {
        return statusXp(sourceStatus) >= statusXp(UNLOCK_STATUS);
    }

    /** Constellation (spec §8): an area of at least 3 skills, every one UNDERSTOOD or higher. */
    public static boolean isConstellation(Collection<String> statuses) {
        return statuses.size() >= CONSTELLATION_MIN_SKILLS
                && statuses.stream().allMatch(s -> statusXp(s) >= statusXp("UNDERSTOOD"));
    }

    /** "ddd.aggregate" -> "ddd"; the hub skill "ddd" is its own area. Same rule as the map layout. */
    public static String areaOf(String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }
}
