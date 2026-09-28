package dev.minibrain.importing.format;

import java.util.List;

/**
 * MINIBRAIN_UPDATE as it arrives from an AI (brief §18): a 1:1 mirror of the JSON, nothing more.
 * Enum-like values stay Strings on purpose: an unknown status must invalidate one item, not the whole file.
 * Any field may be missing (null); unknown fields are ignored.
 * schemaVersion 2 makes knowledge texts bilingual ({@link LocalizedText}); version 1 files (plain strings) still load.
 */
public record UpdateDocument(
        String type,
        Integer schemaVersion,
        Session session,
        Notes notes,
        List<SkillChange> changes,
        List<NewSkill> newSkills,
        List<NewRelation> newRelations,
        List<SuggestedSkill> suggestedSkills) {

    public static final String TYPE = "MINIBRAIN_UPDATE";
    public static final List<Integer> SUPPORTED_VERSIONS = List.of(1, 2);

    /** {@code notes} is also accepted here; AIs put it in either place. */
    public record Session(String topic, Notes notes) {
    }

    /** Bilingual study notes (markdown). */
    public record Notes(String en, String ru) {
    }

    /** {@code openQuestionsResolved} matches an open question by its English or its Russian text. */
    public record SkillChange(
            String skill,
            String proposedStatus,
            List<LocalizedText> evidenceAdded,
            List<LocalizedText> openQuestionsAdded,
            List<LocalizedText> openQuestionsResolved) {
    }

    public record NewSkill(String key, LocalizedText name, LocalizedText description, String status, String reason) {
    }

    public record NewRelation(String from, String type, String to) {
    }

    public record SuggestedSkill(String key, LocalizedText name, String reason) {
    }
}
