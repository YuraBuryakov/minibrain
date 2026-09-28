import { MarkerType, type Edge, type Node } from '@xyflow/react'
import type { KnowledgeGraph, RelationType } from './api'
import { pick, type Lang } from './i18n'
import { fogPositions, radialLayout } from './layout'
import { RADIUS, type RuneEdge } from './skillMapParts'

// The only place where React Flow types meet our graph (brief §31: frontend adapts, backend stays generic).
// Adds presentation-only nodes: the core in the centre and a sigil for areas without their own hub skill.
// Suggestions (fog) become "fog:<key>" nodes, so they never clash with a skill of the same key.

const ARROW_COLOR: Record<RelationType, string> = {
  PART_OF: '#8a7f6e',
  REQUIRES: '#d9772f',
  RELATED_TO: '#7d8fb0',
  LEADS_TO: '#e8bd55',
}

const titleCase = (area: string) => area.replace(/-/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())

export const FOG_PREFIX = 'fog:'

export function toFlow(graph: KnowledgeGraph, selectedKey: string | null, lang: Lang): { nodes: Node[]; edges: Edge[] } {
  const layout = radialLayout(graph.nodes)
  const { areas, positions } = layout
  const hubKeys = new Set(areas.map((a) => a.hubSkillKey).filter(Boolean))
  const radiusOf = new Map<string, number>([['core', RADIUS.core]])

  const nodes: Node[] = [{ id: 'core', type: 'core', position: { x: 0, y: 0 }, data: {}, selectable: false }]

  for (const area of areas) {
    if (area.hubSkillKey) continue // the skill itself is the hub
    const id = `area:${area.name}`
    radiusOf.set(id, RADIUS.hub)
    nodes.push({ id, type: 'area', position: area.hub, data: { name: titleCase(area.name) }, selectable: false })
  }

  for (const skill of graph.nodes) {
    const hub = hubKeys.has(skill.key)
    radiusOf.set(skill.key, hub ? RADIUS.hub : RADIUS.skill)
    nodes.push({
      id: skill.key,
      type: 'skill',
      position: positions.get(skill.key)!,
      data: { name: pick(lang, skill.name, skill.nameRu), status: skill.status, hub },
      selected: skill.key === selectedKey,
    })
  }

  const fog = fogPositions(graph.suggestions, layout)
  for (const s of graph.suggestions) {
    const id = FOG_PREFIX + s.key
    radiusOf.set(id, RADIUS.skill)
    nodes.push({ id, type: 'fog', position: fog.get(s.key)!, data: { name: pick(lang, s.name, s.nameRu) }, selected: id === selectedKey })
  }

  const rune = (id: string, source: string, target: string, className: string, extra: Partial<RuneEdge> = {}): RuneEdge => ({
    id,
    source,
    target,
    type: 'rune',
    className,
    selectable: false,
    data: { sourceRadius: radiusOf.get(source)!, targetRadius: radiusOf.get(target)! },
    ...extra,
  })

  // Structure: core -> area hubs -> their skills (skipped where a PART_OF relation already draws that line).
  const partOf = new Set(graph.edges.filter((e) => e.type === 'PART_OF').map((e) => `${e.from}>${e.to}`))
  const edges: Edge[] = []
  for (const area of areas) {
    const hubId = area.hubSkillKey ?? `area:${area.name}`
    edges.push(rune(`root:${hubId}`, 'core', hubId, 'edge-root'))
    for (const member of area.members) {
      if (!partOf.has(`${member}>${hubId}`)) edges.push(rune(`branch:${member}`, hubId, member, 'edge-branch'))
    }
  }

  // Knowledge: relations, directed, styled by type.
  for (const relation of graph.edges) {
    edges.push(
      rune(`${relation.from}|${relation.type}|${relation.to}`, relation.from, relation.to, `edge-${relation.type.toLowerCase().replace('_', '-')}`, {
        markerEnd: { type: MarkerType.ArrowClosed, color: ARROW_COLOR[relation.type], width: 14, height: 14 },
      }),
    )
  }

  // Fog: a faint dotted line from the source skill to its suggestion.
  for (const s of graph.suggestions) {
    if (s.from && positions.has(s.from)) edges.push(rune(`fog-edge:${s.key}`, s.from, FOG_PREFIX + s.key, 'edge-fog'))
  }

  return { nodes, edges }
}
