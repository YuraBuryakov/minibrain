// Mirrors the backend read model dev.minibrain.skill.query.KnowledgeGraph (renderer-neutral).

export type SkillStatus = 'DISCOVERED' | 'LEARNING' | 'UNDERSTOOD' | 'APPLIED' | 'MASTERED'
export type RelationType = 'PART_OF' | 'REQUIRES' | 'RELATED_TO' | 'LEADS_TO'

export type GraphNode = { key: string; name: string; nameRu: string | null; status: SkillStatus }
export type GraphEdge = { from: string; type: RelationType; to: string }
// A suggested skill in the fog: not a skill yet. from = its source skill key (may be null or unknown).
export type GraphSuggestion = { key: string; name: string; nameRu: string | null; reason: string | null; reasonRu: string | null; from: string | null }
export type KnowledgeGraph = { nodes: GraphNode[]; edges: GraphEdge[]; suggestions: GraphSuggestion[] }

export async function fetchGraph(): Promise<KnowledgeGraph> {
  const response = await fetch('/api/graph')
  if (!response.ok) throw new Error(`GET /api/graph failed: ${response.status}`)
  return response.json()
}

// Pinned map positions (backend NodePositionController). id: a skill key or "area:<name>".
export type NodePosition = { id: string; x: number; y: number }

export async function fetchPositions(): Promise<NodePosition[]> {
  const response = await fetch('/api/layout/positions')
  if (!response.ok) throw new Error(`GET positions failed: ${response.status}`)
  return response.json()
}

export async function pinPosition({ id, x, y }: NodePosition): Promise<void> {
  const response = await fetch(`/api/layout/positions/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ x, y }),
  })
  if (!response.ok) throw new Error(`Saving the position of ${id} failed: ${response.status}`)
}

export async function resetPositions(): Promise<void> {
  const response = await fetch('/api/layout/positions', { method: 'DELETE' })
  if (!response.ok) throw new Error(`Reset layout failed: ${response.status}`)
}

// Unlocking costs a talent point (game module); 409 = no point left (or the key is taken).
export async function unlockSuggestion(key: string): Promise<void> {
  const response = await fetch(`/api/game/unlock/${encodeURIComponent(key)}`, { method: 'POST' })
  if (response.status === 409) throw new Error('409')
  if (!response.ok) throw new Error(`Unlock ${key} failed: ${response.status}`)
}

export async function dismissSuggestion(key: string): Promise<void> {
  const response = await fetch(`/api/suggestions/${encodeURIComponent(key)}/dismiss`, { method: 'POST' })
  if (!response.ok) throw new Error(`Dismiss ${key} failed: ${response.status}`)
}

// Mirrors dev.minibrain.game.query.GameState: the game, computed from the revision history on every request.
export type Title = 'STUDENT' | 'JOURNEYMAN' | 'SCHOLAR' | 'ARCHITECT' | 'MAGISTER'
export type GameState = {
  // vision: clear fog margin in px (grows with the level)
  player: { xp: number; level: number; levelStartXp: number; nextLevelXp: number; title: Title; talentPoints: number; vision: number }
  areas: { key: string; xp: number; rank: number; complete: boolean }[]
  // mastery gate: a fog topic opens only when its source skill has reached this status
  unlockStatus: SkillStatus
}

export async function fetchGame(): Promise<GameState> {
  const response = await fetch('/api/game')
  if (!response.ok) throw new Error(`GET /api/game failed: ${response.status}`)
  return response.json()
}

// Mirrors dev.minibrain.game.domain.Achievement (docs/game-design.md §9).
export const ACHIEVEMENTS = ['FIRST_UNDERSTANDING', 'HANDS_ON', 'MASTERY', 'QUEST_HUNTER', 'CARTOGRAPHER', 'PATHFINDER', 'CONSTELLATION', 'CHRONICLER', 'PROVEN'] as const
export type AchievementId = (typeof ACHIEVEMENTS)[number]

// Mirrors dev.minibrain.game.query.HeroView: the hero window. day = "YYYY-MM-DD", ascending.
// achievements: all of them in enum order, earnedAt (ISO instant) null while locked.
export type HeroView = { xpByDay: { day: string; xp: number }[]; achievements: { id: AchievementId; earnedAt: string | null }[] }

export async function fetchHero(): Promise<HeroView> {
  const response = await fetch('/api/game/hero')
  if (!response.ok) throw new Error(`GET /api/game/hero failed: ${response.status}`)
  return response.json()
}

// Mirrors dev.minibrain.game.query.Quest: an open question seen as a quest. *Ru: null = use English.
export type Quest = {
  area: string
  skillKey: string
  skillName: string
  skillNameRu: string | null
  question: string
  questionRu: string | null
  xp: number
}

export async function fetchQuests(): Promise<Quest[]> {
  const response = await fetch('/api/quests')
  if (!response.ok) throw new Error(`GET /api/quests failed: ${response.status}`)
  return response.json()
}

// Mirrors dev.minibrain.skill.query.SkillDetails (the Skill card). *Ru: optional Russian version (null = use English).
export type SkillDetails = {
  key: string
  name: string
  nameRu: string | null
  description: string | null
  descriptionRu: string | null
  status: SkillStatus
  createdAt: string
  updatedAt: string
  evidence: { text: string; textRu: string | null; createdAt: string }[]
  openQuestions: { text: string; textRu: string | null; createdAt: string; resolvedAt: string | null }[]
  // Seen from this skill: outgoing = this skill is the "from" side; key/name/status belong to the other skill.
  relations: { type: RelationType; outgoing: boolean; key: string; name: string; nameRu: string | null; status: SkillStatus }[]
}

export async function fetchSkillDetails(key: string): Promise<SkillDetails> {
  const response = await fetch(`/api/skills/${encodeURIComponent(key)}/details`)
  if (!response.ok) throw new Error(`GET skill ${key} failed: ${response.status}`)
  return response.json()
}

// MINIBRAIN_CONTEXT is copied to an AI as-is, so the frontend does not need its exact shape.
export async function fetchAiContext(key: string, goal: string): Promise<unknown> {
  const query = goal.trim() ? `?goal=${encodeURIComponent(goal.trim())}` : ''
  const response = await fetch(`/api/skills/${encodeURIComponent(key)}/context${query}`)
  if (!response.ok) throw new Error(`GET context ${key} failed: ${response.status}`)
  return response.json()
}

// current.json (backend CurrentState) is only saved to a file, so the frontend does not need its exact shape.
export async function fetchCurrentState(): Promise<unknown> {
  const response = await fetch('/api/exports/current')
  if (!response.ok) throw new Error(`GET current state failed: ${response.status}`)
  return response.json()
}

// Mirrors dev.minibrain.importing.application.ImportPreview.
export type ImportSection = 'NEW_SKILLS' | 'STATUS_CHANGES' | 'EVIDENCE' | 'OPEN_QUESTIONS' | 'RELATIONS' | 'SUGGESTED_SKILLS' | 'TRANSLATIONS' | 'SESSION_NOTES'
export type ImportIssue = { code: string; severity: 'ERROR' | 'WARNING'; message: string }
export type ImportItem = {
  id: number
  section: ImportSection
  skill: string | null
  label: string
  verdict: 'READY' | 'ALREADY_PRESENT' | 'INVALID'
  issues: ImportIssue[]
  selected: boolean
}
export type ImportPreview = { topic: string | null; documentIssues: ImportIssue[]; items: ImportItem[] }

export async function previewImport(text: string): Promise<ImportPreview> {
  const response = await fetch('/api/imports/preview', {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain; charset=utf-8' },
    body: text,
  })
  if (!response.ok) throw new Error(`Preview failed: ${response.status}`)
  return response.json()
}

export async function applyImport(text: string, selectedIds: number[]): Promise<{ applied: number; skipped: number }> {
  const response = await fetch('/api/imports/apply', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text, selectedIds }),
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(problem?.detail ?? `Apply failed: ${response.status}`)
  }
  return response.json()
}

// Mirrors dev.minibrain.learning.persistence.LearningSessionRepository.SessionNotes.
export type SessionNotes = { id: number; topic: string | null; notesEn: string | null; notesRu: string | null; createdAt: string }

export async function fetchSessions(key: string): Promise<SessionNotes[]> {
  const response = await fetch(`/api/skills/${encodeURIComponent(key)}/sessions`)
  if (!response.ok) throw new Error(`GET sessions ${key} failed: ${response.status}`)
  return response.json()
}

// Mirrors dev.minibrain.skill.query.TranslationRequest: English texts without a Russian version.
export type TranslationRequest = {
  type: 'MINIBRAIN_TRANSLATION_REQUEST'
  schemaVersion: number
  skills: { key: string; name?: string; description?: string; evidence?: string[]; openQuestions?: string[] }[]
}

export async function fetchMissingTranslations(skill?: string): Promise<TranslationRequest> {
  const query = skill ? `?skill=${encodeURIComponent(skill)}` : ''
  const response = await fetch(`/api/translations/missing${query}`)
  if (!response.ok) throw new Error(`GET missing translations failed: ${response.status}`)
  return response.json()
}

// Mirrors dev.minibrain.revision.persistence.RevisionRepository.Revision (history, newest first).
export type RevisionChange = {
  type: 'SKILL_CREATED' | 'SKILL_STATUS_CHANGED' | 'EVIDENCE_ADDED' | 'QUESTION_ADDED' | 'QUESTION_RESOLVED'
    | 'RELATION_ADDED' | 'TRANSLATION_ADDED' | 'NOTES_SAVED' | 'SKILL_UNLOCKED'
  skillKey: string | null
  fromStatus: SkillStatus | null
  toStatus: SkillStatus | null
  text: string | null
  relatedKey: string | null
  relationType: RelationType | null
  occurredAt: string
}
export type Revision = { id: number; source: 'INITIAL' | 'IMPORT' | 'MANUAL'; topic: string | null; createdAt: string; changes: RevisionChange[] }

export async function fetchRevisions(limit = 30): Promise<Revision[]> {
  const response = await fetch(`/api/revisions?limit=${limit}`)
  if (!response.ok) throw new Error(`GET revisions failed: ${response.status}`)
  return response.json()
}
