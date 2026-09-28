package dev.minibrain.importing.web;

import dev.minibrain.importing.format.CurrentState;
import dev.minibrain.importing.query.CurrentStateQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final CurrentStateQuery current;

    public ExportController(CurrentStateQuery current) {
        this.current = current;
    }

    /** current.json: the browser saves it as a file. */
    @GetMapping("/api/exports/current")
    public CurrentState current() {
        return current.get();
    }
}
