CREATE TABLE skill (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    key         TEXT    NOT NULL UNIQUE,
    name        TEXT    NOT NULL,
    description TEXT,
    status      TEXT    NOT NULL
                CHECK (status IN ('DISCOVERED', 'LEARNING', 'UNDERSTOOD', 'APPLIED', 'MASTERED')),
    -- ISO-8601 UTC, fixed width (yyyy-MM-ddTHH:mm:ss.SSSZ) so text order = time order
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL
);
