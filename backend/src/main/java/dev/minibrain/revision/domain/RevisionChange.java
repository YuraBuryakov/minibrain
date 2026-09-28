package dev.minibrain.revision.domain;

import java.time.Instant;

/**
 * One meaningful knowledge change, in domain terms (brief §22), not SQL.
 * Unused fields are null; the factories below say which fields each type uses.
 * Statuses and relation types are plain names (e.g. "UNDERSTOOD", "REQUIRES"): the revision module depends on no
 * other module, so skill and importing can write history without a dependency cycle.
 */
public record RevisionChange(
        Type type,
        String skillKey,
        String fromStatus,
        String toStatus,
        String text,
        String relatedKey,
        String relationType,
        Instant occurredAt) {

    public enum Type {
        SKILL_CREATED, SKILL_STATUS_CHANGED, EVIDENCE_ADDED, QUESTION_ADDED, QUESTION_RESOLVED,
        RELATION_ADDED, TRANSLATION_ADDED, NOTES_SAVED
    }

    public static RevisionChange skillCreated(String key, String status, String name) {
        return new RevisionChange(Type.SKILL_CREATED, key, null, status, name, null, null, Instant.now());
    }

    public static RevisionChange statusChanged(String key, String from, String to) {
        return new RevisionChange(Type.SKILL_STATUS_CHANGED, key, from, to, null, null, null, Instant.now());
    }

    public static RevisionChange evidenceAdded(String key, String text) {
        return new RevisionChange(Type.EVIDENCE_ADDED, key, null, null, text, null, null, Instant.now());
    }

    public static RevisionChange questionAdded(String key, String text) {
        return new RevisionChange(Type.QUESTION_ADDED, key, null, null, text, null, null, Instant.now());
    }

    public static RevisionChange questionResolved(String key, String text) {
        return new RevisionChange(Type.QUESTION_RESOLVED, key, null, null, text, null, null, Instant.now());
    }

    public static RevisionChange relationAdded(String from, String relationType, String to) {
        return new RevisionChange(Type.RELATION_ADDED, from, null, null, null, to, relationType, Instant.now());
    }

    /** {@code text}: what was translated, e.g. "name: Сага". */
    public static RevisionChange translationAdded(String key, String text) {
        return new RevisionChange(Type.TRANSLATION_ADDED, key, null, null, text, null, null, Instant.now());
    }

    public static RevisionChange notesSaved(String topic) {
        return new RevisionChange(Type.NOTES_SAVED, null, null, null, topic, null, null, Instant.now());
    }
}
