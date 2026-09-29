package dev.minibrain.game.query;

import dev.minibrain.game.domain.GameReplay;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
        var byDay = new LinkedHashMap<LocalDate, Integer>(); // moments are in time order: the last one of a day wins
        for (Moment m : timeline()) byDay.put(LocalDate.ofInstant(m.at(), ZoneId.systemDefault()), m.state().xp());
        return new HeroView(byDay.entrySet().stream().map(e -> new HeroView.XpPoint(e.getKey(), e.getValue())).toList());
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
