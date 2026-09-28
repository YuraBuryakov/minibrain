package dev.minibrain.skill;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KnowledgeGraphController {

    private final KnowledgeGraphQuery query;

    public KnowledgeGraphController(KnowledgeGraphQuery query) {
        this.query = query;
    }

    @GetMapping("/api/graph")
    public KnowledgeGraph graph() {
        return query.get();
    }
}
