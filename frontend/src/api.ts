// Mirrors the backend read model dev.minibrain.skill.query.KnowledgeGraph (renderer-neutral).

export type SkillStatus = 'DISCOVERED' | 'LEARNING' | 'UNDERSTOOD' | 'APPLIED' | 'MASTERED'
export type RelationType = 'PART_OF' | 'REQUIRES' | 'RELATED_TO' | 'LEADS_TO'

export type GraphNode = { key: string; name: string; status: SkillStatus }
export type GraphEdge = { from: string; type: RelationType; to: string }
export type KnowledgeGraph = { nodes: GraphNode[]; edges: GraphEdge[] }

export async function fetchGraph(): Promise<KnowledgeGraph> {
  const response = await fetch('/api/graph')
  if (!response.ok) throw new Error(`GET /api/graph failed: ${response.status}`)
  return response.json()
}

// Mirrors dev.minibrain.skill.query.SkillDetails (the Skill card).
export type SkillDetails = {
  key: string
  name: string
  description: string | null
  status: SkillStatus
  createdAt: string
  updatedAt: string
  evidence: { text: string; createdAt: string }[]
  openQuestions: { text: string; createdAt: string; resolvedAt: string | null }[]
  // Seen from this skill: outgoing = this skill is the "from" side; key/name/status belong to the other skill.
  relations: { type: RelationType; outgoing: boolean; key: string; name: string; status: SkillStatus }[]
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
