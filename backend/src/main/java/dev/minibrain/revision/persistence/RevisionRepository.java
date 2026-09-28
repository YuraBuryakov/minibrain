package dev.minibrain.revision.persistence;

import dev.minibrain.revision.domain.RevisionChange;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Append-only: revisions are written once and never updated or deleted. */
@Repository
public class RevisionRepository {

    public enum Source { INITIAL, IMPORT, MANUAL }

    /** A revision as the history list shows it, changes in the order they were recorded. */
    public record Revision(long id, Source source, String topic, Instant createdAt, List<RevisionChange> changes) {
    }

    // Same fixed-width format as the other tables (text order = time order).
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final JdbcClient jdbc;

    public RevisionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Writes one revision with its changes. Nothing is written when there are no changes. */
    public void record(Source source, String topic, List<RevisionChange> changes) {
        if (changes.isEmpty()) return;
        var keyHolder = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO revision (source, topic, created_at) VALUES (:source, :topic, :now)")
                .param("source", source.name())
                .param("topic", topic)
                .param("now", TIMESTAMP.format(Instant.now()))
                .update(keyHolder);
        long revisionId = keyHolder.getKey().longValue();
        for (RevisionChange c : changes) {
            jdbc.sql("""
                            INSERT INTO revision_change (revision_id, type, skill_key, from_status, to_status, text,
                                                         related_key, relation_type, occurred_at)
                            VALUES (:revision, :type, :skill, :from, :to, :text, :related, :relationType, :at)
                            """)
                    .param("revision", revisionId)
                    .param("type", c.type().name())
                    .param("skill", c.skillKey())
                    .param("from", c.fromStatus())
                    .param("to", c.toStatus())
                    .param("text", c.text())
                    .param("related", c.relatedKey())
                    .param("relationType", c.relationType())
                    .param("at", TIMESTAMP.format(c.occurredAt()))
                    .update();
        }
    }

    /** Newest revisions first, each with all its changes. */
    public List<Revision> findRecent(int limit) {
        Map<Long, Revision> byId = new LinkedHashMap<>();
        jdbc.sql("SELECT id, source, topic, created_at FROM revision ORDER BY id DESC LIMIT ?")
                .param(limit)
                .query((rs, rowNum) -> byId.put(rs.getLong("id"), new Revision(rs.getLong("id"),
                        Source.valueOf(rs.getString("source")), rs.getString("topic"),
                        Instant.parse(rs.getString("created_at")), new ArrayList<>())))
                .list();
        if (byId.isEmpty()) return List.of();
        jdbc.sql("SELECT * FROM revision_change WHERE revision_id >= ? ORDER BY id")
                .param(byId.keySet().stream().mapToLong(Long::longValue).min().orElseThrow())
                .query((rs, rowNum) -> {
                    Revision revision = byId.get(rs.getLong("revision_id"));
                    if (revision != null) revision.changes().add(mapChange(rs));
                    return null;
                })
                .list();
        return List.copyOf(byId.values());
    }

    private static RevisionChange mapChange(ResultSet rs) throws SQLException {
        return new RevisionChange(
                RevisionChange.Type.valueOf(rs.getString("type")),
                rs.getString("skill_key"),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("text"),
                rs.getString("related_key"),
                rs.getString("relation_type"),
                Instant.parse(rs.getString("occurred_at")));
    }
}
