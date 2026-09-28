package dev.minibrain.skill.domain;

import java.time.Instant;

/** A current knowledge gap of a Skill. {@code resolvedAt} is null while the question is open. */
public record OpenQuestion(long id, String text, Instant createdAt, Instant resolvedAt) {
}
