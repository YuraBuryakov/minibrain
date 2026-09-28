package dev.minibrain.importing.web;

import dev.minibrain.importing.application.ImportPreview;
import dev.minibrain.importing.application.ImportPreviewer;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportPreviewer previewer;

    public ImportController(ImportPreviewer previewer) {
        this.previewer = previewer;
    }

    /**
     * Body is the raw text (a file's content or pasted chat text), read as a String on purpose:
     * broken JSON must come back as a readable preview problem, not as Spring's generic 400.
     */
    @PostMapping("/preview")
    public ImportPreview preview(@RequestBody String text) {
        return previewer.preview(text);
    }
}
