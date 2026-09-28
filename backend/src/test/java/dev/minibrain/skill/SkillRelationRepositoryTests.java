package dev.minibrain.skill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
class SkillRelationRepositoryTests {

    @Autowired
    SkillRepository skills;

    @Autowired
    SkillRelationRepository relations;

    @Test
    void returnsOutgoingAndIncomingRelationsByKey() {
        Skill ddd = skills.create("rel.ddd", "DDD", null, SkillStatus.LEARNING);
        Skill aggregate = skills.create("rel.aggregate", "Aggregate", null, SkillStatus.LEARNING);
        Skill invariant = skills.create("rel.invariant", "Invariant", null, SkillStatus.LEARNING);
        Skill unrelated = skills.create("rel.unrelated", "Unrelated", null, SkillStatus.LEARNING);
        relations.add(aggregate.id(), ddd.id(), RelationType.PART_OF);
        relations.add(aggregate.id(), invariant.id(), RelationType.REQUIRES);
        relations.add(invariant.id(), unrelated.id(), RelationType.LEADS_TO);

        assertThat(relations.findBySkillId(invariant.id())).containsExactly(
                new SkillRelation("rel.aggregate", RelationType.REQUIRES, "rel.invariant"),
                new SkillRelation("rel.invariant", RelationType.LEADS_TO, "rel.unrelated"));
    }

    @Test
    void sameTripleTwiceIsRejectedButOtherTypeIsFine() {
        Skill a = skills.create("rel.dup-a", "A", null, SkillStatus.LEARNING);
        Skill b = skills.create("rel.dup-b", "B", null, SkillStatus.LEARNING);
        relations.add(a.id(), b.id(), RelationType.REQUIRES);
        relations.add(a.id(), b.id(), RelationType.LEADS_TO);

        assertThatThrownBy(() -> relations.add(a.id(), b.id(), RelationType.REQUIRES))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsSelfRelation() {
        Skill a = skills.create("rel.self", "Self", null, SkillStatus.LEARNING);

        assertThatThrownBy(() -> relations.add(a.id(), a.id(), RelationType.RELATED_TO))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
