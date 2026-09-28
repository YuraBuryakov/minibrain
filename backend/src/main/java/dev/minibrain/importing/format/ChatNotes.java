package dev.minibrain.importing.format;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Bilingual study notes (markdown). Preferred source: the {@code notes} field of the JSON.
 * Fallback: "## English" / "## Русский" sections written in the chat above the JSON block.
 * Either language may be missing.
 */
public record ChatNotes(String en, String ru) {

    private static final Pattern HEADING = Pattern.compile("^#{1,3}[ \\t]*(English|Русский)[ \\t]*$", Pattern.MULTILINE);

    /**
     * Notes from the JSON itself ({@code notes} or {@code session.notes}). A leading "## English" / "## Русский"
     * heading inside the text is dropped: the reading window already has a language switch.
     */
    public static Optional<ChatNotes> fromDocument(UpdateDocument document) {
        UpdateDocument.Notes notes = document.notes() != null ? document.notes()
                : document.session() != null ? document.session().notes() : null;
        if (notes == null) return Optional.empty();
        String en = withoutHeading(notes.en());
        String ru = withoutHeading(notes.ru());
        return en == null && ru == null ? Optional.empty() : Optional.of(new ChatNotes(en, ru));
    }

    private static String withoutHeading(String text) {
        if (text == null) return null;
        String body = text.strip();
        var heading = HEADING.matcher(body);
        if (heading.lookingAt()) body = body.substring(heading.end()).strip(); // only a heading at the very start
        return body.isEmpty() ? null : body;
    }

    /** {@code textBeforeJson}: the pasted chat text up to the fenced MINIBRAIN_UPDATE block. */
    public static Optional<ChatNotes> extract(String textBeforeJson) {
        var headings = HEADING.matcher(textBeforeJson).results().toList();
        String en = null;
        String ru = null;
        for (int i = 0; i < headings.size(); i++) {
            int end = i + 1 < headings.size() ? headings.get(i + 1).start() : textBeforeJson.length();
            String body = textBeforeJson.substring(headings.get(i).end(), end).strip();
            if (body.isEmpty()) continue;
            if (headings.get(i).group(1).equals("English") && en == null) en = body;
            if (headings.get(i).group(1).equals("Русский") && ru == null) ru = body;
        }
        return en == null && ru == null ? Optional.empty() : Optional.of(new ChatNotes(en, ru));
    }
}
