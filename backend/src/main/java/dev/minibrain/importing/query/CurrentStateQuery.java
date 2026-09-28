package dev.minibrain.importing.query;

import dev.minibrain.importing.format.CurrentState;
import dev.minibrain.importing.format.LocalizedText;
import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds current.json straight from the tables. Every ORDER BY uses stored values only (keys, times, texts),
 * never numeric ids, so a restored database exports the same file.
 */
@Component
public class CurrentStateQuery {

    private final JdbcClient jdbc;

    public CurrentStateQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One read transaction: all lists come from the same moment. */
    @Transactional(readOnly = true)
    public CurrentState get() {
        List<CurrentState.Skill> skills = jdbc.sql("""
                        SELECT key, name, name_ru, description, description_ru, status, created_at, updated_at
                        FROM skill ORDER BY key
                        """)
                .query((rs, rowNum) -> new CurrentState.Skill(
                        rs.getString("key"),
                        new LocalizedText(rs.getString("name"), rs.getString("name_ru")),
                        rs.getString("description") == null && rs.getString("description_ru") == null ? null
                                : new LocalizedText(rs.getString("description"), rs.getString("description_ru")),
                        SkillStatus.valueOf(rs.getString("status")),
                        rs.getString("created_at"),
                        rs.getString("updated_at")))
                .list();

        List<CurrentState.Relation> relations = jdbc.sql("""
                        SELECT f.key AS from_key, t.key AS to_key, r.type, r.created_at
                        FROM skill_relation r JOIN skill f ON f.id = r.from_skill_id JOIN skill t ON t.id = r.to_skill_id
                        ORDER BY f.key, t.key, r.type
                        """)
                .query((rs, rowNum) -> new CurrentState.Relation(rs.getString("from_key"), rs.getString("to_key"),
                        RelationType.valueOf(rs.getString("type")), rs.getString("created_at")))
                .list();

        List<CurrentState.Evidence> evidence = jdbc.sql("""
                        SELECT s.key, e.text, e.text_ru, e.created_at
                        FROM evidence e JOIN skill s ON s.id = e.skill_id
                        ORDER BY s.key, e.created_at, e.text
                        """)
                .query((rs, rowNum) -> new CurrentState.Evidence(rs.getString("key"),
                        new LocalizedText(rs.getString("text"), rs.getString("text_ru")), rs.getString("created_at")))
                .list();

        List<CurrentState.OpenQuestion> questions = jdbc.sql("""
                        SELECT s.key, q.text, q.text_ru, q.created_at, q.resolved_at
                        FROM open_question q JOIN skill s ON s.id = q.skill_id
                        ORDER BY s.key, q.created_at, q.text
                        """)
                .query((rs, rowNum) -> new CurrentState.OpenQuestion(rs.getString("key"),
                        new LocalizedText(rs.getString("text"), rs.getString("text_ru")),
                        rs.getString("created_at"), rs.getString("resolved_at")))
                .list();

        Map<Long, List<String>> sessionSkills = new HashMap<>();
        jdbc.sql("""
                        SELECT ls.session_id, s.key
                        FROM learning_session_skill ls JOIN skill s ON s.id = ls.skill_id
                        ORDER BY s.key
                        """)
                .query((rs, rowNum) -> sessionSkills.computeIfAbsent(rs.getLong("session_id"), id -> new ArrayList<>())
                        .add(rs.getString("key")))
                .list();

        List<CurrentState.LearningSession> sessions = jdbc.sql("""
                        SELECT id, topic, notes_en, notes_ru, created_at
                        FROM learning_session ORDER BY created_at, topic
                        """)
                .query((rs, rowNum) -> new CurrentState.LearningSession(
                        rs.getString("topic"),
                        new CurrentState.Notes(rs.getString("notes_en"), rs.getString("notes_ru")),
                        sessionSkills.getOrDefault(rs.getLong("id"), List.of()),
                        rs.getString("created_at")))
                .list();

        return new CurrentState(CurrentState.TYPE, CurrentState.SCHEMA_VERSION, Instant.now().toString(),
                skills, relations, evidence, questions, sessions);
    }
}
