package dev.minibrain.skill.query;

import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;

import java.time.Instant;
import java.util.List;

/** Read model for the Skill card (brief §47 GetSkillDetails): everything the card shows, in one shape. */
public record SkillDetails(
        String key,
        String name,
        String description,
        SkillStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<Evidence> evidence,
        List<Question> openQuestions,
        List<Relation> relations) {

    public record Evidence(String text, Instant createdAt) {
    }

    /** {@code resolvedAt} is null while the question is still open. */
    public record Question(String text, Instant createdAt, Instant resolvedAt) {
    }

    /**
     * A relation seen from this skill. {@code outgoing}: this skill is the "from" side.
     * key / name / status describe the skill on the other end, so the card can show and link it.
     */
    public record Relation(RelationType type, boolean outgoing, String key, String name, SkillStatus status) {
    }
}
