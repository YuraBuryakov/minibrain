package dev.minibrain.game.domain;

import dev.minibrain.game.domain.GameReplay.Snapshot;

import java.util.function.Predicate;

/**
 * Achievements (docs/game-design.md §9): one condition over a moment of the timeline each. Earned at the first moment
 * the condition holds and kept from then on. "Boss slayer" joins when bosses exist.
 */
public enum Achievement {
    FIRST_UNDERSTANDING(s -> reached(s, "UNDERSTOOD")),
    HANDS_ON(s -> reached(s, "APPLIED")),
    MASTERY(s -> reached(s, "MASTERED")),
    QUEST_HUNTER(s -> s.resolved() >= 10),
    CARTOGRAPHER(s -> s.areaXp().size() >= 5),
    PATHFINDER(s -> !s.unlocked().isEmpty()),
    CONSTELLATION(s -> !s.constellations().isEmpty()),
    CHRONICLER(s -> s.notes() >= 10),
    PROVEN(s -> s.evidence() >= 25);

    private final Predicate<Snapshot> condition;

    Achievement(Predicate<Snapshot> condition) {
        this.condition = condition;
    }

    public boolean earnedBy(Snapshot s) {
        return condition.test(s);
    }

    /** At least one skill at this status or higher. */
    private static boolean reached(Snapshot s, String status) {
        return s.statuses().stream().anyMatch(x -> GameRules.statusXp(x) >= GameRules.statusXp(status));
    }
}
