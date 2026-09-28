-- A skill an AI proposed (brief §14): not a Skill yet, no status, evidence or questions.
-- Unlock turns it into a DISCOVERED skill and deletes this row; dismiss hides it (dismissed_at).
CREATE TABLE suggested_skill (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    key              TEXT    NOT NULL UNIQUE,
    name             TEXT    NOT NULL,
    name_ru          TEXT,
    reason           TEXT,
    reason_ru        TEXT,
    -- A key, not a foreign key: the source may be missing (an unticked new skill); unlock links it only if it exists.
    source_skill_key TEXT,
    created_at       TEXT    NOT NULL,
    dismissed_at     TEXT
);
