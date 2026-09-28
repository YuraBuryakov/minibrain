package dev.minibrain.importing.application;

import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.persistence.OpenQuestionRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import dev.minibrain.skill.query.SkillDetails;
import dev.minibrain.skill.query.SkillDetailsQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
class ImportApplierTests {

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    ImportApplier applier;

    @Autowired
    SkillRepository skills;

    @Autowired
    OpenQuestionRepository questions;

    @Autowired
    SkillDetailsQuery details;

    private static final String UPDATE = """
            { "type": "MINIBRAIN_UPDATE", "schemaVersion": 1,
              "changes": [ {
                "skill": "apply.aggregate",
                "proposedStatus": "APPLIED",
                "evidenceAdded": ["Designed the Order aggregate"],
                "openQuestionsAdded": ["How big may an aggregate get?"],
                "openQuestionsResolved": ["Where is the boundary?"]
              } ],
              "newSkills": [ { "key": "apply.boundary", "name": "Consistency Boundary", "status": "LEARNING" } ],
              "newRelations": [ { "from": "apply.aggregate", "type": "REQUIRES", "to": "apply.boundary" } ],
              "suggestedSkills": [ { "key": "apply.event", "name": "Domain Event", "reason": "Next after aggregates" } ] }
            """;

    @Test
    void appliesTheSelectedItemsInOneGo() {
        Skill aggregate = skills.create("apply.aggregate", "Aggregate", null, SkillStatus.UNDERSTOOD);
        questions.add(aggregate.id(), "Where is the boundary?");

        Set<Integer> preselected = previewer.preview(UPDATE).items().stream()
                .filter(Item::selected).map(Item::id).collect(Collectors.toSet());
        ImportApplier.Result result = applier.apply(UPDATE, preselected);

        SkillDetails after = details.find("apply.aggregate").orElseThrow();
        assertThat(result.applied()).isEqualTo(6);
        assertThat(after.status()).isEqualTo(SkillStatus.APPLIED);
        assertThat(after.evidence()).extracting(SkillDetails.Evidence::text).containsExactly("Designed the Order aggregate");
        assertThat(after.openQuestions()).extracting(SkillDetails.Question::text)
                .containsExactly("How big may an aggregate get?", "Where is the boundary?"); // open first, then resolved
        assertThat(after.openQuestions().get(1).resolvedAt()).isNotNull();
        assertThat(after.relations()).extracting(SkillDetails.Relation::key).containsExactly("apply.boundary");
        assertThat(skills.findByKey("apply.event")).isEmpty(); // suggested skill was not ticked

        // Applying the same text again changes nothing: everything is ALREADY_PRESENT now.
        assertThat(applier.apply(UPDATE, preselected).applied()).isZero();
    }

    @Test
    void rejectsTextThatIsNotAnUpdate() {
        assertThatThrownBy(() -> applier.apply("{ broken", Set.of(1)))
                .isInstanceOf(ImportApplier.NotApplicableException.class);
    }
}
