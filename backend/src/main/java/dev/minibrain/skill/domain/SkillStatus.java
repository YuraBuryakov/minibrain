package dev.minibrain.skill.domain;

/** Main learning status of a Skill. Not a state machine: any transition is allowed. */
public enum SkillStatus {
    DISCOVERED,
    LEARNING,
    UNDERSTOOD,
    APPLIED,
    MASTERED
}
