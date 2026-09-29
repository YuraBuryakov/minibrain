package dev.minibrain.game.domain;

import dev.minibrain.game.domain.GameReplay.Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * One entry of the journal of deeds (docs/game-design.md §11): what changed between two moments of the timeline.
 * {@code subject}: area key, skill key or achievement name (null for the character level); {@code value}: the new
 * level or rank (0 when it does not apply). Losses are deeds too: the game is honest (§3).
 */
public record Deed(Kind kind, String subject, int value) {

    public enum Kind { LEVEL_UP, LEVEL_DOWN, RANK_UP, RANK_DOWN, CONSTELLATION_FORMED, CONSTELLATION_LOST, TOPIC_UNLOCKED, ACHIEVEMENT }

    /** Deeds from {@code before} to {@code after}, without achievements (they are dated from the whole timeline). */
    public static List<Deed> between(Snapshot before, Snapshot after) {
        var deeds = new ArrayList<Deed>();
        if (after.level() != before.level()) {
            deeds.add(new Deed(after.level() > before.level() ? Kind.LEVEL_UP : Kind.LEVEL_DOWN, null, after.level()));
        }
        var areas = new TreeSet<>(before.areaXp().keySet());
        areas.addAll(after.areaXp().keySet());
        for (String area : areas) {
            int from = GameRules.levelFor(before.areaXp().getOrDefault(area, 0));
            int to = GameRules.levelFor(after.areaXp().getOrDefault(area, 0));
            if (to != from) deeds.add(new Deed(to > from ? Kind.RANK_UP : Kind.RANK_DOWN, area, to));
        }
        onlyIn(after.constellations(), before.constellations(), Kind.CONSTELLATION_FORMED, deeds);
        onlyIn(before.constellations(), after.constellations(), Kind.CONSTELLATION_LOST, deeds);
        after.unlocked().subList(before.unlocked().size(), after.unlocked().size())
                .forEach(key -> deeds.add(new Deed(Kind.TOPIC_UNLOCKED, key, 0)));
        return deeds;
    }

    private static void onlyIn(Set<String> in, Set<String> notIn, Kind kind, List<Deed> deeds) {
        new TreeSet<>(in).stream().filter(a -> !notIn.contains(a)).forEach(a -> deeds.add(new Deed(kind, a, 0)));
    }
}
