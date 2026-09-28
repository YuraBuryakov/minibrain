package dev.minibrain.game.web;

import dev.minibrain.game.query.GameQuery;
import dev.minibrain.game.query.GameState;
import dev.minibrain.game.query.Quest;
import dev.minibrain.game.query.QuestsQuery;
import dev.minibrain.skill.application.SuggestionUnlocker;
import dev.minibrain.skill.domain.Skill;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
public class GameController {

    private final GameQuery game;
    private final SuggestionUnlocker unlocker;
    private final QuestsQuery quests;

    public GameController(GameQuery game, SuggestionUnlocker unlocker, QuestsQuery quests) {
        this.game = game;
        this.unlocker = unlocker;
        this.quests = quests;
    }

    @GetMapping("/api/game")
    public GameState game() {
        return game.get();
    }

    /** Every open question on the map (docs/game-design.md §10). Finished only through an AI session. */
    @GetMapping("/api/quests")
    public List<Quest> quests() {
        return quests.all();
    }

    /** Spends a talent point to open a topic from the fog (docs/game-design.md §6). */
    @PostMapping("/api/game/unlock/{key}")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Skill unlock(@PathVariable String key) {
        if (game.get().player().talentPoints() < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no talent point: reach the next level");
        }
        return unlocker.unlock(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no open suggestion: " + key));
    }
}
