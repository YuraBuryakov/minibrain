package dev.minibrain.skill;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static dev.minibrain.skill.SkillRepository.TIMESTAMP;

@Repository
public class SkillRelationRepository {

    private final JdbcClient jdbc;

    public SkillRelationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void add(long fromSkillId, long toSkillId, RelationType type) {
        jdbc.sql("""
                        INSERT INTO skill_relation (from_skill_id, to_skill_id, type, created_at)
                        VALUES (:from, :to, :type, :now)
                        """)
                .param("from", fromSkillId)
                .param("to", toSkillId)
                .param("type", type.name())
                .param("now", TIMESTAMP.format(Instant.now()))
                .update();
    }

    /** Outgoing and incoming relations of one Skill. */
    public List<SkillRelation> findBySkillId(long skillId) {
        return jdbc.sql("""
                        SELECT f.key AS from_key, r.type, t.key AS to_key
                        FROM skill_relation r
                        JOIN skill f ON f.id = r.from_skill_id
                        JOIN skill t ON t.id = r.to_skill_id
                        WHERE r.from_skill_id = :id OR r.to_skill_id = :id
                        ORDER BY f.key, r.type, t.key
                        """)
                .param("id", skillId)
                .query((rs, rowNum) -> new SkillRelation(
                        rs.getString("from_key"),
                        RelationType.valueOf(rs.getString("type")),
                        rs.getString("to_key")))
                .list();
    }
}
