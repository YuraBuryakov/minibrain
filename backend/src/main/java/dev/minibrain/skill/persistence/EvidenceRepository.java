package dev.minibrain.skill.persistence;

import dev.minibrain.skill.domain.Evidence;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static dev.minibrain.skill.persistence.SkillRepository.TIMESTAMP;

@Repository
public class EvidenceRepository {

    private final JdbcClient jdbc;

    public EvidenceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Evidence add(long skillId, String text) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var keyHolder = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO evidence (skill_id, text, created_at) VALUES (:skillId, :text, :now)")
                .param("skillId", skillId)
                .param("text", text)
                .param("now", TIMESTAMP.format(now))
                .update(keyHolder);
        return new Evidence(keyHolder.getKey().longValue(), text, now);
    }

    public List<Evidence> findBySkillId(long skillId) {
        return jdbc.sql("SELECT id, text, created_at FROM evidence WHERE skill_id = ? ORDER BY id")
                .param(skillId)
                .query((rs, rowNum) -> new Evidence(
                        rs.getLong("id"),
                        rs.getString("text"),
                        Instant.parse(rs.getString("created_at"))))
                .list();
    }
}
