package dev.minibrain.importing.application;

import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;

/**
 * One validated knowledge change an import can apply. Sealed: the compiler knows every kind,
 * so Apply (step 13) can switch over them exhaustively. {@code *Ru}: optional Russian version (step 13c).
 */
public sealed interface Change {

    record ChangeStatus(String skill, SkillStatus from, SkillStatus to) implements Change {
    }

    record AddEvidence(String skill, String text, String textRu) implements Change {
    }

    record AddQuestion(String skill, String text, String textRu) implements Change {
    }

    /** {@code text}: the stored (English) text of the open question, whichever language the file used. */
    record ResolveQuestion(String skill, String text) implements Change {
    }

    record CreateSkill(String key, String name, String nameRu, String description, String descriptionRu, SkillStatus status) implements Change {
    }

    record AddRelation(String from, RelationType type, String to) implements Change {
    }

    /** Stored as a suggestion (fog), not as a skill: unlocking happens later, on the map. */
    record SuggestSkill(String key, String name, String nameRu, String reason, String reasonRu, String from) implements Change {
    }

    /** Sets the Russian version of one existing text; {@code original} is its stored English text. */
    record Translate(Target target, String skill, String original, String ru) implements Change {
        public enum Target { NAME, DESCRIPTION, EVIDENCE, QUESTION }
    }

    /** Bilingual study notes of the session, linked to the skills this import touches. */
    record SaveSessionNotes(String topic, String notesEn, String notesRu) implements Change {
    }
}
