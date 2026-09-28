package dev.minibrain.game.query;

import dev.minibrain.game.domain.GameRules;
import dev.minibrain.importing.application.ImportApplier;
import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreviewer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The in-memory DB is shared between test classes: only look at the own area prefix ("qq").
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class QuestsQueryTests {

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    ImportApplier applier;

    @Autowired
    QuestsQuery quests;

    @Autowired
    MockMvc mvc;

    @Test
    void listsOnlyOpenQuestionsOrderedBySkillThenOldestFirst() throws Exception {
        apply("""
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "newSkills": [ { "key": "qq", "name": "Quests", "status": "LEARNING" },
                                 { "key": "qq.b", "name": "Bravo", "status": "LEARNING" },
                                 { "key": "qq.a", "name": "Alpha", "status": "LEARNING" } ],
                  "changes": [ { "skill": "qq.b", "openQuestionsAdded": ["Why B?"] },
                               { "skill": "qq.a", "openQuestionsAdded": ["Why A?", "Done A?"] } ] }
                """);
        apply("""
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "changes": [ { "skill": "qq.a", "openQuestionsResolved": ["Done A?"] } ] }
                """);

        var mine = quests.all().stream().filter(q -> q.area().equals("qq")).toList();
        assertThat(mine).extracting(Quest::skillKey, Quest::question)
                .containsExactly(tuple("qq.a", "Why A?"), tuple("qq.b", "Why B?"));
        assertThat(mine).allMatch(q -> q.xp() == GameRules.QUESTION_XP);

        mvc.perform(get("/api/quests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.question == 'Why B?')].skillName").value("Bravo"));
    }

    private void apply(String update) {
        applier.apply(update, previewer.preview(update).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet()));
    }
}
