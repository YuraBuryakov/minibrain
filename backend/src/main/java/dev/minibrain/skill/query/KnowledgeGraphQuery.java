package dev.minibrain.skill.query;

import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read side (brief §47 GetKnowledgeGraph): own SQL, own result types, no repositories involved. */
@Component
public class KnowledgeGraphQuery {

    private final JdbcClient jdbc;

    public KnowledgeGraphQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public KnowledgeGraph get() {
        var nodes = jdbc.sql("SELECT key, name, name_ru, status FROM skill ORDER BY key")
                .query((rs, rowNum) -> new KnowledgeGraph.Node(
                        rs.getString("key"),
                        rs.getString("name"),
                        rs.getString("name_ru"),
                        SkillStatus.valueOf(rs.getString("status"))))
                .list();
        var edges = jdbc.sql("""
                        SELECT f.key AS from_key, r.type, t.key AS to_key
                        FROM skill_relation r
                        JOIN skill f ON f.id = r.from_skill_id
                        JOIN skill t ON t.id = r.to_skill_id
                        ORDER BY f.key, r.type, t.key
                        """)
                .query((rs, rowNum) -> new KnowledgeGraph.Edge(
                        rs.getString("from_key"),
                        RelationType.valueOf(rs.getString("type")),
                        rs.getString("to_key")))
                .list();
        return new KnowledgeGraph(nodes, edges);
    }
}
