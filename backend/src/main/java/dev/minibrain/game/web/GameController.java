package dev.minibrain.game.web;

import dev.minibrain.game.query.GameQuery;
import dev.minibrain.game.query.GameState;
import dev.minibrain.game.query.HeroQuery;
import dev.minibrain.game.query.HeroView;
import dev.minibrain.game.query.Quest;
import dev.minibrain.game.query.QuestsQuery;
import dev.minibrain.game.domain.GameRules;
import dev.minibrain.skill.application.SuggestionUnlocker;
import dev.minibrain.skill.persistence.SkillRepository;
import dev.minibrain.skill.persistence.SuggestedSkillRepository;
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
import java.util.Optional;

@RestController
public class GameController {

    private final GameQuery game;
    private final SuggestionUnlocker unlocker;
    private final QuestsQuery quests;
    private final SuggestedSkillRepository suggestions;
    private final SkillRepository skills;
    private final HeroQuery hero;

    public GameController(GameQuery game, SuggestionUnlocker unlocker, QuestsQuery quests,
                          SuggestedSkillRepository suggestions, SkillRepository skills, HeroQuery hero) {
        this.game = game;
        this.unlocker = unlocker;
        this.quests = quests;
        this.suggestions = suggestions;
        this.skills = skills;
        this.hero = hero;
    }

    @GetMapping("/api/game")
    public GameState game() {
        return game.get();
    }

    /** The hero window (docs/game-design.md §11): XP over time. Fetched only while the window is open. */
    @GetMapping("/api/game/hero")
    public HeroView hero() {
        return hero.get();
    }

    /** Every open question on the map (docs/game-design.md §10). Finished only through an AI session. */
    @GetMapping("/api/quests")
    public List<Quest> quests() {
        return quests.all();
    }

    /**
     * Spends a talent point to open a topic from the fog (docs/game-design.md §6). Mastery gate: its source skill
     * must be UNDERSTOOD or higher; a suggestion without an existing source has no gate.
     */
    @PostMapping("/api/game/unlock/{key}")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Skill unlock(@PathVariable String key) {
        if (game.get().player().talentPoints() < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "no talent point: reach the next level");
        }
        suggestions.findOpen(key)
                .flatMap(s -> s.sourceSkill() == null ? Optional.empty() : skills.findByKey(s.sourceSkill()))
                .filter(source -> !GameRules.opensTheFog(source.status().name()))
                .ifPresent(source -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "master " + source.key() + " first: reach " + GameRules.UNLOCK_STATUS);
                });
        return unlocker.unlock(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no open suggestion: " + key));
    }
}
