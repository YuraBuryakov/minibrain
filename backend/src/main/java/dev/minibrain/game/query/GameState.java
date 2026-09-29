package dev.minibrain.game.query;

import dev.minibrain.game.domain.GameRules;

import java.util.List;

/**
 * The game as the UI shows it (docs/game-design.md §5, §11), computed from the revision history on every request.
 * Not a durable contract: nothing is exported or stored.
 */
public record GameState(Player player, List<Area> areas, String unlockStatus) {

    /**
     * {@code levelStartXp} / {@code nextLevelXp}: XP where the current and the next level start (the XP bar).
     * {@code talentPoints}: left to spend; {@code vision}: clear fog margin in px.
     */
    public record Player(int xp, int level, int levelStartXp, int nextLevelXp, GameRules.Title title, int talentPoints,
                         int vision) {
    }

    /** {@code rank} = the area's level on the same curve. */
    public record Area(String key, int xp, int rank) {
    }
}
