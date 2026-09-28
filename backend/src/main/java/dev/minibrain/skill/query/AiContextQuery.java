package dev.minibrain.skill.query;

import dev.minibrain.skill.domain.RelationType;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Builds MINIBRAIN_CONTEXT from the Skill card read model: selects and trims, no SQL of its own.
 * Selection (brief §16): newest evidence, open questions only, a few most useful related skills.
 */
@Component
public class AiContextQuery {

    static final int MAX_EVIDENCE = 5;
    static final int MAX_RELATED = 7;

    private final SkillDetailsQuery details;
    private final KnowledgeGraphQuery graph;

    public AiContextQuery(SkillDetailsQuery details, KnowledgeGraphQuery graph) {
        this.details = details;
        this.graph = graph;
    }

    public Optional<AiContext> find(String key, String goal) {
        KnowledgeGraph map = graph.get();
        return details.find(key).map(skill -> new AiContext(
                AiContext.TYPE,
                AiContext.SCHEMA_VERSION,
                new AiContext.Focus(skill.key(), skill.name(), skill.status()),
                newest(skill.evidence().stream().map(SkillDetails.Evidence::text).toList(), MAX_EVIDENCE),
                skill.openQuestions().stream().filter(q -> q.resolvedAt() == null).map(SkillDetails.Question::text).toList(),
                skill.relations().stream()
                        .sorted(Comparator.comparingInt(AiContextQuery::usefulness))
                        .limit(MAX_RELATED)
                        .map(r -> new AiContext.RelatedSkill(r.key(), r.name(), r.status(), r.type(), r.outgoing() ? "outgoing" : "incoming"))
                        .toList(),
                // ponytail: all skills; limit to the focus area once the map holds hundreds of skills.
                map.nodes().stream().map(n -> new AiContext.KnownSkill(n.key(), n.name())).toList(),
                map.suggestions().stream().map(s -> new AiContext.KnownSkill(s.key(), s.name())).toList(),
                goal == null || goal.isBlank() ? null : goal.strip()));
    }

    /** Evidence arrives oldest first; keep the last {@code max}. */
    private static List<String> newest(List<String> items, int max) {
        return items.subList(Math.max(0, items.size() - max), items.size());
    }

    /** Lower is more useful for a learning session: prerequisites first, then the parent, then next steps. */
    private static int usefulness(SkillDetails.Relation r) {
        if (r.outgoing()) {
            return switch (r.type()) {
                case REQUIRES -> 0;
                case PART_OF -> 1;
                case LEADS_TO -> 2;
                case RELATED_TO -> 3;
            };
        }
        return r.type() == RelationType.RELATED_TO ? 3 : 4; // incoming: what depends on / follows / belongs to this skill
    }
}
