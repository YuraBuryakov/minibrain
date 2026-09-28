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
