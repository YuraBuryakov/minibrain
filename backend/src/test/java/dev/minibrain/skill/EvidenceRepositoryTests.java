package dev.minibrain.skill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
class EvidenceRepositoryTests {

    @Autowired
    SkillRepository skills;

    @Autowired
    EvidenceRepository evidence;

    @Test
    void readsBackEvidenceOfOneSkillInOrder() {
        Skill skill = skills.create("ddd.aggregate-root", "Aggregate Root", null, SkillStatus.LEARNING);
        Skill other = skills.create("ddd.value-object", "Value Object", null, SkillStatus.LEARNING);
        Evidence first = evidence.add(skill.id(), "Explained why children change only through the root");
        Evidence second = evidence.add(skill.id(), "Found a bypass of the root in a code review");
        evidence.add(other.id(), "Belongs to another skill");

        assertThat(evidence.findBySkillId(skill.id())).containsExactly(first, second);
    }

    @Test
    void foreignKeyRejectsUnknownSkill() {
        assertThatThrownBy(() -> evidence.add(999_999, "Orphan evidence"))
                .isInstanceOf(DataAccessException.class);
    }
}
