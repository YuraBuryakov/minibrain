CREATE TABLE open_question (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    skill_id    INTEGER NOT NULL REFERENCES skill (id),
    text        TEXT    NOT NULL,
    created_at  TEXT    NOT NULL,
    resolved_at TEXT,   -- NULL = still open
    -- MINIBRAIN_UPDATE resolves questions by text, so text must be unique within a Skill.
    -- The (skill_id, ...) prefix also serves lookups by skill_id, no separate index needed.
    UNIQUE (skill_id, text)
);
