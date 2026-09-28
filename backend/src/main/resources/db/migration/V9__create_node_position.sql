-- Pinned map positions (brief §30 hybrid layout). Presentation state, not knowledge: no revision, not in current.json.
-- node_id: a skill key, or "area:<name>" for an area sigil. No foreign key: a pin of a vanished node is simply unused.
CREATE TABLE node_position (
    node_id    TEXT PRIMARY KEY,
    x          REAL NOT NULL,
    y          REAL NOT NULL,
    updated_at TEXT NOT NULL
);
