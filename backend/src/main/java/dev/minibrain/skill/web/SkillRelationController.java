package dev.minibrain.skill.web;

import dev.minibrain.skill.domain.RelationType;
import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillRelation;
import dev.minibrain.skill.persistence.SkillRelationRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/skills/{key}/relations")
public class SkillRelationController {

    record AddRelationRequest(RelationType type, String to) {
    }

    private final SkillRepository skills;
    private final SkillRelationRepository relations;

    public SkillRelationController(SkillRepository skills, SkillRelationRepository relations) {
        this.skills = skills;
        this.relations = relations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SkillRelation add(@PathVariable String key, @RequestBody AddRelationRequest request) {
        if (request.type() == null || request.to() == null || request.to().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type and to are required");
        }
        if (key.equals(request.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a skill cannot relate to itself");
        }
        relations.add(skill(key).id(), skill(request.to()).id(), request.type());
        return new SkillRelation(key, request.type(), request.to());
    }

    @GetMapping
    public List<SkillRelation> list(@PathVariable String key) {
        return relations.findBySkillId(skill(key).id());
    }

    private Skill skill(String key) {
        return skills.findByKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "skill not found: " + key));
    }
}
