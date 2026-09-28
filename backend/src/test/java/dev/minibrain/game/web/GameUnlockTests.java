package dev.minibrain.game.web;

import dev.minibrain.game.domain.GameRules;
import dev.minibrain.game.query.GameQuery;
import dev.minibrain.game.query.GameState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
    MockMvc mvc;

    @Test
    void unlockingWithoutATalentPointIsRefused() throws Exception {
        when(game.get()).thenReturn(new GameState(
                new GameState.Player(0, 1, 0, 25, GameRules.Title.STUDENT, 0, 60), List.of()));

        mvc.perform(post("/api/game/unlock/any.topic")).andExpect(status().isConflict());
    }
}
