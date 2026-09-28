package dev.minibrain.skill.query;

import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;
import java.util.List;

/**
 * Read model for the Skill Map: renderer-neutral (no React Flow, no positions).
 * Independent of the write model: a node is not a Skill and may grow UI-specific fields (e.g. counters).
 */
public record KnowledgeGraph(List<Node> nodes, List<Edge> edges) {

    public record Node(String key, String name, SkillStatus status) {
    }

    public record Edge(String from, RelationType type, String to) {
    }
}
