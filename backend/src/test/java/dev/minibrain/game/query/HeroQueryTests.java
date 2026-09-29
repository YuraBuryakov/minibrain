package dev.minibrain.game.query;

import dev.minibrain.game.domain.Achievement;
import dev.minibrain.importing.application.ImportApplier;
import dev.minibrain.importing.application.ImportPreview.Item;
import dev.minibrain.importing.application.ImportPreviewer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Shared in-memory DB: own area prefix ("hq"), compare with the live game state instead of fixed totals.
@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
@AutoConfigureMockMvc
class HeroQueryTests {

    @Autowired
    ImportPreviewer previewer;

    @Autowired
    ImportApplier applier;

    @Autowired
    GameQuery game;

    @Autowired
    HeroQuery hero;

    @Autowired
    MockMvc mvc;

    @Test
    void todaysPointIsTheCurrentXpAndDaysAreAscending() throws Exception {
        String update = """
                { "type": "MINIBRAIN_UPDATE", "schemaVersion": 2,
                  "newSkills": [ { "key": "hq.chart", "name": "Chart", "status": "UNDERSTOOD" } ] }
                """;
        applier.apply(update, previewer.preview(update).items().stream().filter(Item::selected).map(Item::id).collect(Collectors.toSet()));

        var days = hero.get().xpByDay();
        assertThat(days).isNotEmpty();
        assertThat(days.getLast().day()).isEqualTo(LocalDate.now());
        assertThat(days.getLast().xp()).isEqualTo(game.get().player().xp());
        for (int i = 1; i < days.size(); i++) assertThat(days.get(i).day()).isAfter(days.get(i - 1).day());

        var achievements = hero.get().achievements();
        assertThat(achievements).extracting(HeroView.EarnedAchievement::id).containsExactly(Achievement.values());
        assertThat(achievements.getFirst().earnedAt()).isNotNull(); // FIRST_UNDERSTANDING: the UNDERSTOOD skill above

        mvc.perform(get("/api/game/hero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.xpByDay[-1:].day").value(LocalDate.now().toString()));
    }
}
