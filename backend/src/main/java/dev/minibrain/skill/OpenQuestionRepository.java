package dev.minibrain.skill;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static dev.minibrain.skill.SkillRepository.TIMESTAMP;

@Repository
public class OpenQuestionRepository {

    private final JdbcClient jdbc;

    public OpenQuestionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public OpenQuestion add(long skillId, String text) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var keyHolder = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO open_question (skill_id, text, created_at) VALUES (:skillId, :text, :now)")
                .param("skillId", skillId)
                .param("text", text)
                .param("now", TIMESTAMP.format(now))
                .update(keyHolder);
        return new OpenQuestion(keyHolder.getKey().longValue(), text, now, null);
    }

    public List<OpenQuestion> findBySkillId(long skillId) {
        return jdbc.sql("SELECT * FROM open_question WHERE skill_id = ? ORDER BY id")
                .param(skillId)
                .query(OpenQuestionRepository::mapRow)
                .list();
    }

    /**
     * Marks the question resolved. Idempotent: an already resolved question keeps its first resolution time.
     * Empty if the question does not exist or belongs to another Skill.
     */
    public Optional<OpenQuestion> resolve(long skillId, long id) {
        jdbc.sql("UPDATE open_question SET resolved_at = :now WHERE id = :id AND skill_id = :skillId AND resolved_at IS NULL")
                .param("now", TIMESTAMP.format(Instant.now()))
                .param("id", id)
                .param("skillId", skillId)
                .update();
        return jdbc.sql("SELECT * FROM open_question WHERE id = ? AND skill_id = ?")
                .params(id, skillId)
                .query(OpenQuestionRepository::mapRow)
                .optional();
    }

    private static OpenQuestion mapRow(ResultSet rs, int rowNum) throws SQLException {
        String resolvedAt = rs.getString("resolved_at");
        return new OpenQuestion(
                rs.getLong("id"),
                rs.getString("text"),
                Instant.parse(rs.getString("created_at")),
                resolvedAt == null ? null : Instant.parse(resolvedAt));
    }
}
