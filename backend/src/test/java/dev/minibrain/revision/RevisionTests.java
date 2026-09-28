package dev.minibrain.revision;

import dev.minibrain.importing.application.ImportApplier;
import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreviewer;
import dev.minibrain.revision.domain.RevisionChange;
import dev.minibrain.revision.domain.RevisionChange.Type;
import dev.minibrain.revision.persistence.RevisionRepository;
import dev.minibrain.revision.persistence.RevisionRepository.Revision;
import dev.minibrain.revision.persistence.RevisionRepository.Source;
import dev.minibrain.skill.persistence.OpenQuestionRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class RevisionTests {

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    ImportApplier applier;

    @Autowired
    RevisionRepository revisions;

    @Autowired
    MockMvc mvc;

    @Autowired
    SkillRepository skills;

    @Autowired
    OpenQuestionRepository questions;

    @Test
    void anImportWritesOneRevisionAndReapplyingWritesNone() {
        String update = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2, "session": { "topic": "Revision test" },
                  "newSkills": [ { "key": "rev.base", "name": "Base", "status": "LEARNING" },
                                 { "key": "rev.child", "name": "Child", "status": "DISCOVERED" } ],
                  "changes": [ { "skill": "rev.base", "evidenceAdded": ["Explained it"] } ],
                  "newRelations": [ { "from": "rev.child", "type": "PART_OF", "to": "rev.base" } ] }
                """;
        var selected = previewer.preview(update).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet());

        applier.apply(update, selected);
        applier.apply(update, selected); // everything is ALREADY_PRESENT now

        List<Revision> ours = revisions.findRecent(200).stream().filter(r -> "Revision test".equals(r.topic())).toList();
        assertThat(ours).hasSize(1);
        assertThat(ours.getFirst().source()).isEqualTo(Source.IMPORT);
        assertThat(ours.getFirst().changes()).extracting(RevisionChange::type)
                .containsExactly(Type.SKILL_CREATED, Type.SKILL_CREATED, Type.EVIDENCE_ADDED, Type.RELATION_ADDED);
        assertThat(ours.getFirst().changes().getFirst().toStatus()).isEqualTo("LEARNING");
        assertThat(ours.getFirst().changes().getLast())
                .extracting(RevisionChange::skillKey, RevisionChange::relationType, RevisionChange::relatedKey)
                .containsExactly("rev.child", "PART_OF", "rev.base");
    }

    @Test
    void manualEndpointsWriteManualRevisionsAndAResolveIsRecordedOnce() throws Exception {
        mvc.perform(post("/api/skills").contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\": \"rev.manual\", \"name\": \"Manual\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/skills/rev.manual/open-questions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\": \"Why?\"}")).andExpect(status().isCreated());
        long questionId = questionIdOf("rev.manual");

        mvc.perform(post("/api/skills/rev.manual/open-questions/" + questionId + "/resolve")).andExpect(status().isOk());
        mvc.perform(post("/api/skills/rev.manual/open-questions/" + questionId + "/resolve")).andExpect(status().isOk());

        List<RevisionChange> changes = revisions.findRecent(200).stream()
                .filter(r -> r.source() == Source.MANUAL)
                .flatMap(r -> r.changes().stream())
                .filter(c -> "rev.manual".equals(c.skillKey()))
                .toList();
        assertThat(changes).extracting(RevisionChange::type)
                .containsExactlyInAnyOrder(Type.SKILL_CREATED, Type.QUESTION_ADDED, Type.QUESTION_RESOLVED);
    }

    private long questionIdOf(String skillKey) {
        long skillId = skills.findByKey(skillKey).orElseThrow().id();
        return questions.findBySkillId(skillId).getFirst().id();
    }
}
