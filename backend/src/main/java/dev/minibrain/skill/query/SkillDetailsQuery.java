package dev.minibrain.skill.query;

import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Read side for the Skill card: own SQL, own result type, no repositories involved. */
@Component
public class SkillDetailsQuery {

    private record Head(long id, String key, String name, String description, SkillStatus status,
                        Instant createdAt, Instant updatedAt) {
    }

    private final JdbcClient jdbc;

    public SkillDetailsQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SkillDetails> find(String key) {
        return jdbc.sql("SELECT * FROM skill WHERE key = ?")
                .param(key)
                .query((rs, rowNum) -> new Head(
                        rs.getLong("id"),
                        rs.getString("key"),
                        rs.getString("name"),
                        rs.getString("description"),
                        SkillStatus.valueOf(rs.getString("status")),
                        Instant.parse(rs.getString("created_at")),
                        Instant.parse(rs.getString("updated_at"))))
                .optional()
                .map(this::details);
    }

    private SkillDetails details(Head skill) {
        var evidence = jdbc.sql("SELECT text, created_at FROM evidence WHERE skill_id = ? ORDER BY id")
                .param(skill.id())
                .query((rs, rowNum) -> new SkillDetails.Evidence(
                        rs.getString("text"),
                        Instant.parse(rs.getString("created_at"))))
                .list();

        // Open questions first, then resolved ones.
        var questions = jdbc.sql("""
                        SELECT text, created_at, resolved_at FROM open_question
                        WHERE skill_id = ?
                        ORDER BY resolved_at IS NOT NULL, id
                        """)
                .param(skill.id())
                .query((rs, rowNum) -> {
                    String resolvedAt = rs.getString("resolved_at");
                    return new SkillDetails.Question(
                            rs.getString("text"),
                            Instant.parse(rs.getString("created_at")),
                            resolvedAt == null ? null : Instant.parse(resolvedAt));
                })
                .list();

        var relations = jdbc.sql("""
                        SELECT r.type, 1 AS outgoing, other.key, other.name, other.status
                        FROM skill_relation r JOIN skill other ON other.id = r.to_skill_id
                        WHERE r.from_skill_id = :id
                        UNION ALL
                        SELECT r.type, 0 AS outgoing, other.key, other.name, other.status
                        FROM skill_relation r JOIN skill other ON other.id = r.from_skill_id
                        WHERE r.to_skill_id = :id
                        ORDER BY outgoing DESC, type, name
                        """)
                .param("id", skill.id())
                .query((rs, rowNum) -> new SkillDetails.Relation(
                        RelationType.valueOf(rs.getString("type")),
                        rs.getBoolean("outgoing"),
                        rs.getString("key"),
                        rs.getString("name"),
                        SkillStatus.valueOf(rs.getString("status"))))
                .list();

        return new SkillDetails(skill.key(), skill.name(), skill.description(), skill.status(),
                skill.createdAt(), skill.updatedAt(), evidence, questions, relations);
    }
}
