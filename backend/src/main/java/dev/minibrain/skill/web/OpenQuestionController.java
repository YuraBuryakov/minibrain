package dev.minibrain.skill.web;

import dev.minibrain.revision.domain.RevisionChange;
import dev.minibrain.revision.persistence.RevisionRepository;
import dev.minibrain.revision.persistence.RevisionRepository.Source;
import dev.minibrain.skill.domain.OpenQuestion;
import dev.minibrain.skill.persistence.OpenQuestionRepository;
import dev.minibrain.skill.persistence.SkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/skills/{key}/open-questions")
public class OpenQuestionController {

    record AddOpenQuestionRequest(String text) {
    }

    private final SkillRepository skills;
    private final OpenQuestionRepository questions;
    private final RevisionRepository revisions;

    public OpenQuestionController(SkillRepository skills, OpenQuestionRepository questions, RevisionRepository revisions) {
        this.skills = skills;
        this.questions = questions;
        this.revisions = revisions;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public OpenQuestion add(@PathVariable String key, @RequestBody AddOpenQuestionRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
        }
        OpenQuestion added = questions.add(skillId(key), request.text());
        revisions.record(Source.MANUAL, null, List.of(RevisionChange.questionAdded(key, added.text())));
        return added;
    }

    @GetMapping
    public List<OpenQuestion> list(@PathVariable String key) {
        return questions.findBySkillId(skillId(key));
    }

    @PostMapping("/{id}/resolve")
    @Transactional
    public OpenQuestion resolve(@PathVariable String key, @PathVariable long id) {
        long skillId = skillId(key);
        // resolve() is idempotent; history records only the first, real resolution.
        boolean wasOpen = questions.findBySkillId(skillId).stream().anyMatch(q -> q.id() == id && q.resolvedAt() == null);
        OpenQuestion question = questions.resolve(skillId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "open question not found: " + id));
        if (wasOpen) {
            revisions.record(Source.MANUAL, null, List.of(RevisionChange.questionResolved(key, question.text())));
        }
        return question;
    }

    private long skillId(String key) {
        return skills.findByKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "skill not found: " + key))
                .id();
    }
}
