CREATE TABLE skill_relation (
    from_skill_id INTEGER NOT NULL REFERENCES skill (id),
    to_skill_id   INTEGER NOT NULL REFERENCES skill (id),
    type          TEXT    NOT NULL
                  CHECK (type IN ('PART_OF', 'REQUIRES', 'RELATED_TO', 'LEADS_TO')),
    created_at    TEXT    NOT NULL,
    -- A relation is identified by (from, to, type): no surrogate id, no duplicates.
    PRIMARY KEY (from_skill_id, to_skill_id, type),
    CHECK (from_skill_id <> to_skill_id)
);

-- The primary key serves lookups by from_skill_id; this one serves incoming relations.
CREATE INDEX skill_relation_to_skill_id ON skill_relation (to_skill_id);
