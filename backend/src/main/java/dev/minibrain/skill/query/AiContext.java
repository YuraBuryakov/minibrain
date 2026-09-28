package dev.minibrain.skill.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;

import java.util.List;

/**
 * MINIBRAIN_CONTEXT (brief §16): a small topic-specific context handed to an AI before a learning session.
 * Durable JSON contract: change the shape only together with {@code schemaVersion}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // no "goal": null when there is no goal
public record AiContext(
        String type,
        int schemaVersion,
        Focus focus,
        List<String> evidence,
        List<String> openQuestions,
        List<RelatedSkill> relatedSkills,
        List<KnownSkill> knownSkills,
        List<KnownSkill> suggestedSkills,
        String goal) {

    public static final String TYPE = "MINIBRAIN_CONTEXT";
    public static final int SCHEMA_VERSION = 2; // 2: suggestedSkills

    public record Focus(String key, String name, SkillStatus status) {
    }

    /**
     * {@code relation} + {@code direction}: "outgoing" reads "focus REQUIRES this skill",
     * "incoming" reads "this skill REQUIRES focus". (Extension of the brief example, which has no relation.)
     */
    /**
     * Every skill in MiniBrain, key + name only (not the graph: no evidence, no relations).
     * Lets the AI link new skills to existing ones and avoid duplicates. (Extension of the brief example.)
     */
    /** Also used for {@code suggestedSkills}: topics already waiting in the fog, so the AI does not suggest them again. */
    public record KnownSkill(String key, String name) {
    }

    public record RelatedSkill(String key, String name, SkillStatus status, RelationType relation, String direction) {
    }
}
