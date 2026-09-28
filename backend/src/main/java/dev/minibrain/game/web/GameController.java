package dev.minibrain.game.web;

import dev.minibrain.game.query.GameQuery;
import dev.minibrain.game.query.GameState;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GameController {

    private final GameQuery game;

    public GameController(GameQuery game) {
        this.game = game;
    }

    @GetMapping("/api/game")
    public GameState game() {
        return game.get();
    }
}
