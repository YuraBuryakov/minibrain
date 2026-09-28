package dev.minibrain.skill.domain;

import java.time.Instant;

/** What the user demonstrated about a Skill (not what AI explained). Plain text in MVP. */
public record Evidence(long id, String text, Instant createdAt) {
}
