package dev.minibrain.revision.web;

import dev.minibrain.revision.persistence.RevisionRepository;
import dev.minibrain.revision.persistence.RevisionRepository.Revision;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class RevisionController {

    private final RevisionRepository revisions;

    public RevisionController(RevisionRepository revisions) {
        this.revisions = revisions;
    }

    /** History, newest first. */
    @GetMapping("/api/revisions")
    public List<Revision> recent(@RequestParam(defaultValue = "30") int limit) {
        return revisions.findRecent(Math.clamp(limit, 1, 200));
    }
}
