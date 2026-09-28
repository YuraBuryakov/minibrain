package dev.minibrain.game.query;

import dev.minibrain.game.domain.GameReplay;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import static dev.minibrain.game.domain.GameRules.levelFor;
import static dev.minibrain.game.domain.GameRules.titleFor;
import static dev.minibrain.game.domain.GameRules.vision;
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
        int talentPoints = Math.max(0, level - 1 - replay.unlocks()); // a lost level may leave spent points "owed"
        var player = new GameState.Player(xp, level, xpForLevel(level), xpForLevel(level + 1), titleFor(level),
                talentPoints, vision(level));
        var areas = replay.areaXp().entrySet().stream()
                .map(e -> new GameState.Area(e.getKey(), e.getValue(), levelFor(e.getValue())))
                .toList();
        return new GameState(player, areas);
    }
}
