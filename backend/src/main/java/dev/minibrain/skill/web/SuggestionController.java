package dev.minibrain.skill.web;

import dev.minibrain.revision.domain.RevisionChange;
import dev.minibrain.revision.persistence.RevisionRepository;
import dev.minibrain.revision.persistence.RevisionRepository.Source;
import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.domain.SuggestedSkill;
import dev.minibrain.skill.persistence.SkillRelationRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import dev.minibrain.skill.persistence.SuggestedSkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/** Decisions on suggested skills (brief §14): unlock or dismiss. */
@RestController
@RequestMapping("/api/suggestions/{key}")
public class SuggestionController {

    private final SuggestedSkillRepository suggestions;
    private final SkillRepository skills;
    private final SkillRelationRepository relations;
    private final RevisionRepository revisions;

    public SuggestionController(SuggestedSkillRepository suggestions, SkillRepository skills,
                                SkillRelationRepository relations, RevisionRepository revisions) {
        this.suggestions = suggestions;
        this.skills = skills;
        this.relations = relations;
        this.revisions = revisions;
    }

    /**
     * The suggestion becomes a DISCOVERED skill (its reason becomes the description) and, when its source skill
     * exists, "source LEADS_TO new skill", so the new skill is not an orphan. A skill with the same key → 409.
     */
    @PostMapping("/unlock")
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Skill unlock(@PathVariable String key) {
        SuggestedSkill s = suggestions.findOpen(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no open suggestion: " + key));
        Skill skill = skills.create(s.key(), s.name(), s.nameRu(), s.reason(), s.reasonRu(), SkillStatus.DISCOVERED);
        var history = new ArrayList<>(List.of(RevisionChange.skillCreated(key, SkillStatus.DISCOVERED.name(), s.name())));
        if (s.sourceSkill() != null) {
            skills.findByKey(s.sourceSkill()).ifPresent(source -> {
                relations.add(source.id(), skill.id(), RelationType.LEADS_TO);
                history.add(RevisionChange.relationAdded(source.key(), RelationType.LEADS_TO.name(), key));
            });
        }
        suggestions.delete(key);
        revisions.record(Source.MANUAL, null, history);
        return skill;
    }

    /** Hides the suggestion. Not a knowledge change, so no revision. */
    @PostMapping("/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable String key) {
        suggestions.findOpen(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no open suggestion: " + key));
        suggestions.dismiss(key);
    }
}
