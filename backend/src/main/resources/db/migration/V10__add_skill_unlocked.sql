-- Game G2: SKILL_UNLOCKED = a skill opened from the fog with a talent point. SQLite cannot change a CHECK
-- constraint, so the table is rebuilt: new table, copy every row (ids kept), drop, rename, indexes again.
CREATE TABLE revision_change_new (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    revision_id   INTEGER NOT NULL REFERENCES revision (id),
    type          TEXT    NOT NULL CHECK (type IN ('SKILL_CREATED', 'SKILL_STATUS_CHANGED', 'EVIDENCE_ADDED',
                          'QUESTION_ADDED', 'QUESTION_RESOLVED', 'RELATION_ADDED', 'TRANSLATION_ADDED', 'NOTES_SAVED',
                          'SKILL_UNLOCKED')),
    skill_key     TEXT,
    from_status   TEXT,
    to_status     TEXT,
    text          TEXT,
    related_key   TEXT,
    relation_type TEXT,
    occurred_at   TEXT    NOT NULL
);

INSERT INTO revision_change_new (id, revision_id, type, skill_key, from_status, to_status, text, related_key,
                                 relation_type, occurred_at)
SELECT id, revision_id, type, skill_key, from_status, to_status, text, related_key, relation_type, occurred_at
FROM revision_change;

DROP TABLE revision_change;
ALTER TABLE revision_change_new RENAME TO revision_change;

CREATE INDEX revision_change_revision_id ON revision_change (revision_id);
CREATE INDEX revision_change_skill_key ON revision_change (skill_key);
