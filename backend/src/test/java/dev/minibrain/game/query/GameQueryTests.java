package dev.minibrain.game.query;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The in-memory DB is shared between test classes: compare before / after and use an own area prefix ("gq").
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class GameQueryTests {

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    ImportApplier applier;

    @Autowired
    GameQuery game;

    @Autowired
    MockMvc mvc;

    @Test
    void anImportRaisesXpOfItsAreaAndTheCharacter() throws Exception {
        int before = game.get().player().xp();
        String update = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "newSkills": [ { "key": "gq", "name": "Game", "status": "UNDERSTOOD" },
                                 { "key": "gq.xp", "name": "Xp", "status": "LEARNING" } ],
                  "changes": [ { "skill": "gq.xp", "evidenceAdded": ["Explained it"] } ] }
                """;
        applier.apply(update, previewer.preview(update).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet()));

        GameState after = game.get();
        assertThat(after.player().xp() - before).isEqualTo(30 + 10 + 5);
        assertThat(after.areas()).contains(new GameState.Area("gq", 45, 2, false));
        assertThat(after.player().nextLevelXp()).isGreaterThan(after.player().xp());
        assertThat(after.player().talentPoints()).isEqualTo(after.player().level() - 1);

        mvc.perform(get("/api/game"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areas[?(@.key == 'gq')].rank").value(2))
                .andExpect(jsonPath("$.player.title").isString());
    }
}
