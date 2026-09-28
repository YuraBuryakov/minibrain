package dev.minibrain.skill.application;

import dev.minibrain.revision.domain.RevisionChange;
import dev.minibrain.revision.persistence.RevisionRepository;
import dev.minibrain.revision.persistence.RevisionRepository.Source;
import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.persistence.SkillRelationRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import dev.minibrain.skill.persistence.SuggestedSkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Unlock (brief §14): an open suggestion becomes a DISCOVERED skill (its reason becomes the description) and, when
 * its source exists, "source LEADS_TO new skill". History: SKILL_UNLOCKED (+ RELATION_ADDED). Who may unlock (talent
 * points) is the game's decision, not this service's. A skill with the same key -> DuplicateKeyException (409).
 */
@Service
public class SuggestionUnlocker {

    private final SuggestedSkillRepository suggestions;
    private final SkillRepository skills;
    private final SkillRelationRepository relations;
    private final RevisionRepository revisions;

    public SuggestionUnlocker(SuggestedSkillRepository suggestions, SkillRepository skills,
                              SkillRelationRepository relations, RevisionRepository revisions) {
        this.suggestions = suggestions;
        this.skills = skills;
        this.relations = relations;
        this.revisions = revisions;
    }

    /** Empty when there is no open suggestion with this key. */
    @Transactional
    public Optional<Skill> unlock(String key) {
        return suggestions.findOpen(key).map(s -> {
            Skill skill = skills.create(s.key(), s.name(), s.nameRu(), s.reason(), s.reasonRu(), SkillStatus.DISCOVERED);
            List<RevisionChange> history = new ArrayList<>(List.of(RevisionChange.skillUnlocked(key, SkillStatus.DISCOVERED.name(), s.name())));
            if (s.sourceSkill() != null) {
                skills.findByKey(s.sourceSkill()).ifPresent(source -> {
                    relations.add(source.id(), skill.id(), RelationType.LEADS_TO);
                    history.add(RevisionChange.relationAdded(source.key(), RelationType.LEADS_TO.name(), key));
                });
            }
            suggestions.delete(key);
            revisions.record(Source.MANUAL, null, history);
            return skill;
        });
    }
}
