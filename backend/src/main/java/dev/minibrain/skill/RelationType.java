package dev.minibrain.skill;

public enum RelationType {
    /** Structural: Aggregate PART_OF DDD. */
    PART_OF,
    /** Prerequisite knowledge: Outbox REQUIRES Database Transactions. */
    REQUIRES,
    /** Meaningful link without strict dependency. */
    RELATED_TO,
    /** Logical next learning direction: Aggregate LEADS_TO Domain Event. */
    LEADS_TO
}
