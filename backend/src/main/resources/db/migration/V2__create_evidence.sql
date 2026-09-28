CREATE TABLE evidence (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    skill_id    INTEGER NOT NULL REFERENCES skill (id),
    text        TEXT    NOT NULL,
    created_at  TEXT    NOT NULL
);

CREATE INDEX evidence_skill_id ON evidence (skill_id);
