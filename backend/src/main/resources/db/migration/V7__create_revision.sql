-- Revision history (brief §22): immutable records of meaningful knowledge changes, in domain terms.
-- Not Event Sourcing: the current state lives in the other tables; history only tells what happened.
CREATE TABLE revision (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    source      TEXT    NOT NULL CHECK (source IN ('INITIAL', 'IMPORT', 'MANUAL')),
    topic       TEXT,
    created_at  TEXT    NOT NULL
);

-- One meaningful change. Skills are referenced by key (not id), so history stays readable on its own.
-- Plain columns instead of a JSON blob: growth over time must be answerable with simple SQL.
CREATE TABLE revision_change (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    revision_id   INTEGER NOT NULL REFERENCES revision (id),
    type          TEXT    NOT NULL CHECK (type IN ('SKILL_CREATED', 'SKILL_STATUS_CHANGED', 'EVIDENCE_ADDED',
                          'QUESTION_ADDED', 'QUESTION_RESOLVED', 'RELATION_ADDED', 'TRANSLATION_ADDED', 'NOTES_SAVED')),
    skill_key     TEXT,
    from_status   TEXT,
    to_status     TEXT,
    text          TEXT,
    related_key   TEXT,
    relation_type TEXT,
    occurred_at   TEXT    NOT NULL
);

CREATE INDEX revision_change_revision_id ON revision_change (revision_id);
CREATE INDEX revision_change_skill_key ON revision_change (skill_key);

-- Initial state: one revision rebuilt from the data that existed before history, with the real dates of each row.
-- Skipped on an empty database. Status history before this point is unknown: SKILL_CREATED carries today's status.
INSERT INTO revision (source, topic, created_at)
SELECT 'INITIAL', 'Initial state (rebuilt from existing data)', MIN(created_at) FROM skill HAVING COUNT(*) > 0;

INSERT INTO revision_change (revision_id, type, skill_key, to_status, text, occurred_at)
SELECT r.id, 'SKILL_CREATED', s.key, s.status, s.name, s.created_at
FROM skill s JOIN revision r ON r.source = 'INITIAL';

INSERT INTO revision_change (revision_id, type, skill_key, text, occurred_at)
SELECT r.id, 'EVIDENCE_ADDED', s.key, e.text, e.created_at
FROM evidence e JOIN skill s ON s.id = e.skill_id JOIN revision r ON r.source = 'INITIAL';

INSERT INTO revision_change (revision_id, type, skill_key, text, occurred_at)
SELECT r.id, 'QUESTION_ADDED', s.key, q.text, q.created_at
FROM open_question q JOIN skill s ON s.id = q.skill_id JOIN revision r ON r.source = 'INITIAL';

INSERT INTO revision_change (revision_id, type, skill_key, text, occurred_at)
SELECT r.id, 'QUESTION_RESOLVED', s.key, q.text, q.resolved_at
FROM open_question q JOIN skill s ON s.id = q.skill_id JOIN revision r ON r.source = 'INITIAL'
WHERE q.resolved_at IS NOT NULL;

INSERT INTO revision_change (revision_id, type, skill_key, related_key, relation_type, occurred_at)
SELECT r.id, 'RELATION_ADDED', f.key, t.key, sr.type, sr.created_at
FROM skill_relation sr JOIN skill f ON f.id = sr.from_skill_id JOIN skill t ON t.id = sr.to_skill_id
JOIN revision r ON r.source = 'INITIAL';

INSERT INTO revision_change (revision_id, type, text, occurred_at)
SELECT r.id, 'NOTES_SAVED', ls.topic, ls.created_at
FROM learning_session ls JOIN revision r ON r.source = 'INITIAL';
