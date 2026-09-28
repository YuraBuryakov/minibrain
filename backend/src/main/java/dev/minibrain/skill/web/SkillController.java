package dev.minibrain.skill.web;

import dev.minibrain.revision.domain.RevisionChange;
import dev.minibrain.revision.persistence.RevisionRepository;
import dev.minibrain.revision.persistence.RevisionRepository.Source;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.persistence.SkillRepository;
import dev.minibrain.skill.query.AiContext;
import dev.minibrain.skill.query.AiContextQuery;
import dev.minibrain.skill.query.SkillDetails;
import dev.minibrain.skill.query.SkillDetailsQuery;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    record CreateSkillRequest(String key, String name, String description, SkillStatus status) {
    }

    private final SkillRepository skills;
    private final SkillDetailsQuery detailsQuery;
    private final AiContextQuery contextQuery;
    private final RevisionRepository revisions;

    public SkillController(SkillRepository skills, SkillDetailsQuery detailsQuery, AiContextQuery contextQuery,
                           RevisionRepository revisions) {
        this.skills = skills;
        this.detailsQuery = detailsQuery;
        this.contextQuery = contextQuery;
        this.revisions = revisions;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Skill create(@RequestBody CreateSkillRequest request) {
        if (isBlank(request.key()) || isBlank(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key and name are required");
        }
        SkillStatus status = request.status() != null ? request.status() : SkillStatus.DISCOVERED;
        Skill skill = skills.create(request.key(), request.name(), request.description(), status);
        revisions.record(Source.MANUAL, null, List.of(RevisionChange.skillCreated(skill.key(), status.name(), skill.name())));
        return skill;
    }

    @GetMapping
    public List<Skill> list() {
        return skills.findAll();
    }

    @GetMapping("/{key}")
    public Skill get(@PathVariable String key) {
        return skills.findByKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "skill not found: " + key));
    }

    @GetMapping("/{key}/details")
    public SkillDetails details(@PathVariable String key) {
        return detailsQuery.find(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "skill not found: " + key));
    }

    /** MINIBRAIN_CONTEXT for a learning session with an AI; {@code goal} is optional. */
    @GetMapping("/{key}/context")
    public AiContext context(@PathVariable String key, @RequestParam(required = false) String goal) {
        return contextQuery.find(key, goal)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "skill not found: " + key));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
