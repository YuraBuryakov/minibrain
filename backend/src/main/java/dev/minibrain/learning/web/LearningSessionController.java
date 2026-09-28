package dev.minibrain.learning.web;

import dev.minibrain.learning.persistence.LearningSessionRepository;
import dev.minibrain.learning.persistence.LearningSessionRepository.SessionNotes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LearningSessionController {

    private final LearningSessionRepository sessions;

    public LearningSessionController(LearningSessionRepository sessions) {
        this.sessions = sessions;
    }

    /** Study notes of every session that touched this skill, newest first (empty list for an unknown skill). */
    @GetMapping("/api/skills/{key}/sessions")
    public List<SessionNotes> sessions(@PathVariable String key) {
        return sessions.findBySkillKey(key);
    }
}
