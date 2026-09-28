package dev.minibrain.skill.web;

import dev.minibrain.skill.persistence.SuggestedSkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dismissing suggested skills (brief §14). Unlocking costs a talent point and lives in the game module
 * ({@code POST /api/game/unlock/{key}}), which calls {@code SuggestionUnlocker}.
 */
@RestController
@RequestMapping("/api/suggestions/{key}")
public class SuggestionController {

    private final SuggestedSkillRepository suggestions;

    public SuggestionController(SuggestedSkillRepository suggestions) {
        this.suggestions = suggestions;
    }

    /** Hides the suggestion. Not a knowledge change, so no revision. */
    @PostMapping("/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable String key) {
        suggestions.findOpen(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no open suggestion: " + key));
        suggestions.dismiss(key);
    }
}
