package dev.minibrain.importing.application;

import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.learning.persistence.LearningSessionRepository;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.persistence.OpenQuestionRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import dev.minibrain.skill.persistence.SuggestedSkillRepository;
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

    @Autowired
    LearningSessionRepository sessions;

    @Autowired
    SuggestedSkillRepository suggestions;

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
              "suggestedSkills": [ { "key": "apply.event", "name": "Domain Event", "reason": "Next after aggregates", "from": "apply.aggregate" } ] }
            """;

    @Test
    void appliesTheSelectedItemsInOneGo() {
        Skill aggregate = skills.create("apply.aggregate", "Aggregate", null, SkillStatus.UNDERSTOOD);
        questions.add(aggregate.id(), "Where is the boundary?");

        Set<Integer> preselected = previewer.preview(UPDATE).items().stream()
                .filter(Item::selected).map(Item::id).collect(Collectors.toSet());
        ImportApplier.Result result = applier.apply(UPDATE, preselected);

        SkillDetails after = details.find("apply.aggregate").orElseThrow();
        assertThat(result.applied()).isEqualTo(7);
        assertThat(after.status()).isEqualTo(SkillStatus.APPLIED);
        assertThat(after.evidence()).extracting(SkillDetails.Evidence::text).containsExactly("Designed the Order aggregate");
        assertThat(after.openQuestions()).extracting(SkillDetails.Question::text)
                .containsExactly("How big may an aggregate get?", "Where is the boundary?"); // open first, then resolved
        assertThat(after.openQuestions().get(1).resolvedAt()).isNotNull();
        assertThat(after.relations()).extracting(SkillDetails.Relation::key).containsExactly("apply.boundary");
        assertThat(skills.findByKey("apply.event")).isEmpty(); // a suggestion goes to the fog, not onto the map
        assertThat(suggestions.findOpen("apply.event")).hasValueSatisfying(s -> assertThat(s.sourceSkill()).isEqualTo("apply.aggregate"));

        // Applying the same text again changes nothing: everything is ALREADY_PRESENT now.
        assertThat(applier.apply(UPDATE, preselected).applied()).isZero();
    }

    @Test
    void rejectsTextThatIsNotAnUpdate() {
        assertThatThrownBy(() -> applier.apply("{ broken", Set.of(1)))
                .isInstanceOf(ImportApplier.NotApplicableException.class);
    }

    @Test
    void savesBilingualNotesFromPastedChatTextAndLinksThemToTouchedSkills() {
        skills.create("notes.saga", "Saga", null, SkillStatus.LEARNING);
        String chat = """
                Great session. Here are your notes.

                ## English
                ### Saga
                A saga coordinates local transactions. Example payload:
                ```json
                { "orderId": 42 }
                ```

                ## Русский
                ### Сага
                Сага координирует локальные транзакции.

                ```json
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 1, "session": { "topic": "Sagas" },
                  "changes": [ { "skill": "notes.saga", "evidenceAdded": ["Explained compensation"] } ] }
                ```
                """;

        ImportPreview preview = previewer.preview(chat);
        assertThat(preview.documentIssues()).isEmpty();
        Set<Integer> all = preview.items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet());
        assertThat(applier.apply(chat, all).applied()).isEqualTo(2); // evidence + notes

        var saved = sessions.findBySkillKey("notes.saga");
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().topic()).isEqualTo("Sagas");
        assertThat(saved.getFirst().notesEn()).startsWith("### Saga").contains("{ \"orderId\": 42 }");
        assertThat(saved.getFirst().notesRu()).isEqualTo("### Сага\nСага координирует локальные транзакции.");

        // The same text again: the notes are recognised as already saved.
        assertThat(applier.apply(chat, all).applied()).isZero();
    }

    @Test
    void takesNotesFromTheJsonNotesFieldWithoutTheirLanguageHeading() {
        skills.create("notes.context", "Bounded Context", null, SkillStatus.LEARNING);
        // A bare JSON, as copied with the chat's copy button: notes inside, each starting with its language heading.
        String json = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 1, "session": { "topic": "Contexts" },
                  "notes": { "en": "## English\\n\\n### Key ideas\\n- One meaning per term", "ru": "## Русский\\n\\n### Идеи\\n- Одно значение" },
                  "changes": [ { "skill": "notes.context", "openQuestionsAdded": ["Where is the boundary?"] } ] }
                """;

        Set<Integer> all = previewer.preview(json).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet());
        applier.apply(json, all);

        var saved = sessions.findBySkillKey("notes.context").getFirst();
        assertThat(saved.notesEn()).isEqualTo("### Key ideas\n- One meaning per term");
        assertThat(saved.notesRu()).isEqualTo("### Идеи\n- Одно значение");
    }

    @Test
    void notesArrivingLaterStillLinkToSkillsWhoseChangesAreAlreadyPresent() {
        skills.create("notes.later", "Later", null, SkillStatus.LEARNING);
        String withoutNotes = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 1,
                  "changes": [ { "skill": "notes.later", "openQuestionsAdded": ["First question"] } ] }
                """;
        String withNotes = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 1, "notes": { "en": "Notes", "ru": "Конспект" },
                  "changes": [ { "skill": "notes.later", "openQuestionsAdded": ["First question"] } ] }
                """;
        applier.apply(withoutNotes, Set.of(1));

        // Second import: the question is ALREADY_PRESENT, only the notes are new, yet they must land on the skill.
        Set<Integer> selected = previewer.preview(withNotes).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet());
        assertThat(applier.apply(withNotes, selected).applied()).isEqualTo(1);
        assertThat(sessions.findBySkillKey("notes.later")).extracting(LearningSessionRepository.SessionNotes::notesRu).containsExactly("Конспект");
    }
}
