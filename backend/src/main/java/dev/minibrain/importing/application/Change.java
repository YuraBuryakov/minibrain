package dev.minibrain.importing.application;

import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.SkillStatus;

/**
 * One validated knowledge change an import can apply. Sealed: the compiler knows every kind,
 * so Apply (step 13) can switch over them exhaustively.
 */
public sealed interface Change {

    record ChangeStatus(String skill, SkillStatus from, SkillStatus to) implements Change {
    }

    record AddEvidence(String skill, String text) implements Change {
    }

    record AddQuestion(String skill, String text) implements Change {
    }

    record ResolveQuestion(String skill, String text) implements Change {
    }

    record CreateSkill(String key, String name, String description, SkillStatus status) implements Change {
    }

    record AddRelation(String from, RelationType type, String to) implements Change {
    }

    record SuggestSkill(String key, String name, String reason) implements Change {
    }
}
