package dev.minibrain.skill;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

    record CreateSkillRequest(String key, String name, String description, SkillStatus status) {
    }

    private final SkillRepository skills;

    public SkillController(SkillRepository skills) {
        this.skills = skills;
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
