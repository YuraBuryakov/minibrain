package dev.minibrain.skill.domain;

/**
 * A skill an AI proposed (brief §14), waiting for my decision: unlock (becomes a DISCOVERED Skill) or dismiss.
 * Not a Skill: no status, evidence or questions. {@code *Ru} and {@code sourceSkill} may be null.
 */
public record SuggestedSkill(String key, String name, String nameRu, String reason, String reasonRu, String sourceSkill) {
}
