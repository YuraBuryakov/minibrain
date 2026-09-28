// Mirrors the backend read model dev.minibrain.skill.query.KnowledgeGraph (renderer-neutral).

export type SkillStatus = 'DISCOVERED' | 'LEARNING' | 'UNDERSTOOD' | 'APPLIED' | 'MASTERED'
export type RelationType = 'PART_OF' | 'REQUIRES' | 'RELATED_TO' | 'LEADS_TO'

export type GraphNode = { key: string; name: string; nameRu: string | null; status: SkillStatus }
export type GraphEdge = { from: string; type: RelationType; to: string }
export type KnowledgeGraph = { nodes: GraphNode[]; edges: GraphEdge[] }

export async function fetchGraph(): Promise<KnowledgeGraph> {
  const response = await fetch('/api/graph')
  if (!response.ok) throw new Error(`GET /api/graph failed: ${response.status}`)
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
    | 'RELATION_ADDED' | 'TRANSLATION_ADDED' | 'NOTES_SAVED'
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
