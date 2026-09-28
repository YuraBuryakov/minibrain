package dev.minibrain.game.query;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

import static dev.minibrain.game.domain.GameRules.QUESTION_XP;
import static dev.minibrain.game.domain.GameRules.areaOf;

/** Every open question on the map as a quest, ordered by area, skill, then oldest first. Nothing is stored. */
@Component
public class QuestsQuery {

    private final JdbcClient jdbc;

    public QuestsQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Quest> all() {
        return jdbc.sql("""
                        SELECT s.key, s.name, s.name_ru, q.text, q.text_ru
                        FROM open_question q JOIN skill s ON s.id = q.skill_id
                        WHERE q.resolved_at IS NULL
                        ORDER BY s.key, q.id
                        """)
                .query((rs, rowNum) -> new Quest(areaOf(rs.getString("key")), rs.getString("key"),
                        rs.getString("name"), rs.getString("name_ru"), rs.getString("text"), rs.getString("text_ru"),
                        QUESTION_XP))
                .list()
                .stream()
                .sorted(Comparator.comparing(Quest::area)) // stable: keeps the skill / id order inside an area
                .toList();
    }
}
