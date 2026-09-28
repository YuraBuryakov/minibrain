package dev.minibrain.skill.query;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * MINIBRAIN_TRANSLATION_REQUEST: English knowledge texts that have no Russian version yet, grouped by skill.
 * Handed to an AI, which answers with a MINIBRAIN_UPDATE carrying a "translations" section.
 * Only missing texts are listed; a skill with nothing missing is left out.
 */
public record TranslationRequest(String type, int schemaVersion, List<SkillTexts> skills) {

    public static final String TYPE = "MINIBRAIN_TRANSLATION_REQUEST";
    public static final int SCHEMA_VERSION = 1;

    /** {@code name} / {@code description} are present only when their Russian version is missing. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record SkillTexts(String key, String name, String description, List<String> evidence, List<String> openQuestions) {
    }
}
