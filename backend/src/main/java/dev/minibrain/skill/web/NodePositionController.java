package dev.minibrain.skill.web;

import dev.minibrain.skill.persistence.NodePositionRepository;
import dev.minibrain.skill.persistence.NodePositionRepository.NodePosition;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Map layout (brief §30): pinned positions only. Separate from the knowledge graph on purpose (brief §31). */
@RestController
@RequestMapping("/api/layout/positions")
public class NodePositionController {

    record PinRequest(double x, double y) {
    }

    private final NodePositionRepository positions;

    public NodePositionController(NodePositionRepository positions) {
        this.positions = positions;
    }

    @GetMapping
    public List<NodePosition> all() {
        return positions.findAll();
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pin(@PathVariable String id, @RequestBody PinRequest request) {
        if (!Double.isFinite(request.x()) || !Double.isFinite(request.y())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "x and y must be numbers");
        }
        positions.pin(new NodePosition(id, request.x(), request.y()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        positions.unpinAll();
    }
}
