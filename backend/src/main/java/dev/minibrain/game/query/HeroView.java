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
