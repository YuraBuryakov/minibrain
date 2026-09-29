package dev.minibrain.game.domain;

import dev.minibrain.game.domain.GameReplay.Event;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameRulesTests {

    @Test
    void xpComesFromStatusCappedEvidenceAndResolvedQuestions() {
        assertThat(GameRules.skillXp("DISCOVERED", 0, 0)).isZero();
        assertThat(GameRules.skillXp("UNDERSTOOD", 2, 1)).isEqualTo(30 + 10 + 8);
        assertThat(GameRules.skillXp("MASTERED", 9, 0)).isEqualTo(100 + 25); // evidence capped at 5
    }

    @Test
    void levelCurveStartsLevelLAt25TimesLMinusOneSquared() {
        assertThat(GameRules.levelFor(0)).isEqualTo(1);
        assertThat(GameRules.levelFor(24)).isEqualTo(1);
        assertThat(GameRules.levelFor(25)).isEqualTo(2);
        assertThat(GameRules.levelFor(99)).isEqualTo(2);
        assertThat(GameRules.levelFor(100)).isEqualTo(3);
        assertThat(GameRules.xpForLevel(4)).isEqualTo(225);
    }

    @Test
    void titlesFollowLevelBands() {
        assertThat(GameRules.titleFor(2)).isEqualTo(GameRules.Title.STUDENT);
        assertThat(GameRules.titleFor(3)).isEqualTo(GameRules.Title.JOURNEYMAN);
        assertThat(GameRules.titleFor(9)).isEqualTo(GameRules.Title.SCHOLAR);
        assertThat(GameRules.titleFor(10)).isEqualTo(GameRules.Title.ARCHITECT);
        assertThat(GameRules.titleFor(15)).isEqualTo(GameRules.Title.MAGISTER);
    }

    @Test
    void visionGrowsThirtyPerLevelUpTo200() {
        assertThat(GameRules.vision(1)).isEqualTo(60);
        assertThat(GameRules.vision(3)).isEqualTo(120);
        assertThat(GameRules.vision(6)).isEqualTo(200);
        assertThat(GameRules.vision(12)).isEqualTo(200);
    }

    @Test
    void anUnlockCreatesTheSkillAndIsCounted() {
        var replay = new GameReplay();
        replay.apply(new Event("SKILL_UNLOCKED", "ddd.value-object", "DISCOVERED"));
        replay.apply(new Event("SKILL_STATUS_CHANGED", "ddd.value-object", "LEARNING"));
        assertThat(replay.unlocks()).isEqualTo(1);
        assertThat(replay.totalXp()).isEqualTo(10);
    }

    @Test
    void onlyAnUnderstoodOrHigherSourceOpensTheFog() {
        assertThat(GameRules.opensTheFog("LEARNING")).isFalse();
        assertThat(GameRules.opensTheFog("UNDERSTOOD")).isTrue();
        assertThat(GameRules.opensTheFog("MASTERED")).isTrue();
    }

    @Test
    void replaySumsSkillsIntoAreasAndLosesXpWhenAStatusGoesDown() {
        var replay = new GameReplay();
        replay.apply(new Event("SKILL_CREATED", "ddd", "LEARNING"));
        replay.apply(new Event("SKILL_CREATED", "ddd.aggregate", "UNDERSTOOD"));
        replay.apply(new Event("EVIDENCE_ADDED", "ddd.aggregate", null));
        replay.apply(new Event("QUESTION_RESOLVED", "ddd.aggregate", null));
        replay.apply(new Event("SKILL_CREATED", "spring.boot", "APPLIED"));
        replay.apply(new Event("RELATION_ADDED", "ddd.aggregate", null)); // ignored

        assertThat(replay.areaXp()).containsEntry("ddd", 10 + 30 + 5 + 8).containsEntry("spring", 60);
        assertThat(replay.totalXp()).isEqualTo(113);

        replay.apply(new Event("SKILL_STATUS_CHANGED", "spring.boot", "LEARNING"));
        assertThat(replay.totalXp()).isEqualTo(63);
    }

    @Test
    void anAreaOfThreeUnderstoodSkillsIsAConstellationAndLosesItWhenOneGoesDown() {
        var replay = new GameReplay();
        replay.apply(new Event("SKILL_CREATED", "ddd", "UNDERSTOOD"));
        replay.apply(new Event("SKILL_CREATED", "ddd.aggregate", "APPLIED"));
        replay.apply(new Event("SKILL_CREATED", "spring.boot", "MASTERED"));
        replay.apply(new Event("SKILL_CREATED", "spring.web", "MASTERED"));
        assertThat(replay.constellations()).isEmpty(); // two skills are not enough

        replay.apply(new Event("SKILL_CREATED", "ddd.entity", "LEARNING"));
        assertThat(replay.constellations()).isEmpty(); // one skill below UNDERSTOOD

        replay.apply(new Event("SKILL_STATUS_CHANGED", "ddd.entity", "UNDERSTOOD"));
        assertThat(replay.constellations()).containsExactly("ddd");

        replay.apply(new Event("SKILL_STATUS_CHANGED", "ddd.aggregate", "LEARNING"));
        assertThat(replay.constellations()).isEmpty();
    }

    @Test
    void aSnapshotFreezesTheStateSoFarAndLaterChangesDoNotTouchIt() {
        var replay = new GameReplay();
        replay.apply(new Event("SKILL_CREATED", "ddd.aggregate", "UNDERSTOOD"));
        var first = replay.snapshot();

        replay.apply(new Event("SKILL_CREATED", "ddd.entity", "APPLIED"));
        replay.apply(new Event("SKILL_STATUS_CHANGED", "ddd.aggregate", "LEARNING"));
        var second = replay.snapshot();

        assertThat(first.xp()).isEqualTo(30);
        assertThat(first.level()).isEqualTo(2);
        assertThat(first.areaXp()).containsExactly(java.util.Map.entry("ddd", 30));
        assertThat(second.xp()).isEqualTo(70);
        assertThat(second.areaXp()).containsExactly(java.util.Map.entry("ddd", 70));
        assertThat(second.constellations()).isEmpty();
    }

    @Test
    void achievementsFollowTheSnapshotCountersAndNotesWithoutASkillCount() {
        var replay = new GameReplay();
        replay.apply(new Event("SKILL_CREATED", "ddd.aggregate", "LEARNING"));
        assertThat(earned(replay.snapshot())).isEmpty();

        replay.apply(new Event("SKILL_STATUS_CHANGED", "ddd.aggregate", "APPLIED"));
        replay.apply(new Event("SKILL_UNLOCKED", "ddd.entity", "DISCOVERED"));
        replay.apply(new Event("NOTES_SAVED", null, null));
        var s = replay.snapshot();
        assertThat(s.notes()).isEqualTo(1);
        assertThat(earned(s)).containsExactly(Achievement.FIRST_UNDERSTANDING, Achievement.HANDS_ON, Achievement.PATHFINDER);
    }

    private static java.util.List<Achievement> earned(GameReplay.Snapshot s) {
        return java.util.Arrays.stream(Achievement.values()).filter(a -> a.earnedBy(s)).toList();
    }

    @Test
    void deedsBetweenTwoMomentsIncludeLossesAndUnlockedTopics() {
        var replay = new GameReplay();
        var empty = replay.snapshot();
        replay.apply(new Event("SKILL_CREATED", "mq", "UNDERSTOOD"));
        replay.apply(new Event("SKILL_CREATED", "mq.a", "UNDERSTOOD"));
        replay.apply(new Event("SKILL_CREATED", "mq.b", "UNDERSTOOD"));
        var formed = replay.snapshot(); // 90 XP: level 2, rank 2, a constellation
        assertThat(Deed.between(empty, formed)).containsExactly(
                new Deed(Deed.Kind.LEVEL_UP, null, 2),
                new Deed(Deed.Kind.RANK_UP, "mq", 2),
                new Deed(Deed.Kind.CONSTELLATION_FORMED, "mq", 0));

        replay.apply(new Event("SKILL_STATUS_CHANGED", "mq.b", "DISCOVERED"));
        replay.apply(new Event("SKILL_UNLOCKED", "mq.c", "DISCOVERED"));
        var lost = replay.snapshot(); // 60 XP: still level 2 and rank 2, the constellation is lost
        assertThat(Deed.between(formed, lost)).containsExactly(
                new Deed(Deed.Kind.CONSTELLATION_LOST, "mq", 0),
                new Deed(Deed.Kind.TOPIC_UNLOCKED, "mq.c", 0));
    }
}
