package dev.minibrain.skill.domain;

/** Directed relation between two Skills, addressed by their stable keys: {@code from TYPE to}. */
public record SkillRelation(String from, RelationType type, String to) {
}
