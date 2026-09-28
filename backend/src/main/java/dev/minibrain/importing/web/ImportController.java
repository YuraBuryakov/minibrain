package dev.minibrain.importing.web;

import dev.minibrain.importing.application.ImportApplier;
import dev.minibrain.importing.application.ImportPreview;
import dev.minibrain.importing.application.ImportPreviewer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    /** The same text that was previewed, plus the ids of the items ticked in the preview. */
    record ApplyRequest(String text, List<Integer> selectedIds) {
    }

    private final ImportPreviewer previewer;
    private final ImportApplier applier;

    public ImportController(ImportPreviewer previewer, ImportApplier applier) {
        this.previewer = previewer;
        this.applier = applier;
    }

    /**
     * Body is the raw text (a file's content or pasted chat text), read as a String on purpose:
     * broken JSON must come back as a readable preview problem, not as Spring's generic 400.
     */
    @PostMapping("/preview")
    public ImportPreview preview(@RequestBody String text) {
        return previewer.preview(text);
    }

    @PostMapping("/apply")
    public ImportApplier.Result apply(@RequestBody ApplyRequest request) {
        return applier.apply(request.text(), new HashSet<>(request.selectedIds() == null ? List.of() : request.selectedIds()));
    }

    @ExceptionHandler(ImportApplier.NotApplicableException.class)
    ProblemDetail notApplicable(ImportApplier.NotApplicableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
