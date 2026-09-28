package dev.minibrain.importing.format;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * A knowledge text in two languages. In JSON it is either an object {@code {"en": "...", "ru": "..."}}
 * (schemaVersion 2) or a plain string (schemaVersion 1, read as English), so old files keep importing.
 */
public record LocalizedText(String en, String ru) {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public LocalizedText {
        en = blankToNull(en);
        ru = blankToNull(ru);
    }

    /** A plain JSON string: an English-only text. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static LocalizedText of(String en) {
        return new LocalizedText(en, null);
    }

    /** The text used for storing and matching: English, or Russian when only Russian is given. Null if empty. */
    public String primary() {
        return en != null ? en : ru;
    }

    /** The Russian version worth storing next to {@link #primary()} (null when it would only repeat it). */
    public String secondaryRu() {
        return en != null ? ru : null;
    }

    public boolean isEmpty() {
        return primary() == null;
    }

    /** Display form, e.g. "Aggregate / Агрегат". */
    public String display() {
        return en != null && ru != null ? en + " / " + ru : String.valueOf(primary());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
