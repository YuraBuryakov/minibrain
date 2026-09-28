package dev.minibrain.importing.application;

import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreview.Verdict;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.persistence.OpenQuestionRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import dev.minibrain.skill.query.KnowledgeGraphQuery;
import dev.minibrain.skill.query.SkillDetails;
import dev.minibrain.skill.query.SkillDetailsQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
class BilingualImportTests {

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
    KnowledgeGraphQuery graph;

    @Test
    void storesBothLanguagesAndResolvesAQuestionByItsRussianText() {
        Skill context = skills.create("bi.context", "Bounded Context", null, SkillStatus.LEARNING);
        questions.add(context.id(), "Where is the boundary?", "Где проходит граница?");

        String update = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "newSkills": [ { "key": "bi.acl",
                                   "name": { "en": "Anti-Corruption Layer", "ru": "Защитный слой" },
                                   "description": { "en": "Translates between models", "ru": "Переводит между моделями" },
                                   "status": "DISCOVERED" } ],
                  "changes": [ { "skill": "bi.context",
                                 "evidenceAdded": [ { "en": "Explained the boundary", "ru": "Объяснил границу" } ],
                                 "openQuestionsAdded": [ "English only question" ],
                                 "openQuestionsResolved": [ { "ru": "Где проходит граница?" } ] } ],
                  "newRelations": [ { "from": "bi.acl", "type": "PART_OF", "to": "bi.context" } ] }
                """;

        var preview = previewer.preview(update);
        assertThat(preview.documentIssues()).isEmpty();
        assertThat(preview.items()).extracting(Item::verdict).containsOnly(Verdict.READY);
        Set<Integer> selected = preview.items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet());
        applier.apply(update, selected);

        SkillDetails acl = details.find("bi.acl").orElseThrow();
        assertThat(acl.name()).isEqualTo("Anti-Corruption Layer");
        assertThat(acl.nameRu()).isEqualTo("Защитный слой");
        assertThat(acl.descriptionRu()).isEqualTo("Переводит между моделями");

        SkillDetails after = details.find("bi.context").orElseThrow();
        assertThat(after.evidence()).extracting(SkillDetails.Evidence::textRu).containsExactly("Объяснил границу");
        assertThat(after.openQuestions()).filteredOn(q -> q.text().equals("English only question"))
                .extracting(SkillDetails.Question::textRu).containsExactly((String) null);
        assertThat(after.openQuestions()).filteredOn(q -> q.text().equals("Where is the boundary?"))
                .allSatisfy(q -> assertThat(q.resolvedAt()).isNotNull());
        assertThat(graph.get().nodes()).filteredOn(n -> n.key().equals("bi.acl"))
                .extracting(n -> n.nameRu()).containsExactly("Защитный слой");
    }

    @Test
    void aVersionOneFileWithPlainStringsStillImports() {
        String update = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 1,
                  "newSkills": [ { "key": "bi.plain", "name": "Plain", "description": "English only", "status": "LEARNING" } ] }
                """;

        var preview = previewer.preview(update);
        applier.apply(update, preview.items().stream().map(Item::id).collect(Collectors.toSet()));

        SkillDetails plain = details.find("bi.plain").orElseThrow();
        assertThat(plain.name()).isEqualTo("Plain");
        assertThat(plain.nameRu()).isNull();
        assertThat(plain.description()).isEqualTo("English only");
    }
}
