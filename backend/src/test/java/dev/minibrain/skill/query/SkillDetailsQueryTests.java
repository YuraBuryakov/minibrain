package dev.minibrain.skill.query;

import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.persistence.EvidenceRepository;
import dev.minibrain.skill.persistence.OpenQuestionRepository;
import dev.minibrain.skill.persistence.SkillRelationRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
class SkillDetailsQueryTests {

    @Autowired
    SkillRepository skills;

    @Autowired
    EvidenceRepository evidence;

    @Autowired
    OpenQuestionRepository questions;

    @Autowired
    SkillRelationRepository relations;

    @Autowired
    SkillDetailsQuery query;

    @Test
    void collectsEverythingTheCardShows() {
        Skill outbox = skills.create("details.outbox", "Outbox", "Events stored with the change", SkillStatus.UNDERSTOOD);
        Skill tx = skills.create("details.tx", "Transactions", null, SkillStatus.APPLIED);
        Skill saga = skills.create("details.saga", "Saga", null, SkillStatus.LEARNING);
        evidence.add(outbox.id(), "Explained the retry window");
        var closed = questions.add(outbox.id(), "Closed question");
        questions.add(outbox.id(), "Still open");
        questions.resolve(outbox.id(), closed.id());
        relations.add(outbox.id(), tx.id(), RelationType.REQUIRES);
        relations.add(saga.id(), outbox.id(), RelationType.RELATED_TO);

        SkillDetails details = query.find("details.outbox").orElseThrow();

        assertThat(details.name()).isEqualTo("Outbox");
        assertThat(details.description()).isEqualTo("Events stored with the change");
        assertThat(details.evidence()).extracting(SkillDetails.Evidence::text).containsExactly("Explained the retry window");
        // open questions first
        assertThat(details.openQuestions()).extracting(SkillDetails.Question::text).containsExactly("Still open", "Closed question");
        assertThat(details.openQuestions().get(1).resolvedAt()).isNotNull();
        // outgoing first, each seen from this skill with the other skill's name and status
        assertThat(details.relations()).containsExactly(
                new SkillDetails.Relation(RelationType.REQUIRES, true, "details.tx", "Transactions", null, SkillStatus.APPLIED),
                new SkillDetails.Relation(RelationType.RELATED_TO, false, "details.saga", "Saga", null, SkillStatus.LEARNING));
    }

    @Test
    void unknownKeyIsEmpty() {
        assertThat(query.find("details.no-such")).isEmpty();
    }
}
