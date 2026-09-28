package dev.minibrain.skill;

import java.time.Instant;

/**
 * A unit of learning. {@code id} is the database identity,
 * {@code key} (e.g. "ddd.aggregate") is the stable external identity used in JSON.
 */
public record Skill(
        long id,
        String key,
        String name,
        String description,
        SkillStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
