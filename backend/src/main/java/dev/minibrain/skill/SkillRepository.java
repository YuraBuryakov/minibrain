package dev.minibrain.skill;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

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
}
