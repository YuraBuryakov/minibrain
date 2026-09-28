package dev.minibrain.skill.persistence;

import dev.minibrain.skill.domain.SuggestedSkill;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

import static dev.minibrain.skill.persistence.SkillRepository.TIMESTAMP;

@Repository
public class SuggestedSkillRepository {

    private final JdbcClient jdbc;

    public SuggestedSkillRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void add(SuggestedSkill s) {
        jdbc.sql("""
                        INSERT INTO suggested_skill (key, name, name_ru, reason, reason_ru, source_skill_key, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(s.key(), s.name(), s.nameRu(), s.reason(), s.reasonRu(), s.sourceSkill(), TIMESTAMP.format(Instant.now()))
                .update();
    }

    /** Also true for a dismissed suggestion: the same key is not suggested again. */
    public boolean exists(String key) {
        return jdbc.sql("SELECT COUNT(*) FROM suggested_skill WHERE key = ?").param(key).query(Integer.class).single() > 0;
    }

    /** A suggestion still waiting for a decision (not dismissed). */
    public Optional<SuggestedSkill> findOpen(String key) {
        return jdbc.sql("""
                        SELECT key, name, name_ru, reason, reason_ru, source_skill_key
                        FROM suggested_skill WHERE key = ? AND dismissed_at IS NULL
                        """)
                .param(key)
                .query((rs, rowNum) -> new SuggestedSkill(rs.getString("key"), rs.getString("name"), rs.getString("name_ru"),
                        rs.getString("reason"), rs.getString("reason_ru"), rs.getString("source_skill_key")))
                .optional();
    }

    public void delete(String key) {
        jdbc.sql("DELETE FROM suggested_skill WHERE key = ?").param(key).update();
    }

    public void dismiss(String key) {
        jdbc.sql("UPDATE suggested_skill SET dismissed_at = ? WHERE key = ? AND dismissed_at IS NULL")
                .params(TIMESTAMP.format(Instant.now()), key)
                .update();
    }
}
