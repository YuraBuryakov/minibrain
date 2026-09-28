-- A learning session with an AI (brief §50 module "learning"): study notes in two languages.
CREATE TABLE learning_session (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    topic       TEXT,
    notes_en    TEXT,
    notes_ru    TEXT,
    created_at  TEXT    NOT NULL
);

-- Skills the session touched (skills changed by the import that saved it).
CREATE TABLE learning_session_skill (
    session_id  INTEGER NOT NULL REFERENCES learning_session (id),
    skill_id    INTEGER NOT NULL REFERENCES skill (id),
    PRIMARY KEY (session_id, skill_id)
);

CREATE INDEX learning_session_skill_skill_id ON learning_session_skill (skill_id);
