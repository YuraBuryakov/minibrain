package dev.minibrain.skill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
class KnowledgeGraphQueryTests {

    @Autowired
    SkillRepository skills;

    @Autowired
    SkillRelationRepository relations;

    @Autowired
    KnowledgeGraphQuery query;

    @Test
    void returnsSkillsAsNodesAndRelationsAsEdgesByKey() {
        Skill outbox = skills.create("graph.outbox", "Outbox", "not in the node", SkillStatus.LEARNING);
        Skill tx = skills.create("graph.transactions", "Transactions", null, SkillStatus.APPLIED);
        relations.add(outbox.id(), tx.id(), RelationType.REQUIRES);

        KnowledgeGraph graph = query.get();

        assertThat(graph.nodes()).contains(
                new KnowledgeGraph.Node("graph.outbox", "Outbox", SkillStatus.LEARNING),
                new KnowledgeGraph.Node("graph.transactions", "Transactions", SkillStatus.APPLIED));
        assertThat(graph.edges()).contains(
                new KnowledgeGraph.Edge("graph.outbox", RelationType.REQUIRES, "graph.transactions"));
    }
}
