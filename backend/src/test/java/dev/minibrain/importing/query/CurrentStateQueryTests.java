package dev.minibrain.importing.query;

import dev.minibrain.importing.application.ImportApplier;
import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreviewer;
import dev.minibrain.importing.format.CurrentState;
import dev.minibrain.importing.format.LocalizedText;
import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class CurrentStateQueryTests {

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    ImportApplier applier;

    @Autowired
    CurrentStateQuery query;

    @Autowired
    MockMvc mvc;

    @Test
    void exportsAllKnowledgeByKeysInStableOrder() throws Exception {
        // "zz" before "aa" in the file: the export must sort by key, not by insertion order.
        String update = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "session": { "topic": "Export test" },
                  "notes": { "en": "# Notes", "ru": "# Конспект" },
                  "newSkills": [ { "key": "exp.zz", "name": { "en": "Zeta", "ru": "Зета" }, "status": "LEARNING" },
                                 { "key": "exp.aa", "name": "Alpha", "description": "First", "status": "DISCOVERED" } ],
                  "changes": [ { "skill": "exp.zz", "evidenceAdded": [ { "en": "Explained it", "ru": "Объяснил" } ],
                                 "openQuestionsAdded": ["Why?"] } ],
                  "newRelations": [ { "from": "exp.aa", "type": "LEADS_TO", "to": "exp.zz" } ] }
                """;
        var selected = previewer.preview(update).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet());
        applier.apply(update, selected);

        CurrentState state = query.get();

        assertThat(state.type()).isEqualTo("MINIBRAIN_CURRENT");
        assertThat(state.skills()).extracting(CurrentState.Skill::key).containsSubsequence("exp.aa", "exp.zz");
        CurrentState.Skill zeta = state.skills().stream().filter(s -> s.key().equals("exp.zz")).findFirst().orElseThrow();
        assertThat(zeta.name()).isEqualTo(new LocalizedText("Zeta", "Зета"));
        assertThat(zeta.description()).isNull();
        assertThat(zeta.status()).isEqualTo(SkillStatus.LEARNING);

        assertThat(state.relations()).extracting(CurrentState.Relation::from, CurrentState.Relation::to, CurrentState.Relation::type)
                .contains(org.assertj.core.groups.Tuple.tuple("exp.aa", "exp.zz", RelationType.LEADS_TO));
        assertThat(state.evidence()).anySatisfy(e -> {
            assertThat(e.skill()).isEqualTo("exp.zz");
            assertThat(e.text()).isEqualTo(new LocalizedText("Explained it", "Объяснил"));
        });
        assertThat(state.openQuestions()).anySatisfy(q -> {
            assertThat(q.text().en()).isEqualTo("Why?");
            assertThat(q.resolvedAt()).isNull();
        });
        assertThat(state.learningSessions()).anySatisfy(s -> {
            assertThat(s.topic()).isEqualTo("Export test");
            assertThat(s.notes()).isEqualTo(new CurrentState.Notes("# Notes", "# Конспект"));
            assertThat(s.skills()).isSorted();
        });

        // Same knowledge, same file (apart from the export time).
        CurrentState again = query.get();
        assertThat(again).usingRecursiveComparison().ignoringFields("exportedAt").isEqualTo(state);

        mvc.perform(get("/api/exports/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(2))
                .andExpect(jsonPath("$.relations[?(@.from == 'exp.aa')].to").value("exp.zz"));
    }
}
