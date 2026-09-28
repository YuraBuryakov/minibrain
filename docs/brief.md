# MiniBrain / Knowledge Skill Tree
## Product & Architecture Brief for Incremental Development with Codex

## 1. Product idea

MiniBrain is a local-first web application for tracking learning, knowledge growth, and skill development while working with AI.

The application is not meant to be a chat history.

Its purpose is:

> to show the history of how I become better.

The main interface is a visual Skill Map / Knowledge Graph inspired more by game skill trees (for example Path of Exile) and Obsidian-like graph views than by a traditional folder hierarchy.

The product should help answer:

- What have I already studied?
- What have I only discovered?
- What do I understand?
- What have I actually applied?
- Which skills are related?
- What are my current knowledge gaps?
- What should I logically study next?
- What evidence do I have that I understand something?
- How has my knowledge map changed over time?

---

# 2. Main development philosophy

The application must stay simple at first.

Core rule:

> If a feature is not needed for the first useful workflow, do not build it yet.

We deliberately avoid premature complexity.

Do NOT start with:

- microservices;
- authentication;
- cloud infrastructure;
- Docker as a requirement;
- Kubernetes;
- Event Sourcing;
- complex CQRS frameworks;
- command buses;
- automatic semantic duplicate detection;
- XP systems;
- achievements;
- spaced repetition;
- advanced graph physics;
- complex recommendation engines.

These can be added later only if the basic workflow proves useful and pleasant.

---

# 3. Development workflow with AI / Codex

This project is also a learning project.

AI must not do the entire project for me.

Preferred workflow:

```text
task
↓
discuss options
↓
understand trade-offs
↓
implement a small piece
↓
review code
↓
I understand and accept it
↓
small commit
↓
next task
```

Codex should behave like a technical pair-programmer, not an autonomous project generator.

## Rules for Codex

1. Do not generate the whole project at once.
2. Work in small, reviewable iterations.
3. Before introducing a new abstraction, explain why it is needed.
4. Prefer the simplest working design.
5. Do not add infrastructure “for the future” unless the current task requires it.
6. When there are meaningful trade-offs, present 2–3 options.
7. Stop after the requested slice is complete.
8. Do not silently refactor unrelated parts of the project.
9. Keep commits conceptually small.
10. Prefer code that is easy to understand over code that is maximally abstract.
11. Architectural complexity must come from real requirements, not pattern enthusiasm.
12. Major parts should be replaceable through small contracts.
13. Explain unfamiliar concepts when they appear.
14. The user remains responsible for final architectural decisions.
15. If a task can be solved with one simple class, do not create five interfaces.
16. Do not implement future phases unless explicitly requested.

---

# 4. Initial deployment model

MiniBrain is a normal web application.

First phase:

```text
Browser
↓
React frontend
↓
Spring Boot backend
↓
SQLite
```

Everything runs locally on Windows.

Example:

```text
frontend: localhost:5173
backend:  localhost:8080
database: ./data/minibrain.db
```

Later the same application may move to a server:

```text
Browser
↓
Nginx / reverse proxy
↓
React
↓
Spring Boot
↓
PostgreSQL
```

The local-first design should not leak into the domain model.

---

# 5. Proposed technology stack

## Backend

- Java 21
- Spring Boot
- Spring Web
- Spring JDBC
- Flyway
- Jackson
- Spring Modulith, used lightly
- SQLite initially
- Maven

Spring JDBC is preferred initially because the model is small and it makes persistence boundaries and SQL visible.

JPA may be reconsidered later if it becomes useful.

## Frontend

- React
- TypeScript
- Vite
- TanStack Query
- React Flow is a strong candidate for the Skill Map canvas

Avoid Redux/Zustand unless real frontend complexity appears.

---

# 6. Core product workflow

The most important MVP workflow:

```text
MiniBrain
↓
Copy AI Context for a skill/topic
↓
ChatGPT / Codex / another AI
↓
learning conversation
↓
user asks: "Export MiniBrain"
↓
AI generates MINIBRAIN_UPDATE JSON
↓
MiniBrain imports JSON
↓
validate
↓
preview changes
↓
user confirms selected changes
↓
apply
↓
create Revision
↓
Skill Map updates
```

This workflow is the core product.

If it feels pleasant to use, MVP succeeds.

---

# 7. Knowledge states

A Skill has one main learning status:

```text
DISCOVERED
LEARNING
UNDERSTOOD
APPLIED
MASTERED
```

Meaning:

## DISCOVERED

I know that the concept exists.

## LEARNING

I am currently trying to understand it.

## UNDERSTOOD

I can explain the concept in my own words and answer basic questions.

## APPLIED

I used it in a real task, project, refactoring, design, or sufficiently realistic exercise.

## MASTERED

I can confidently apply it, explain trade-offs, recognize mistakes, and adapt it to different situations.

`MASTERED` should be rare.

Status is not a percentage.

Strict state-machine transitions are NOT required.

For example:

```text
UNDERSTOOD → LEARNING
```

is allowed if I realize that my previous understanding was shallow.

AI may propose status changes.

The user confirms them.

Automatic AI downgrade should not happen silently.

---

# 8. Evidence

Evidence is a central concept.

A Skill is not considered understood simply because it was discussed.

Core rule:

> Evidence describes what the user demonstrated, not what AI explained.

Good Evidence:

- independently explained a concept;
- answered a verification question correctly;
- made and justified an architectural decision;
- compared alternatives and trade-offs;
- applied the concept in code;
- found a bug or design flaw;
- corrected a previous misunderstanding;
- connected a new concept to existing knowledge;
- independently defined an invariant, boundary, rule, or design.

Bad Evidence:

- AI explained the topic;
- the topic was mentioned;
- user said “I understand”;
- user read an example;
- AI generated code;
- user simply agreed.

Example:

Bad:

```text
"Discussed Aggregate Root."
```

Good:

```text
"Independently explained why child entities should be modified through the Aggregate Root."
```

AI should prefer no Evidence over weak Evidence.

Evidence is plain text in MVP.

Possible Evidence types may be added later, but not required initially.

---

# 9. Open Questions

Each Skill can have Open Questions.

Example:

```text
Aggregate

Open Questions:
- Where should an Aggregate boundary be drawn?
- When does an Aggregate become too large?
- How should operations involving two Aggregates be handled?
```

Open Questions describe current knowledge gaps.

They are especially useful in AI Context.

AI can propose:

- adding an Open Question;
- resolving an existing Open Question.

Resolution may produce new Evidence.

Example:

```text
Open Question:
"Where should an Aggregate boundary be drawn?"

later:

Resolved

Evidence:
"Explained that Aggregate boundaries follow consistency requirements and invariants."
```

---

# 10. Skill granularity

A new term does NOT automatically become a new Skill.

Core rule:

> A topic becomes its own Skill when it becomes an independent unit of learning.

A Skill should usually be able to have:

- its own status;
- its own Evidence;
- its own Open Questions;
- its own relationships;
- future learning sessions.

Good Skill candidates:

```text
Aggregate
Invariant
Bounded Context
Domain Event
Transactional Outbox
Idempotency
```

Things that may remain details inside another Skill:

```text
Aggregate Root
Aggregate Method
Kafka Retry Delay
Outbox Table Column
```

A topic can later be promoted into a Skill if it starts “living its own life”.

---

# 11. Main knowledge entities

Initial conceptual model:

```text
Skill
Evidence
OpenQuestion
SkillRelation
LearningSession
Revision
SuggestedSkill
```

Keep the model small.

---

# 12. Skill identity

A Skill has:

```text
id
key
name
description
status
createdAt
updatedAt
```

Example stable key:

```text
ddd.aggregate
messaging.outbox
architecture.modular-monolith
```

Important distinction:

```text
Database identity:
id

Stable external identity:
key
```

`key` is used in AI JSON and portable exports.

Normal rename changes `name`, not `key`.

Changing `key` should be a rare advanced operation.

---

# 13. Skill relations

MVP relation types:

```text
PART_OF
REQUIRES
RELATED_TO
LEADS_TO
```

## PART_OF

Structural relationship.

Example:

```text
Aggregate PART_OF DDD
```

## REQUIRES

Prerequisite knowledge.

Example:

```text
Transactional Outbox REQUIRES Database Transactions
```

## RELATED_TO

Meaningful relationship without strict dependency.

Example:

```text
Aggregate RELATED_TO Transaction Boundary
```

## LEADS_TO

Logical next learning direction.

Example:

```text
Aggregate LEADS_TO Domain Event
```

Avoid creating many redundant relations.

If `A REQUIRES B`, `A RELATED_TO B` is usually unnecessary.

---

# 14. Suggested Skills / Fog of War concept

Suggested Skills are NOT normal Skills yet.

Conceptual lifecycle:

```text
AI suggests topic
↓
SUGGESTED
↓
user decides

Unlock
→ becomes Skill(DISCOVERED)

Dismiss
→ suggestion is hidden
```

Suggested Skill may contain:

```text
key
name
reason
sourceSkill
createdAt
```

Suggested Skills should not have full Evidence/Open Questions/status before they are unlocked.

Important:

> Fog of War is a UX concept, not a domain rendering rule.

Today a suggestion may look translucent.

Tomorrow it may be:

- dotted;
- darkened;
- a `?` node;
- glow;
- hidden branch;
- another visual style.

The domain model must not depend on one visual design.

---

# 15. Active learning

`active` is separate from Skill status.

Example:

```text
status = UNDERSTOOD
active = true
```

Meaning:

The Skill is already understood, but I am actively revisiting or deepening it.

Active is a UX/current-focus state.

It does not create a Revision.

Possible UI:

- subtle pulsing;
- active branch highlighting;
- quick Current Focus navigation.

---

# 16. AI Context

MiniBrain exports a small topic-specific context to AI.

It is NOT the full database.

Concept:

```text
Focus Skill
+
Status
+
Strong Evidence
+
Open Questions
+
Directly Related Skills
+
Optional Current Goal
```

Example:

```json
{
  "type": "MINIBRAIN_CONTEXT",
  "schemaVersion": 1,
  "focus": {
    "key": "ddd.aggregate",
    "name": "Aggregate",
    "status": "LEARNING"
  },
  "evidence": [
    "Understands the role of Aggregate Root",
    "Can explain why Aggregates protect invariants"
  ],
  "openQuestions": [
    "Where should the Aggregate boundary be drawn?"
  ],
  "relatedSkills": [
    {
      "key": "ddd.invariant",
      "name": "Invariant",
      "status": "UNDERSTOOD"
    }
  ],
  "goal": "Learn how to identify Aggregate boundaries in real models"
}
```

MVP context selection:

- selected Skill;
- maximum ~3–5 strong Evidence;
- Open Questions;
- approximately 3–7 directly related Skills;
- optional current goal.

Do not include:

- complete Revision history;
- the entire graph;
- unrelated Skills;
- all previous chat transcripts.

---

# 17. AI behavior rules

A reusable MiniBrain AI prompt should tell AI:

- do not do all work for the user;
- present options where useful;
- explain trade-offs;
- ask verification questions;
- allow the user to make architectural decisions;
- do not treat a topic as mastered just because AI explained it;
- create Evidence only from demonstrated understanding;
- keep updates small;
- avoid creating Skills for every mentioned term.

---

# 18. MINIBRAIN_UPDATE

AI returns a delta, not a full snapshot.

Core rule:

> Export only meaningful changes in knowledge.

Possible changes:

- proposed status change;
- new strong Evidence;
- new Open Question;
- resolved Open Question;
- new Skill;
- new Relation;
- Suggested Next Skill.

If nothing changed for a topic, do not include it.

Example:

```json
{
  "type": "MINIBRAIN_UPDATE",
  "schemaVersion": 1,
  "session": {
    "topic": "DDD Aggregates"
  },
  "changes": [
    {
      "skill": "ddd.aggregate",
      "proposedStatus": "UNDERSTOOD",
      "evidenceAdded": [
        "Independently explained the role of Aggregate Root"
      ],
      "openQuestionsAdded": [],
      "openQuestionsResolved": [
        "Where should the Aggregate boundary be drawn?"
      ]
    }
  ],
  "newSkills": [
    {
      "key": "ddd.consistency-boundary",
      "name": "Consistency Boundary",
      "status": "DISCOVERED",
      "reason": "The topic became an independent learning concept during the session"
    }
  ],
  "suggestedSkills": [
    {
      "key": "ddd.domain-event",
      "name": "Domain Event",
      "reason": "Logical continuation after Aggregate"
    }
  ],
  "newRelations": [
    {
      "from": "ddd.aggregate",
      "to": "ddd.consistency-boundary",
      "type": "RELATED_TO"
    }
  ]
}
```

AI should be conservative.

Suggested limits per session:

- 3–5 changed Skills;
- 1–3 new Skills;
- 1–3 Suggested Skills;
- only strong Evidence.

---

# 19. Import workflow

Import is never applied automatically.

Workflow:

```text
MINIBRAIN_UPDATE JSON
↓
parse
↓
schema validation
↓
semantic validation
↓
compare with current MiniBrain state
↓
build Preview / Change Set
↓
user confirms selected changes
↓
apply transaction
↓
Revision
```

---

# 20. Import Preview

Preview must show:

> what will actually change if Apply is pressed.

Do not simply render JSON.

Suggested sections:

```text
Summary

Status Changes
Evidence
Open Questions
Relations
New Skills
Suggested Skills
Conflicts

[Apply selected changes]
[Cancel]
```

Example:

```text
Aggregate
LEARNING → UNDERSTOOD

Evidence
+ Independently explained Aggregate Root
+ Identified an invariant for Order

Open Questions
- Where should Aggregate boundary be drawn?
  Resolve

New Skill
+ Consistency Boundary

Suggested Next
[ ] Domain Event

Conflict
! AI proposes LEARNING for Entity
  Current: UNDERSTOOD
```

Normal safe changes may be selected by default.

Suggested Skills are not selected by default.

---

# 21. Conflicts

Keep conflict handling simple.

Initial useful conflict types:

```text
STATUS_DOWNGRADE
DUPLICATE_SKILL_KEY
POSSIBLE_DUPLICATE_SKILL
INVALID_RELATION
UNKNOWN_SKILL_REFERENCE
```

Exact key match should be handled deterministically.

Semantic/fuzzy duplicate detection can be added later.

---

# 22. Revision history

Do NOT implement Event Sourcing.

Use:

```text
Current state = SQLite
History = immutable Revision records
Recovery = snapshots
```

Revision describes meaningful domain changes.

Example:

```text
Revision #42

+ Skill: Consistency Boundary
Aggregate: LEARNING → UNDERSTOOD
+ Evidence x2
+ Relation: Aggregate → Invariant
```

Revision should describe domain operations, not SQL.

Good:

```text
SKILL_CREATED
SKILL_STATUS_CHANGED
EVIDENCE_ADDED
RELATION_ADDED
QUESTION_RESOLVED
```

Bad:

```text
UPDATE skill SET ...
INSERT INTO ...
```

---

# 23. Snapshots and recovery

Portable snapshots are separate from revisions.

Use:

```text
Revision history
→ understand what changed

Snapshot
→ restore state
```

Snapshot may be created:

- manually;
- before migrations;
- before major application upgrades;
- when desired.

Recovery workflow:

```text
choose snapshot
↓
preview restore
↓
confirm
↓
replace current knowledge state
↓
create RESTORE Revision
```

No complex history rewind is needed for MVP.

---

# 24. Portable current.json

`current.json` is the full portable representation of current knowledge.

Core requirement:

> If SQLite and the entire implementation disappear, MiniBrain should be reconstructable from current.json.

Example top-level structure:

```json
{
  "schemaVersion": 1,
  "exportedAt": "2026-09-28T16:30:00+02:00",
  "skills": [],
  "relations": [],
  "evidence": [],
  "openQuestions": [],
  "learningSessions": []
}
```

Prefer a flat structure.

Do not tightly nest everything under Skill.

Relations should use stable Skill keys:

```json
{
  "from": "ddd.aggregate",
  "to": "ddd.invariant",
  "type": "RELATED_TO"
}
```

Do not depend on database numeric IDs.

---

# 25. JSON formats are different contracts

There are several different JSON concepts.

Do not mix them.

## MINIBRAIN_CONTEXT

MiniBrain → AI

Question answered:

> What should AI know before this learning session?

## MINIBRAIN_UPDATE

AI → MiniBrain

Question answered:

> What changed during this session?

## current.json

Complete portable current state.

Question answered:

> What does MiniBrain currently know?

## snapshot-*.json

Saved historical copy of current state.

## Revision

Meaningful change history.

Question answered:

> What changed between states?

---

# 26. schemaVersion

Every durable JSON contract should contain:

```json
{
  "schemaVersion": 1
}
```

Formats will evolve.

Example:

```text
v1 Evidence = text
v2 Evidence gets type
v3 Skill gets aliases
```

Old files may later be migrated:

```text
v1 → v2
v2 → v3
```

---

# 27. Deterministic exports

Portable JSON should be stable for Git diffs.

Examples:

- sort Skills by `key`;
- sort Relations by `from`, `to`, `type`;
- avoid unnecessary field reordering.

Do not export calculated values such as:

```text
evidenceCount
relationCount
```

if they can be recomputed.

Do not mix UI state into the knowledge export.

---

# 28. Main Skill Map

The startup screen is the Skill Map.

No dashboard is required before it.

Primary interface:

```text
Skill Map canvas
+
small top toolbar
+
Skill details panel on selection
```

Top toolbar may contain:

```text
Import
Export
Search
Current Focus
```

The map should feel like a living skill tree, not a folder browser.

---

# 29. Graph visualization

Visual inspiration:

- Path of Exile skill tree;
- Obsidian graph;
- game progression maps.

Possible visual behavior:

- panning;
- zoom;
- interactive nodes;
- relation edges;
- active branch pulsing;
- Suggested Skills / fog concept;
- focus highlighting;
- clusters/areas;
- manual node movement.

The exact visual language can change later.

Do not encode visual design into the domain.

---

# 30. Hybrid graph layout

Use hybrid positioning.

Rules:

```text
new node
↓
auto-position near related/parent node
↓
user may drag it
↓
manual position becomes pinned
```

Conceptual layout state:

```text
AUTO
PINNED
```

Future actions may include:

```text
Auto arrange visible
Auto arrange cluster
Reset positions
```

Layout state is not knowledge history.

Moving a node does NOT create a Revision.

---

# 31. Graph model vs layout vs renderer

These must remain independent.

```text
Knowledge Graph Data
↓
Graph Layout
↓
Node positions
↓
Graph Renderer
```

Graph Layout can later be replaced.

Graph Renderer can later be replaced.

React Flow must not leak into backend/domain contracts.

Backend returns generic graph information.

Frontend adapts it to React Flow.

---

# 32. Focus mode

Focus mode is temporary UX state.

Example:

Select `Transactional Outbox`.

Keep highlighted:

- selected Skill;
- REQUIRES;
- RELATED_TO;
- LEADS_TO;
- optionally PART_OF parent.

Dim the rest of the map.

Exit with:

```text
Esc
Back to full map
```

Focus mode does not change knowledge state.

---

# 33. Search

Search should:

```text
type skill name/key
↓
show matches
↓
select result
↓
center canvas on node
↓
highlight it
```

Search does not modify state.

Useful workflow:

```text
Search
↓
Select Skill
↓
Focus
↓
Copy AI Context
```

---

# 34. Skill card / details panel

Clicking a node opens a compact details panel.

Suggested content:

```text
Skill name
Status
Active / inactive
Description

Evidence
Open Questions
Relations
Recent changes
```

Important actions:

```text
Copy AI Context
Set Active
Edit
Add Relation
```

Rare actions go into `...`:

```text
Merge
Delete
Change technical key
```

The card should help continue learning, not only display metadata.

---

# 35. Manual operations

MiniBrain must remain usable without AI.

Manual actions:

- create Skill;
- rename Skill;
- change status;
- add Evidence;
- add Open Question;
- resolve Open Question;
- create Relation;
- remove Relation;
- merge Skills;
- delete Skill;
- move/pin node;
- mark active.

Rule:

> Knowledge changes create Revision. Presentation changes do not.

Examples:

Creates Revision:

```text
Rename Skill
Change Status
Add Evidence
Add Relation
Delete Skill
Merge Skills
```

No Revision:

```text
Move Node
Zoom Canvas
Focus Mode
Pin Node
Active UX marker (initially)
```

---

# 36. Rename

Normal rename changes only:

```text
name
```

It does not change:

```text
key
```

Example:

```text
key = ddd.aggregate
name = Aggregate
```

rename to:

```text
name = DDD Aggregate
```

Key stays stable.

---

# 37. Merge

Merge is always user-confirmed.

Example:

```text
Aggregate Root
→ merge into →
Aggregate
```

Move:

- Evidence;
- Open Questions;
- Relations;
- session references.

Remove duplicate relations where exact duplicates appear.

AI may suggest possible duplicates.

AI must not automatically merge them.

---

# 38. Delete

MVP may use hard delete.

Before deleting, show impact:

```text
Delete "Aggregate"?

Evidence: 4
Open Questions: 2
Relations: 5
```

Do not cascade-delete child Skills just because a parent/category is deleted.

Children may become unassigned/root nodes.

Soft delete / Trash may be added later only if needed.

---

# 39. Architectural independence

This is a major project principle.

Each major concern should be replaceable with minimal effect on others.

Conceptual independent blocks:

```text
Knowledge
Import
AI Context / Export
Revision
Persistence
Knowledge Query
Graph Visualization
Graph Layout
UI
```

Do NOT create abstraction merely for abstraction.

Create a boundary where replacement or responsibility separation is genuinely useful.

---

# 40. Main contracts

Conceptual write pipeline:

```text
AI JSON
↓
Importer
↓
Validator
↓
Preview Builder
↓
Change Set
↓
Preview UI
↓
Approved Change Set
↓
Apply Engine
↓
Knowledge Model
↓
Revision
```

Conceptual read pipeline:

```text
Knowledge Model
↓
Knowledge Query
↓
Graph Data
↓
Layout
↓
Renderer
```

AI context pipeline:

```text
Knowledge Model
↓
AI Context Builder
↓
MINIBRAIN_CONTEXT
↓
AI
```

---

# 41. Importer responsibility

Input:

```text
raw JSON / uploaded file
```

Output:

```text
MiniBrainUpdate
```

Importer handles:

- JSON syntax;
- `type`;
- `schemaVersion`;
- required fields.

Importer should not contain deep domain decisions.

---

# 42. Validator responsibility

Input:

```text
MiniBrainUpdate
```

Output:

```text
ValidatedUpdate
```

Validator handles semantic consistency such as:

- unknown references;
- duplicate exact keys;
- invalid relation structure;
- unsupported status values.

Validator does not modify the database.

---

# 43. Preview Builder responsibility

Input:

```text
ValidatedUpdate
+
Current Knowledge State
```

Output:

```text
ChangeSet
```

Example ChangeSet items:

```text
AddSkill
ChangeStatus
AddEvidence
AddOpenQuestion
ResolveQuestion
AddRelation
Conflict
```

If proposed state already matches current state:

```text
NO_CHANGE
```

Do not show meaningless changes.

---

# 44. Preview UI responsibility

Input:

```text
ChangeSet
```

Output:

```text
ApprovedChangeSet
```

UI decides what the user selected.

UI does not directly mutate the domain.

---

# 45. Apply Engine responsibility

Input:

```text
ApprovedChangeSet
```

Apply all knowledge changes inside one transaction.

Either everything succeeds or nothing changes.

The Apply Engine should not know:

- original raw JSON;
- how Preview was rendered;
- React Flow details.

---

# 46. Revision Recorder responsibility

Conceptually:

```text
AppliedChanges
↓
Revision Recorder
↓
Revision
```

Technical implementation may still participate in the same transaction.

Responsibility should remain separate.

---

# 47. Knowledge Query responsibility

Read-side operations may include:

```text
GetSkillDetails
GetKnowledgeGraph
SearchSkills
GetActiveSkills
GetRevisionHistory
```

Graph UI reads query models, not repositories directly.

---

# 48. CQRS approach

Use CQRS only where it naturally helps.

No framework-heavy CQRS.

Natural split:

Write side:

```text
CreateSkill
ChangeStatus
AddEvidence
ApplyImport
MergeSkills
```

Read side:

```text
GetSkillDetails
GetKnowledgeGraph
SearchSkills
```

Read models may be different from domain write models.

---

# 49. DDD approach

Use DDD where it adds meaning.

Good candidates:

```text
Skill
SkillKey
SkillStatus
Relations
domain rules
module boundaries
```

Do not force every DTO, JSON payload, Preview object, or parser into a Domain Entity.

---

# 50. Possible module boundaries

Initial conceptual modules:

```text
skill
learning
importing
revision
```

Do not create more modules until needed.

Possible responsibility:

## skill

- Skill
- Evidence
- Open Questions
- Relations
- graph/read queries

## learning

- LearningSession
- learning session metadata

## importing

- MINIBRAIN_UPDATE
- parse
- validate
- preview
- apply orchestration

## revision

- Revision history

Review/spaced repetition is future work and should not exist initially.

---

# 51. Persistence independence

Application/domain should not depend directly on SQLite-specific implementation.

Conceptually:

```text
Application
↓
SkillRepository contract
↓
SQLite implementation
```

Later:

```text
Application
↓
SkillRepository contract
↓
PostgreSQL implementation
```

However:

> do not create interfaces everywhere just because this document mentions contracts.

Use them where replacement boundaries are meaningful.

---

# 52. JSON boundary rule

Raw JSON must not leak into the domain.

Bad:

```text
Domain service works with JsonNode / ObjectMapper.
```

Good:

```text
JSON
↓
Importer boundary
↓
MiniBrainUpdate / domain-friendly structures
```

Similarly React Flow data types must not leak into backend/domain.

---

# 53. Initial SQLite storage

Runtime source of truth:

```text
data/minibrain.db
```

Portable representation:

```text
exports/current.json
```

Possible folders:

```text
MiniBrain/
├── data/
│   └── minibrain.db
├── exports/
│   ├── current.json
│   └── snapshots/
├── imports/
└── backups/
```

`.db` does not need to be in Git.

JSON snapshots may be stored in Git.

---

# 54. Future migration path

Possible evolution:

## Phase 1

```text
Local web app
SQLite
No auth
```

## Phase 2

```text
Same application on server
PostgreSQL
Reverse proxy
HTTPS
```

## Phase 3

Possible later additions:

```text
Authentication
Cloud sync
Mobile
Browser extension
IDE integration
Automatic chat import
Obsidian integration
Review questions
Spaced repetition
Achievements
Timeline visualization
```

Do not implement these now.

---

# 55. MVP definition

MVP is successful if this cycle is pleasant:

```text
1. Open Skill Map.
2. Select Skill.
3. Copy AI Context.
4. Learn with AI.
5. Ask AI for MiniBrain export.
6. Save/copy MINIBRAIN_UPDATE JSON.
7. Import into MiniBrain.
8. MiniBrain validates.
9. Preview shows real changes.
10. User selects/confirms changes.
11. Changes apply.
12. Revision is created.
13. Skill Map visibly changes.
14. Portable current.json can be exported.
```

---

# 56. Suggested incremental implementation strategy

Do not implement all of this at once.

A possible order:

```text
Step 1
Minimal Spring Boot + SQLite + one Skill

Step 2
Read Skill data back

Step 3
Evidence

Step 4
Open Questions

Step 5
Relations

Step 6
Basic graph query

Step 7
Minimal React Skill Map

Step 8
Select node + Skill card

Step 9
AI Context export

Step 10
MINIBRAIN_UPDATE parser

Step 11
Validation

Step 12
Preview / Change Set

Step 13
Apply

Step 14
Revision

Step 15
current.json export

Step 16
Suggested Skills / Fog concept

Step 17
Focus mode / search

Step 18
Layout improvements
```

This is a direction, not a fixed plan.

Re-evaluate after every few steps.

---

# 57. Important working principle for every implementation task

Before coding a feature, answer:

```text
What is the smallest useful version?
```

After coding it, ask:

```text
Did we solve a real current problem,
or did we build infrastructure for an imagined future?
```

If the latter, simplify.

---

# 58. Codex collaboration prompt

Use this at the beginning of implementation chats if useful:

```text
We are building MiniBrain incrementally.

Do not generate the whole application or large unrelated sections.

For each task:

1. Restate the small goal.
2. Identify any important trade-off.
3. Propose the simplest implementation.
4. Generate only the code required for this slice.
5. Explain unfamiliar concepts briefly.
6. Do not add future infrastructure unless required.
7. Keep boundaries replaceable where it genuinely matters.
8. Do not refactor unrelated code.
9. Stop after the requested slice is complete.
10. I will review, understand, run and commit the code before we continue.

The goal is not just to finish MiniBrain.
The development process itself is part of my Java architecture learning.
```

---

# 59. Final architectural principle

MiniBrain should evolve like the learning process it represents:

```text
small problem
↓
understand it
↓
choose a solution
↓
implement
↓
verify
↓
small commit
↓
next improvement
```

The product must remain easy to change.

The architecture should protect important boundaries without turning the project into an architecture exercise for its own sake.
