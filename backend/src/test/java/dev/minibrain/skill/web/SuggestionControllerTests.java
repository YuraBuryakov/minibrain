package dev.minibrain.skill.web;

import dev.minibrain.importing.application.ImportApplier;
import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreview.Verdict;
import dev.minibrain.importing.application.ImportPreviewer;
import dev.minibrain.revision.domain.RevisionChange;
import dev.minibrain.revision.persistence.RevisionRepository;
import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.query.KnowledgeGraph;
import dev.minibrain.skill.query.KnowledgeGraphQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class SuggestionControllerTests {

    private static final String UPDATE = """
            { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
              "newSkills": [ { "key": "sug.base", "name": "Base", "status": "LEARNING" },
                             { "key": "sug.xp", "name": "Xp", "status": "MASTERED" } ],
              "suggestedSkills": [
                { "key": "sug.next", "name": { "en": "Next", "ru": "Дальше" }, "reason": { "en": "Grows from base", "ru": "Растёт из базы" },
                  "from": "sug.base" },
                { "key": "sug.meh", "name": "Meh" } ] }
            """;

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    ImportApplier applier;

    @Autowired
    KnowledgeGraphQuery graph;

    @Autowired
    RevisionRepository revisions;

    @Autowired
    MockMvc mvc;

    @Test
    void unlockTurnsASuggestionIntoALinkedSkillAndDismissHidesIt() throws Exception {
        var selected = previewer.preview(UPDATE).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet());
        applier.apply(UPDATE, selected);
        assertThat(graph.get().suggestions()).extracting(KnowledgeGraph.Suggestion::key).contains("sug.next", "sug.meh");
        assertThat(graph.get().nodes()).extracting(KnowledgeGraph.Node::key).doesNotContain("sug.next");

        mvc.perform(post("/api/game/unlock/sug.next")).andExpect(status().isCreated()); // a MASTERED skill = points

        KnowledgeGraph after = graph.get();
        assertThat(after.suggestions()).extracting(KnowledgeGraph.Suggestion::key).doesNotContain("sug.next");
        assertThat(after.nodes()).contains(new KnowledgeGraph.Node("sug.next", "Next", "Дальше", SkillStatus.DISCOVERED));
        assertThat(after.edges()).contains(new KnowledgeGraph.Edge("sug.base", RelationType.LEADS_TO, "sug.next"));
        assertThat(revisions.findRecent(1).getFirst().changes()).extracting(RevisionChange::type)
                .containsExactly(RevisionChange.Type.SKILL_UNLOCKED, RevisionChange.Type.RELATION_ADDED);

        mvc.perform(post("/api/suggestions/sug.meh/dismiss")).andExpect(status().isNoContent());
        assertThat(graph.get().suggestions()).extracting(KnowledgeGraph.Suggestion::key).doesNotContain("sug.meh");
        mvc.perform(post("/api/game/unlock/sug.meh")).andExpect(status().isNotFound());

        // A dismissed suggestion is not offered again.
        assertThat(previewer.preview(UPDATE).items()).filteredOn(i -> "sug.meh".equals(i.skill()))
                .extracting(Item::verdict).containsExactly(Verdict.ALREADY_PRESENT);
    }

    @Test
    void aSuggestionLearnedAsANewSkillLeavesTheFog() {
        String suggest = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2, "suggestedSkills": [ { "key": "sug.learned", "name": "Learned" } ] }
                """;
        String learn = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "newSkills": [ { "key": "sug.learned", "name": "Learned", "status": "LEARNING" } ] }
                """;
        for (String update : List.of(suggest, learn)) {
            applier.apply(update, previewer.preview(update).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet()));
        }

        KnowledgeGraph after = graph.get();
        assertThat(after.nodes()).extracting(KnowledgeGraph.Node::key).contains("sug.learned");
        assertThat(after.suggestions()).extracting(KnowledgeGraph.Suggestion::key).doesNotContain("sug.learned");
    }
}
