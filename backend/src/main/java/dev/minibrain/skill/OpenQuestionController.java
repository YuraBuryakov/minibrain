package dev.minibrain.skill;

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
@RequestMapping("/api/skills/{key}/open-questions")
public class OpenQuestionController {

    record AddOpenQuestionRequest(String text) {
    }

    private final SkillRepository skills;
    private final OpenQuestionRepository questions;

    public OpenQuestionController(SkillRepository skills, OpenQuestionRepository questions) {
        this.skills = skills;
        this.questions = questions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OpenQuestion add(@PathVariable String key, @RequestBody AddOpenQuestionRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
        }
        return questions.add(skillId(key), request.text());
    }

    @GetMapping
    public List<OpenQuestion> list(@PathVariable String key) {
        return questions.findBySkillId(skillId(key));
    }

    @PostMapping("/{id}/resolve")
    public OpenQuestion resolve(@PathVariable String key, @PathVariable long id) {
        return questions.resolve(skillId(key), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "open question not found: " + id));
    }

    private long skillId(String key) {
        return skills.findByKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "skill not found: " + key))
                .id();
    }
}
