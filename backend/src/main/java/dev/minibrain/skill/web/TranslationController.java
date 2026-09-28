package dev.minibrain.skill.web;

import dev.minibrain.skill.query.TranslationRequest;
import dev.minibrain.skill.query.TranslationRequestQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TranslationController {

    private final TranslationRequestQuery query;

    public TranslationController(TranslationRequestQuery query) {
        this.query = query;
    }

    /** Untranslated texts for an AI; {@code skill} limits it to one skill. */
    @GetMapping("/api/translations/missing")
    public TranslationRequest missing(@RequestParam(required = false) String skill) {
        return query.missing(skill);
    }
}
