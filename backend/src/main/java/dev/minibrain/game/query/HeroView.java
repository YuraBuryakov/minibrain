package dev.minibrain.game.query;

import dev.minibrain.game.domain.Achievement;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The hero window (docs/game-design.md §11): character XP over time, one point per day with changes, days ascending;
 * every achievement in enum order, {@code earnedAt} null while locked (§9). G4d adds the journal. Not a durable contract.
 */
public record HeroView(List<XpPoint> xpByDay, List<EarnedAchievement> achievements) {

    public record XpPoint(LocalDate day, int xp) {
    }

    public record EarnedAchievement(Achievement id, Instant earnedAt) {
    }
}
