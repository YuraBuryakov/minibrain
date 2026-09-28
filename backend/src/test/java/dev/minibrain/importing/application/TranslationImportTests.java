package dev.minibrain.importing.application;

import dev.minibrain.importing.application.ImportPreview.Issue.Code;
import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreview.Verdict;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.persistence.EvidenceRepository;
import dev.minibrain.skill.persistence.OpenQuestionRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import dev.minibrain.skill.query.SkillDetails;
import dev.minibrain.skill.query.SkillDetailsQuery;
import dev.minibrain.skill.query.TranslationRequest;
import dev.minibrain.skill.query.TranslationRequestQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
class TranslationImportTests {

    @Autowired
    TranslationRequestQuery requestQuery;

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    ImportApplier applier;

    @Autowired
    SkillRepository skills;

    @Autowired
    EvidenceRepository evidence;

    @Autowired
    OpenQuestionRepository questions;

    @Autowired
    SkillDetailsQuery details;

    @Test
    void listsOnlyMissingRussianTextsThenFillsThemFromATranslationImport() {
        Skill saga = skills.create("tr.saga", "Saga", null, "Coordinates local transactions", null, SkillStatus.LEARNING);
        evidence.add(saga.id(), "Explained compensation");
        evidence.add(saga.id(), "Already translated", "Уже переведено");
        questions.add(saga.id(), "When to use a saga?");
        skills.create("tr.done", "Done", "Готово", null, null, SkillStatus.LEARNING);

        TranslationRequest request = requestQuery.missing(null);
        assertThat(request.type()).isEqualTo("MINIBRAIN_TRANSLATION_REQUEST");
        assertThat(request.skills()).filteredOn(s -> s.key().startsWith("tr."))
                .containsExactly(new TranslationRequest.SkillTexts("tr.saga", "Saga", "Coordinates local transactions",
                        java.util.List.of("Explained compensation"), java.util.List.of("When to use a saga?")));
        assertThat(requestQuery.missing("tr.done").skills()).isEmpty();

        String answer = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "translations": [ { "skill": "tr.saga",
                    "name": { "en": "Saga", "ru": "Сага" },
                    "description": { "en": "Coordinates local transactions", "ru": "Координирует локальные транзакции" },
                    "evidence": [ { "en": "Explained compensation", "ru": "Объяснил компенсацию" },
                                  { "en": "Already translated", "ru": "Другой перевод" },
                                  { "en": "Never written", "ru": "Не было" } ],
                    "openQuestions": [ { "en": "When to use a saga?", "ru": "Когда нужна сага?" } ] } ] }
                """;

        var preview = previewer.preview(answer);
        assertThat(preview.items()).filteredOn(i -> i.label().contains("Другой перевод")).singleElement().satisfies(i -> {
            assertThat(i.issues()).extracting(ImportPreview.Issue::code).containsExactly(Code.REPLACES_TRANSLATION);
            assertThat(i.selected()).isFalse(); // replacing an existing translation must be ticked by hand
        });
        assertThat(preview.items()).filteredOn(i -> i.label().contains("Не было")).singleElement()
                .satisfies(i -> assertThat(i.verdict()).isEqualTo(Verdict.INVALID));

        Set<Integer> selected = preview.items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet());
        assertThat(applier.apply(answer, selected).applied()).isEqualTo(4);

        SkillDetails after = details.find("tr.saga").orElseThrow();
        assertThat(after.nameRu()).isEqualTo("Сага");
        assertThat(after.descriptionRu()).isEqualTo("Координирует локальные транзакции");
        assertThat(after.evidence()).extracting(SkillDetails.Evidence::textRu).containsExactly("Объяснил компенсацию", "Уже переведено");
        assertThat(after.openQuestions()).extracting(SkillDetails.Question::textRu).containsExactly("Когда нужна сага?");
        assertThat(requestQuery.missing("tr.saga").skills()).isEmpty(); // nothing left to translate
    }
}
