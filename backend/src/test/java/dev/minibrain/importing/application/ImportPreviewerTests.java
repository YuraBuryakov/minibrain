package dev.minibrain.importing.application;

import dev.minibrain.importing.application.ImportPreview.Issue.Code;
import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreview.Verdict;
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
class ImportPreviewerTests {

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    SkillRepository skills;

    @Autowired
    EvidenceRepository evidence;

    @Autowired
    OpenQuestionRepository questions;

    @Autowired
    SkillRelationRepository relations;

    @Test
    void reportsBrokenJsonAndForeignDocumentsAsAWhole() {
        assertThat(previewer.preview("{ not json").documentIssues()).extracting(ImportPreview.Issue::code).containsExactly(Code.PARSE_ERROR);
        assertThat(previewer.preview("{\"type\": \"MINIBRAIN_CONTEXT\", \"schemaVersion\": 1}").documentIssues())
                .extracting(ImportPreview.Issue::code).containsExactly(Code.UNSUPPORTED_DOCUMENT);
    }

    @Test
    void findsTheUpdateInsidePastedChatText() {
        String chat = """
                ## English
                Notes with code: ```java
                class Order { void add() {} }
                ```
                ```json
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 1, "session": { "topic": "Chat paste" } }
                ```
                """;

        ImportPreview preview = previewer.preview(chat);

        assertThat(preview.documentIssues()).isEmpty();
        assertThat(preview.topic()).isEqualTo("Chat paste");
    }

    @Test
    void validatesNewSkillsAndTheirRelations() {
        skills.create("imp.base", "Base", null, SkillStatus.UNDERSTOOD);

        ImportPreview preview = previewer.preview("""
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 1,
                  "newSkills": [
                    { "key": "imp.child", "name": "Child", "status": "DISCOVERED" },
                    { "key": "imp.orphan", "name": "Orphan", "status": "LEARNING" },
                    { "key": "IMP.Bad", "name": "Bad key", "status": "LEARNING" },
                    { "key": "imp.base", "name": "Base again", "status": "LEARNING" },
                    { "key": "imp.guru", "name": "Guru", "status": "GURU" }
                  ],
                  "newRelations": [
                    { "from": "imp.child", "type": "PART_OF", "to": "imp.base" },
                    { "from": "imp.child", "type": "REQUIRES", "to": "imp.ghost" },
                    { "from": "imp.child", "type": "PART_OF", "to": "imp.base" },
                    { "from": "imp.child", "type": "REQUIRES", "to": "imp.child" }
                  ] }
                """);

        assertThat(item(preview, "Child (imp.child)")).satisfies(i -> {
            assertThat(i.verdict()).isEqualTo(Verdict.READY);
            assertThat(i.issues()).isEmpty();
            assertThat(i.selected()).isTrue();
        });
        assertThat(item(preview, "Orphan (imp.orphan)")).satisfies(i -> {
            assertThat(i.verdict()).isEqualTo(Verdict.READY);
            assertThat(codes(i)).containsExactly(Code.ORPHAN_SKILL); // warning only: still applicable
            assertThat(i.selected()).isTrue();
        });
        assertThat(codes(item(preview, "Bad key (IMP.Bad)"))).containsExactly(Code.INVALID_VALUE);
        assertThat(codes(item(preview, "Base again (imp.base)"))).containsExactly(Code.DUPLICATE_SKILL_KEY);
        assertThat(codes(item(preview, "Guru (imp.guru)"))).containsExactly(Code.INVALID_VALUE);

        var relationItems = preview.items().stream().filter(i -> i.section() == ImportPreview.Section.RELATIONS).toList();
        assertThat(relationItems).extracting(Item::verdict)
                .containsExactly(Verdict.READY, Verdict.INVALID, Verdict.ALREADY_PRESENT, Verdict.INVALID);
        assertThat(codes(relationItems.get(1))).containsExactly(Code.UNKNOWN_SKILL_REFERENCE);
        assertThat(codes(relationItems.get(3))).containsExactly(Code.INVALID_RELATION);
    }

    @Test
    void comparesChangesWithTheCurrentState() {
        Skill aggregate = skills.create("imp.aggregate", "Aggregate", null, SkillStatus.UNDERSTOOD);
        Skill invariant = skills.create("imp.invariant", "Invariant", null, SkillStatus.UNDERSTOOD);
        evidence.add(aggregate.id(), "Known evidence");
        questions.add(aggregate.id(), "Open question");
        relations.add(aggregate.id(), invariant.id(), RelationType.REQUIRES);

        ImportPreview preview = previewer.preview("""
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 1,
                  "changes": [ {
                    "skill": "imp.aggregate",
                    "proposedStatus": "LEARNING",
                    "evidenceAdded": ["Known evidence", "New evidence"],
                    "openQuestionsResolved": ["Open question", "Never asked"]
                  }, { "skill": "imp.nobody", "evidenceAdded": ["Lost"] } ],
                  "newRelations": [
                    { "from": "imp.aggregate", "type": "RELATED_TO", "to": "imp.invariant" },
                    { "from": "imp.aggregate", "type": "REQUIRES", "to": "imp.invariant" }
                  ],
                  "suggestedSkills": [ { "key": "imp.domain-event", "name": "Domain Event" } ] }
                """);

        assertThat(item(preview, "Aggregate: UNDERSTOOD → LEARNING")).satisfies(i -> {
            assertThat(i.verdict()).isEqualTo(Verdict.READY);
            assertThat(codes(i)).containsExactly(Code.STATUS_DOWNGRADE);
            assertThat(i.selected()).isFalse(); // a downgrade must be ticked by hand
        });
        assertThat(item(preview, "Aggregate: Known evidence").verdict()).isEqualTo(Verdict.ALREADY_PRESENT);
        assertThat(item(preview, "Aggregate: New evidence").selected()).isTrue();
        assertThat(item(preview, "resolve \"Open question\"").verdict()).isEqualTo(Verdict.READY);
        assertThat(codes(item(preview, "resolve \"Never asked\""))).containsExactly(Code.UNKNOWN_OPEN_QUESTION);
        assertThat(codes(item(preview, "imp.nobody: Lost"))).containsExactly(Code.UNKNOWN_SKILL_REFERENCE);
        assertThat(item(preview, "Aggregate RELATED_TO Invariant")).satisfies(i -> {
            assertThat(codes(i)).containsExactly(Code.REDUNDANT_RELATION);
            assertThat(i.selected()).isFalse();
        });
        assertThat(item(preview, "Aggregate REQUIRES Invariant").verdict()).isEqualTo(Verdict.ALREADY_PRESENT);
        assertThat(item(preview, "Domain Event (imp.domain-event)")).satisfies(i -> {
            assertThat(i.verdict()).isEqualTo(Verdict.READY);
            assertThat(i.selected()).isTrue(); // storing a suggestion is harmless: unlocking is a separate decision
        });
    }

    private static Item item(ImportPreview preview, String labelPart) {
        return preview.items().stream().filter(i -> i.label().contains(labelPart)).findFirst()
                .orElseThrow(() -> new AssertionError("no item containing: " + labelPart + " in " + preview.items()));
    }

    private static java.util.List<Code> codes(Item item) {
        return item.issues().stream().map(ImportPreview.Issue::code).toList();
    }
}
