package dev.minibrain.skill.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static dev.minibrain.skill.persistence.SkillRepository.TIMESTAMP;

/** Pinned node positions on the map (layout state, not knowledge). Nodes without a row are placed automatically. */
@Repository
public class NodePositionRepository {

    public record NodePosition(String id, double x, double y) {
    }

    private final JdbcClient jdbc;

    public NodePositionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<NodePosition> findAll() {
        return jdbc.sql("SELECT node_id, x, y FROM node_position ORDER BY node_id")
                .query((rs, rowNum) -> new NodePosition(rs.getString("node_id"), rs.getDouble("x"), rs.getDouble("y")))
                .list();
    }

    /** Insert or move (SQLite upsert). */
    public void pin(NodePosition p) {
        jdbc.sql("""
                        INSERT INTO node_position (node_id, x, y, updated_at) VALUES (?, ?, ?, ?)
                        ON CONFLICT (node_id) DO UPDATE SET x = excluded.x, y = excluded.y, updated_at = excluded.updated_at
                        """)
                .params(p.id(), p.x(), p.y(), TIMESTAMP.format(Instant.now()))
                .update();
    }

    /** Back to automatic layout for every node. */
    public void unpinAll() {
        jdbc.sql("DELETE FROM node_position").update();
    }
}
