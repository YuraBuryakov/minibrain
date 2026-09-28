package dev.minibrain.skill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
class SkillRepositoryTests {

    @Autowired
    SkillRepository skills;

    @Autowired
    JdbcClient jdbc;

    @Test
    void createsSkillRow() {
        Skill skill = skills.create("ddd.aggregate", "Aggregate", null, SkillStatus.LEARNING);

        assertThat(skill.id()).isPositive();
        String row = jdbc.sql("SELECT key || '|' || status || '|' || created_at FROM skill WHERE id = ?")
                .param(skill.id())
                .query(String.class)
                .single();
        assertThat(row).matches("ddd\\.aggregate\\|LEARNING\\|\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
    }

    @Test
    void readsBackWhatWasCreated() {
        Skill created = skills.create("ddd.invariant", "Invariant", "Rule that must always hold", SkillStatus.UNDERSTOOD);

        assertThat(skills.findByKey("ddd.invariant")).contains(created);
        assertThat(skills.findAll()).contains(created);
    }

    @Test
    void findByKeyReturnsEmptyForUnknownKey() {
        assertThat(skills.findByKey("no.such-skill")).isEmpty();
    }

    @Test
    void rejectsDuplicateKey() {
        skills.create("messaging.outbox", "Outbox", null, SkillStatus.DISCOVERED);

        assertThatThrownBy(() -> skills.create("messaging.outbox", "Outbox again", null, SkillStatus.DISCOVERED))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void databaseRejectsUnknownStatus() {
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO skill (key, name, status, created_at, updated_at)
                        VALUES ('x.y', 'X', 'GURU', '2026-01-01T00:00:00.000Z', '2026-01-01T00:00:00.000Z')
                        """).update())
                .isInstanceOf(DataAccessException.class);
    }
}
