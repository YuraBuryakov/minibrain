package dev.minibrain.importing.format;

import java.util.List;

/**
 * MINIBRAIN_UPDATE as it arrives from an AI (brief §18): a 1:1 mirror of the JSON, nothing more.
 * Enum-like values stay Strings on purpose: an unknown status must invalidate one item, not the whole file.
 * Any field may be missing (null); unknown fields are ignored.
 */
public record UpdateDocument(
        String type,
        Integer schemaVersion,
        Session session,
        List<SkillChange> changes,
        List<NewSkill> newSkills,
        List<NewRelation> newRelations,
        List<SuggestedSkill> suggestedSkills) {

    public static final String TYPE = "MINIBRAIN_UPDATE";
    public static final int SCHEMA_VERSION = 1;

    public record Session(String topic) {
    }

    public record SkillChange(
            String skill,
            String proposedStatus,
            List<String> evidenceAdded,
            List<String> openQuestionsAdded,
            List<String> openQuestionsResolved) {
    }

    public record NewSkill(String key, String name, String description, String status, String reason) {
    }

    public record NewRelation(String from, String type, String to) {
    }

    public record SuggestedSkill(String key, String name, String reason) {
    }
}
