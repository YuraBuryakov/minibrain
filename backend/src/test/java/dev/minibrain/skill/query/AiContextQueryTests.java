package dev.minibrain.skill.query;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class AiContextQueryTests {

    @Autowired
    SkillRepository skills;

    @Autowired
    EvidenceRepository evidence;

    @Autowired
    OpenQuestionRepository questions;

    @Autowired
    SkillRelationRepository relations;

    @Autowired
    AiContextQuery query;

    @Autowired
    ObjectMapper json;

    @Test
    void selectsNewestEvidenceOpenQuestionsAndMostUsefulRelations() {
        Skill focus = skills.create("ctx.focus", "Focus", null, SkillStatus.LEARNING);
        for (int i = 1; i <= 7; i++) {
            evidence.add(focus.id(), "Evidence " + i);
        }
        var closed = questions.add(focus.id(), "Closed");
        questions.add(focus.id(), "Open");
        questions.resolve(focus.id(), closed.id());

        // 8 related skills: one more than the limit; the incoming one is the least useful and must be dropped.
        Skill dependant = skills.create("ctx.dependant", "Dependant", null, SkillStatus.DISCOVERED);
        relations.add(dependant.id(), focus.id(), RelationType.REQUIRES);
        Skill prerequisite = skills.create("ctx.prerequisite", "Prerequisite", null, SkillStatus.APPLIED);
        relations.add(focus.id(), prerequisite.id(), RelationType.REQUIRES);
        for (int i = 1; i <= 6; i++) {
            Skill next = skills.create("ctx.next-" + i, "Next " + i, null, SkillStatus.DISCOVERED);
            relations.add(focus.id(), next.id(), RelationType.LEADS_TO);
        }

        AiContext context = query.find("ctx.focus", null).orElseThrow();

        assertThat(context.type()).isEqualTo("MINIBRAIN_CONTEXT");
        assertThat(context.schemaVersion()).isEqualTo(1);
        assertThat(context.evidence()).containsExactly("Evidence 3", "Evidence 4", "Evidence 5", "Evidence 6", "Evidence 7");
        assertThat(context.openQuestions()).containsExactly("Open");
        assertThat(context.relatedSkills()).hasSize(7);
        assertThat(context.relatedSkills().getFirst())
                .isEqualTo(new AiContext.RelatedSkill("ctx.prerequisite", "Prerequisite", SkillStatus.APPLIED, RelationType.REQUIRES, "outgoing"));
        assertThat(context.relatedSkills()).extracting(AiContext.RelatedSkill::key).doesNotContain("ctx.dependant");
        assertThat(context.goal()).isNull();
    }

    @Test
    void jsonOmitsMissingGoalAndKeepsGivenOne() throws Exception {
        skills.create("ctx.json", "Json", null, SkillStatus.LEARNING);

        String withoutGoal = json.writeValueAsString(query.find("ctx.json", "  ").orElseThrow());
        String withGoal = json.writeValueAsString(query.find("ctx.json", " Learn boundaries ").orElseThrow());

        assertThat(withoutGoal).contains("\"type\":\"MINIBRAIN_CONTEXT\"").doesNotContain("goal");
        assertThat(withGoal).contains("\"goal\":\"Learn boundaries\"");
    }

    @Test
    void unknownSkillIsEmpty() {
        assertThat(query.find("ctx.no-such", null)).isEmpty();
    }
}
