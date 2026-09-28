package dev.minibrain.importing.application;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * What would change if Apply were pressed (brief §20). Nothing is written while a preview is built.
 * {@code documentIssues}: problems with the file as a whole (then {@code items} is empty).
 */
public record ImportPreview(String topic, List<Issue> documentIssues, List<Item> items) {

    /** Sections of the preview screen, in display order. */
    public enum Section { NEW_SKILLS, STATUS_CHANGES, EVIDENCE, OPEN_QUESTIONS, RELATIONS, SUGGESTED_SKILLS }

    /** READY: can be applied. ALREADY_PRESENT: MiniBrain already has it, skipped. INVALID: has an error. */
    public enum Verdict { READY, ALREADY_PRESENT, INVALID }

    /**
     * One line of the preview. {@code label} is display text; {@code change} (null when invalid) is what Apply uses
     * and stays on the server. {@code selected}: pre-ticked in the UI.
     */
    public record Item(
            int id,
            Section section,
            String skill,
            String label,
            Verdict verdict,
            List<Issue> issues,
            boolean selected,
            @JsonIgnore Change change) {
    }

    public record Issue(Code code, Severity severity, String message) {

        public enum Severity { ERROR, WARNING }

        /** Brief §21 conflict types plus a few file-level and consistency checks. */
        public enum Code {
            PARSE_ERROR,
            UNSUPPORTED_DOCUMENT,
            INVALID_VALUE,
            UNKNOWN_SKILL_REFERENCE,
            DUPLICATE_SKILL_KEY,
            INVALID_RELATION,
            UNKNOWN_OPEN_QUESTION,
            STATUS_DOWNGRADE,
            ORPHAN_SKILL,
            REDUNDANT_RELATION
        }

        static Issue error(Code code, String message) {
            return new Issue(code, Severity.ERROR, message);
        }

        static Issue warning(Code code, String message) {
            return new Issue(code, Severity.WARNING, message);
        }
    }
}
