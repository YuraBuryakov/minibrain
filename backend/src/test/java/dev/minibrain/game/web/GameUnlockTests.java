package dev.minibrain.game.web;

import dev.minibrain.game.domain.GameRules;
import dev.minibrain.game.query.GameQuery;
import dev.minibrain.game.query.GameState;
import dev.minibrain.importing.application.ImportApplier;
import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreviewer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Own context with a mocked GameQuery, so the talent point balance is known.
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class GameUnlockTests {

    @MockitoBean
    GameQuery game;

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    ImportApplier applier;

    @Autowired
    MockMvc mvc;

    @Test
    void unlockingWithoutATalentPointIsRefused() throws Exception {
        givenTalentPoints(0);

        mvc.perform(post("/api/game/unlock/any.topic")).andExpect(status().isConflict());
    }

    @Test
    void aTopicOpensOnlyWhenItsSourceSkillIsUnderstood() throws Exception {
        givenTalentPoints(5);
        apply("""
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "newSkills": [ { "key": "gu", "name": "Gate", "status": "LEARNING" } ],
                  "suggestedSkills": [ { "key": "gu.next", "name": "Next", "from": "gu" },
                                       { "key": "gu.free", "name": "Free" } ] }
                """);

        mvc.perform(post("/api/game/unlock/gu.next")).andExpect(status().isConflict());
        mvc.perform(post("/api/game/unlock/gu.free")).andExpect(status().isCreated()); // no source, no gate

        apply("""
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "changes": [ { "skill": "gu", "proposedStatus": "UNDERSTOOD" } ] }
                """);
        mvc.perform(post("/api/game/unlock/gu.next")).andExpect(status().isCreated());
    }

    private void givenTalentPoints(int points) {
        when(game.get()).thenReturn(new GameState(
                new GameState.Player(0, 1, 0, 25, GameRules.Title.STUDENT, points, 60), List.of(), GameRules.UNLOCK_STATUS));
    }

    private void apply(String update) {
        applier.apply(update, previewer.preview(update).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet()));
    }
}
