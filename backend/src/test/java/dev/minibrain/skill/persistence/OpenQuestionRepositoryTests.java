package dev.minibrain.skill.persistence;

import dev.minibrain.skill.domain.OpenQuestion;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
class OpenQuestionRepositoryTests {

    @Autowired
    SkillRepository skills;

    @Autowired
    OpenQuestionRepository questions;

    @Test
    void addedQuestionIsOpenAndReadsBack() {
        Skill skill = skills.create("oq.read-back", "Read back", null, SkillStatus.LEARNING);
        OpenQuestion question = questions.add(skill.id(), "Where should the boundary be drawn?");

        assertThat(question.resolvedAt()).isNull();
        assertThat(questions.findBySkillId(skill.id())).containsExactly(question);
    }

    @Test
    void resolveIsIdempotentAndKeepsFirstTime() {
        Skill skill = skills.create("oq.resolve", "Resolve", null, SkillStatus.LEARNING);
        OpenQuestion question = questions.add(skill.id(), "When is it too large?");

        OpenQuestion first = questions.resolve(skill.id(), question.id()).orElseThrow();
        OpenQuestion again = questions.resolve(skill.id(), question.id()).orElseThrow();

        assertThat(first.resolvedAt()).isNotNull();
        assertThat(again.resolvedAt()).isEqualTo(first.resolvedAt());
    }

    @Test
    void resolveIgnoresQuestionOfAnotherSkill() {
        Skill owner = skills.create("oq.owner", "Owner", null, SkillStatus.LEARNING);
        Skill stranger = skills.create("oq.stranger", "Stranger", null, SkillStatus.LEARNING);
        OpenQuestion question = questions.add(owner.id(), "Whose question?");

        assertThat(questions.resolve(stranger.id(), question.id())).isEmpty();
        assertThat(questions.findBySkillId(owner.id()).getFirst().resolvedAt()).isNull();
    }

    @Test
    void textIsUniqueWithinSkillOnly() {
        Skill a = skills.create("oq.unique-a", "A", null, SkillStatus.LEARNING);
        Skill b = skills.create("oq.unique-b", "B", null, SkillStatus.LEARNING);
        questions.add(a.id(), "Same text");
        questions.add(b.id(), "Same text");

        assertThatThrownBy(() -> questions.add(a.id(), "Same text"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
