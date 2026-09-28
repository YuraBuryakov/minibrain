package dev.minibrain.importing.format;

import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;

import java.util.List;

/**
 * current.json (brief §24): everything MiniBrain knows, enough to rebuild the database from this file alone.
 * Flat lists linked by skill keys, never numeric ids. Stored values only: no calculated fields, no UI state,
 * no revision history (that is a separate contract). Timestamps are the stored ISO-8601 UTC strings.
 * Lists are sorted so that the same knowledge always gives the same file (only {@code exportedAt} differs).
 * Durable JSON contract: change the shape only together with {@code schemaVersion}.
 */
public record CurrentState(
        String type,
        int schemaVersion,
        String exportedAt,
        List<Skill> skills,
        List<Relation> relations,
        List<Evidence> evidence,
        List<OpenQuestion> openQuestions,
        List<LearningSession> learningSessions) {

    public static final String TYPE = "MINIBRAIN_CURRENT";
    public static final int SCHEMA_VERSION = 1;

    /** {@code description} is null when the skill has none. */
    public record Skill(String key, LocalizedText name, LocalizedText description, SkillStatus status,
                        String createdAt, String updatedAt) {
    }

    public record Relation(String from, String to, RelationType type, String createdAt) {
    }

    public record Evidence(String skill, LocalizedText text, String createdAt) {
    }

    /** {@code resolvedAt} null = still open. */
    public record OpenQuestion(String skill, LocalizedText text, String createdAt, String resolvedAt) {
    }

    /** {@code notes} markdown per language; {@code skills} = keys of the skills the session was about. */
    public record LearningSession(String topic, Notes notes, List<String> skills, String createdAt) {
    }

    public record Notes(String en, String ru) {
    }
}
