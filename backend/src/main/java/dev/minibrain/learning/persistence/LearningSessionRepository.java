package dev.minibrain.learning.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

@Repository
public class LearningSessionRepository {

    /** Study notes of one session, as the reading window shows them. Either language may be null. */
    public record SessionNotes(long id, String topic, String notesEn, String notesRu, Instant createdAt) {
    }

    // Same fixed-width format as the skill tables (text order = time order).
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final JdbcClient jdbc;

    public LearningSessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long add(String topic, String notesEn, String notesRu, Collection<Long> skillIds) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO learning_session (topic, notes_en, notes_ru, created_at) VALUES (:topic, :en, :ru, :now)")
                .param("topic", topic)
                .param("en", notesEn)
                .param("ru", notesRu)
                .param("now", TIMESTAMP.format(Instant.now()))
                .update(keyHolder);
        long sessionId = keyHolder.getKey().longValue();
        for (long skillId : skillIds) {
            jdbc.sql("INSERT INTO learning_session_skill (session_id, skill_id) VALUES (?, ?)")
                    .params(sessionId, skillId)
                    .update();
        }
        return sessionId;
    }

    /** True if exactly these notes were already saved (makes re-importing the same text a no-op). */
    public boolean exists(String notesEn, String notesRu) {
        return jdbc.sql("SELECT COUNT(*) FROM learning_session WHERE notes_en IS ? AND notes_ru IS ?")
                .params(notesEn, notesRu)
                .query(Integer.class)
                .single() > 0;
    }

    /** Sessions that touched a skill, newest first. */
    public List<SessionNotes> findBySkillKey(String skillKey) {
        return jdbc.sql("""
                        SELECT s.id, s.topic, s.notes_en, s.notes_ru, s.created_at
                        FROM learning_session s
                        JOIN learning_session_skill ss ON ss.session_id = s.id
                        JOIN skill k ON k.id = ss.skill_id
                        WHERE k.key = ?
                        ORDER BY s.id DESC
                        """)
                .param(skillKey)
                .query((rs, rowNum) -> new SessionNotes(
                        rs.getLong("id"),
                        rs.getString("topic"),
                        rs.getString("notes_en"),
                        rs.getString("notes_ru"),
                        Instant.parse(rs.getString("created_at"))))
                .list();
    }
}
