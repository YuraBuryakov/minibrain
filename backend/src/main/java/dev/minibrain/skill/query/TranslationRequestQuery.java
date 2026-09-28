package dev.minibrain.skill.query;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read side: every English knowledge text without a Russian version, optionally for one skill only. */
@Component
public class TranslationRequestQuery {

    private final JdbcClient jdbc;

    public TranslationRequestQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code skillKey} null = all skills. */
    public TranslationRequest missing(String skillKey) {
        // key -> [name, description, evidence, questions]; LinkedHashMap keeps the skills ordered by key.
        Map<String, Missing> bySkill = new LinkedHashMap<>();

        jdbc.sql("""
                        SELECT key,
                               CASE WHEN name_ru IS NULL THEN name END AS name,
                               CASE WHEN description IS NOT NULL AND description_ru IS NULL THEN description END AS description
                        FROM skill
                        WHERE (:key IS NULL OR key = :key)
                        ORDER BY key
                        """)
                .param("key", skillKey)
                .query((rs, rowNum) -> bySkill.put(rs.getString("key"), new Missing(rs.getString("name"), rs.getString("description"))))
                .list();

        collect("evidence", skillKey, bySkill, true);
        collect("open_question", skillKey, bySkill, false);

        List<TranslationRequest.SkillTexts> skills = bySkill.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> new TranslationRequest.SkillTexts(e.getKey(), e.getValue().name, e.getValue().description,
                        e.getValue().evidence, e.getValue().questions))
                .toList();
        return new TranslationRequest(TranslationRequest.TYPE, TranslationRequest.SCHEMA_VERSION, skills);
    }

    private void collect(String table, String skillKey, Map<String, Missing> bySkill, boolean evidence) {
        // The table name is one of two constants above, never user input.
        jdbc.sql("SELECT k.key, t.text FROM " + table + " t JOIN skill k ON k.id = t.skill_id"
                        + " WHERE t.text_ru IS NULL AND (:key IS NULL OR k.key = :key) ORDER BY t.id")
                .param("key", skillKey)
                .query((rs, rowNum) -> {
                    Missing missing = bySkill.get(rs.getString("key"));
                    (evidence ? missing.evidence : missing.questions).add(rs.getString("text"));
                    return null;
                })
                .list();
    }

    private static final class Missing {
        final String name;
        final String description;
        final List<String> evidence = new ArrayList<>();
        final List<String> questions = new ArrayList<>();

        Missing(String name, String description) {
            this.name = name;
            this.description = description;
        }

        boolean isEmpty() {
            return name == null && description == null && evidence.isEmpty() && questions.isEmpty();
        }
    }
}
