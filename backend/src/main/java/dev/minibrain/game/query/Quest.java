package dev.minibrain.game.query;

/** One open question seen as a quest (docs/game-design.md §10). *Ru: optional Russian version (null = use English). */
public record Quest(String area, String skillKey, String skillName, String skillNameRu,
                    String question, String questionRu, int xp) {
}
