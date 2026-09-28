package dev.minibrain.skill.web;

import dev.minibrain.skill.domain.Skill;
import dev.minibrain.skill.domain.SkillStatus;
import dev.minibrain.skill.persistence.SkillRepository;
import dev.minibrain.skill.query.SkillDetails;
import dev.minibrain.skill.query.SkillDetailsQuery;
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
@RequestMapping("/api/skills")
public class SkillController {

    record CreateSkillRequest(String key, String name, String description, SkillStatus status) {
    }

    private final SkillRepository skills;
    private final SkillDetailsQuery detailsQuery;

    public SkillController(SkillRepository skills, SkillDetailsQuery detailsQuery) {
        this.skills = skills;
        this.detailsQuery = detailsQuery;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Skill create(@RequestBody CreateSkillRequest request) {
        if (isBlank(request.key()) || isBlank(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key and name are required");
        }
        SkillStatus status = request.status() != null ? request.status() : SkillStatus.DISCOVERED;
        return skills.create(request.key(), request.name(), request.description(), status);
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
