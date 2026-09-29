package dev.minibrain.game.query;

import dev.minibrain.game.domain.Achievement;
import dev.minibrain.game.domain.Deed;
import dev.minibrain.game.domain.GameReplay;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Replays the history like {@link GameQuery}, but takes a snapshot after each revision: the game's timeline.
 * ponytail: full replay per request, same ceiling as GameQuery.
 */
@Component
public class HeroQuery {

    /** One moment of the timeline: the state right after a revision. */
    record Moment(Instant at, GameReplay.Snapshot state) {
    }

    private final JdbcClient jdbc;

    public HeroQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public HeroView get() {
        var timeline = timeline();
        var byDay = new LinkedHashMap<LocalDate, Integer>(); // moments are in time order: the last one of a day wins
        for (Moment m : timeline) byDay.put(LocalDate.ofInstant(m.at(), ZoneId.systemDefault()), m.state().xp());
        var achievements = Arrays.stream(Achievement.values())
                .map(a -> new HeroView.EarnedAchievement(a, timeline.stream()
                        .filter(m -> a.earnedBy(m.state())).map(Moment::at).findFirst().orElse(null)))
                .toList();
        return new HeroView(byDay.entrySet().stream().map(e -> new HeroView.XpPoint(e.getKey(), e.getValue())).toList(),
                achievements, journal(timeline, achievements));
    }

    /** Deeds between neighbouring moments (the first against an empty map) plus earned achievements, newest first. */
    private static List<HeroView.JournalEntry> journal(List<Moment> timeline, List<HeroView.EarnedAchievement> achievements) {
        var entries = new ArrayList<HeroView.JournalEntry>();
        var before = new GameReplay().snapshot();
        for (Moment m : timeline) {
            int start = entries.size();
            for (Deed d : Deed.between(before, m.state())) {
                entries.add(new HeroView.JournalEntry(m.at(), d.kind(), d.subject(), d.value()));
            }
            for (var a : achievements) {
                if (m.at().equals(a.earnedAt())) {
                    entries.add(new HeroView.JournalEntry(m.at(), Deed.Kind.ACHIEVEMENT, a.id().name(), 0));
                }
            }
            before = m.state();
            Collections.rotate(entries, entries.size() - start); // this moment first
        }
        return entries; // newest moment first, deeds inside a moment in their natural order (level, ranks, ...)
    }

    List<Moment> timeline() {
        record Row(long revision, GameReplay.Event event, String at) {
        }
        var rows = jdbc.sql("SELECT revision_id, type, skill_key, to_status, occurred_at FROM revision_change ORDER BY occurred_at, id")
                .query((rs, rowNum) -> new Row(rs.getLong("revision_id"),
                        new GameReplay.Event(rs.getString("type"), rs.getString("skill_key"), rs.getString("to_status")),
                        rs.getString("occurred_at")))
                .list();

        var replay = new GameReplay();
        var moments = new ArrayList<Moment>();
        for (int i = 0; i < rows.size(); i++) {
            replay.apply(rows.get(i).event());
            boolean lastOfRevision = i + 1 == rows.size() || rows.get(i + 1).revision() != rows.get(i).revision();
            if (lastOfRevision) moments.add(new Moment(Instant.parse(rows.get(i).at()), replay.snapshot()));
        }
        return moments;
    }
}
