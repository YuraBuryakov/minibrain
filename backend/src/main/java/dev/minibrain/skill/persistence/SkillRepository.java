package dev.minibrain.skill.persistence;

import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Repository
public class SkillRepository {

    // Fixed width on purpose: Instant.toString() drops trailing zeros ("...:00Z" vs "...:00.120Z"),
    // which breaks sorting timestamps as text.
    static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final JdbcClient jdbc;

    public SkillRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Skill create(String key, String name, String description, SkillStatus status) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var keyHolder = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO skill (key, name, description, status, created_at, updated_at)
                        VALUES (:key, :name, :description, :status, :now, :now)
                        """)
                .param("key", key)
                .param("name", name)
                .param("description", description)
                .param("status", status.name())
                .param("now", TIMESTAMP.format(now))
                .update(keyHolder);
        return new Skill(keyHolder.getKey().longValue(), key, name, description, status, now, now);
    }

    public Optional<Skill> findByKey(String key) {
        return jdbc.sql("SELECT * FROM skill WHERE key = ?")
                .param(key)
                .query(SkillRepository::mapRow)
                .optional();
    }

    public List<Skill> findAll() {
        return jdbc.sql("SELECT * FROM skill ORDER BY key")
                .query(SkillRepository::mapRow)
                .list();
    }

    private static Skill mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Skill(
                rs.getLong("id"),
                rs.getString("key"),
                rs.getString("name"),
                rs.getString("description"),
                SkillStatus.valueOf(rs.getString("status")),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at")));
    }
}
