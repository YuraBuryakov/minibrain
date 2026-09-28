package dev.minibrain.skill.web;

import dev.minibrain.revision.domain.RevisionChange;
import dev.minibrain.revision.persistence.RevisionRepository;
import dev.minibrain.revision.persistence.RevisionRepository.Source;
import dev.minibrain.skill.domain.Evidence;
import dev.minibrain.skill.persistence.EvidenceRepository;
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
@RequestMapping("/api/skills/{key}/evidence")
public class EvidenceController {

    record AddEvidenceRequest(String text) {
    }

    private final SkillRepository skills;
    private final EvidenceRepository evidence;
    private final RevisionRepository revisions;

    public EvidenceController(SkillRepository skills, EvidenceRepository evidence, RevisionRepository revisions) {
        this.skills = skills;
        this.evidence = evidence;
        this.revisions = revisions;
    }

    @PostMapping
    @Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public Evidence add(@PathVariable String key, @RequestBody AddEvidenceRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is required");
        }
        Evidence added = evidence.add(skillId(key), request.text());
        revisions.record(Source.MANUAL, null, List.of(RevisionChange.evidenceAdded(key, added.text())));
        return added;
    }

    @GetMapping
    public List<Evidence> list(@PathVariable String key) {
        return evidence.findBySkillId(skillId(key));
    }

    private long skillId(String key) {
        return skills.findByKey(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "skill not found: " + key))
                .id();
    }
}
