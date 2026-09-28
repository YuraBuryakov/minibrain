import { MarkerType, type Edge, type Node } from '@xyflow/react'
import type { KnowledgeGraph, RelationType, SkillStatus } from './api'
import { pick, type Lang } from './i18n'
import { applyPins, fogPositions, hubIdOf, radialLayout, type Point } from './layout'
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

/**
 * Which node ids stay bright; null = all of them. Presentation only (brief §32 focus, backlog status filter).
 * Focus (a selected skill or suggestion): itself, its direct relations, its area hub, the suggestions growing from it.
 * Status filter (empty = off): only skills with a chosen status. Both on: a node must pass both.
 */
function litIds(graph: KnowledgeGraph, selectedKey: string | null, statuses: ReadonlySet<SkillStatus>, hubOf: Map<string, string>) {
  let lit: Set<string> | null = null
  if (selectedKey) {
    const key = selectedKey.startsWith(FOG_PREFIX) ? selectedKey.slice(FOG_PREFIX.length) : selectedKey
    const source = graph.suggestions.find((s) => FOG_PREFIX + s.key === selectedKey)?.from
    lit = new Set([selectedKey, hubOf.get(key) ?? '', source ?? ''])
    for (const e of graph.edges) {
      if (e.from === key) lit.add(e.to)
      if (e.to === key) lit.add(e.from)
    }
    for (const s of graph.suggestions) if (s.from === key) lit.add(FOG_PREFIX + s.key)
  }
  if (statuses.size > 0) {
    const byStatus = new Set(graph.nodes.filter((n) => statuses.has(n.status)).map((n) => n.key))
    lit = lit ? new Set([...lit].filter((id) => byStatus.has(id) || id === selectedKey)) : byStatus
  }
  return lit
}

export type FlowView = {
  selectedKey: string | null
  lang: Lang
  statuses?: ReadonlySet<SkillStatus>
  pins?: ReadonlyMap<string, Point>
  ranks?: ReadonlyMap<string, number> // area key -> rank (game)
}

export function toFlow(graph: KnowledgeGraph, view: FlowView): { nodes: Node[]; edges: Edge[] } {
  const { selectedKey, lang, statuses = new Set<SkillStatus>(), pins = new Map<string, Point>(), ranks = new Map<string, number>() } = view
  const layout = applyPins(radialLayout(graph.nodes), pins)
  const { areas, positions } = layout
  const hubOf = new Map(areas.flatMap((a) => a.members.map((m) => [m, hubIdOf(a)] as [string, string])))
  const lit = litIds(graph, selectedKey, statuses, hubOf)
  const dim = (id: string) => (lit && !lit.has(id) ? 'is-dim' : undefined)
  const hubKeys = new Set(areas.map((a) => a.hubSkillKey).filter(Boolean))
  const radiusOf = new Map<string, number>([['core', RADIUS.core]])

  // Skills and area sigils can be dragged (pinned); the core and suggestions cannot.
  const nodes: Node[] = [{ id: 'core', type: 'core', position: { x: 0, y: 0 }, data: {}, selectable: false, draggable: false }]

  for (const area of areas) {
    if (area.hubSkillKey) continue // the skill itself is the hub
    const id = `area:${area.name}`
    radiusOf.set(id, RADIUS.hub)
    nodes.push({ id, type: 'area', position: area.hub, data: { name: titleCase(area.name), rank: ranks.get(area.name) }, selectable: false, draggable: true, className: dim(id) })
  }

  for (const skill of graph.nodes) {
    const hub = hubKeys.has(skill.key)
    radiusOf.set(skill.key, hub ? RADIUS.hub : RADIUS.skill)
    nodes.push({
      id: skill.key,
      type: 'skill',
      position: positions.get(skill.key)!,
      data: { name: pick(lang, skill.name, skill.nameRu), status: skill.status, hub, rank: hub ? ranks.get(skill.key) : undefined },
      selected: skill.key === selectedKey,
      draggable: true,
      className: dim(skill.key),
    })
  }

  const fog = fogPositions(graph.suggestions, layout)
  for (const s of graph.suggestions) {
    const id = FOG_PREFIX + s.key
    radiusOf.set(id, RADIUS.skill)
    nodes.push({
      id,
      type: 'fog',
      position: fog.get(s.key)!,
      data: { name: pick(lang, s.name, s.nameRu) },
      selected: id === selectedKey,
      draggable: false,
      className: dim(id),
    })
  }

  // In focus only the lines touching the selected node stay bright; otherwise a line is bright when both ends are.
  const edgeDim = (source: string, target: string) =>
    lit && (selectedKey ? source !== selectedKey && target !== selectedKey : !lit.has(source) || !lit.has(target))
  const rune = (id: string, source: string, target: string, className: string, extra: Partial<RuneEdge> = {}): RuneEdge => ({
    id,
    source,
    target,
    type: 'rune',
    className: edgeDim(source, target) ? `${className} is-dim` : className,
    selectable: false,
    data: { sourceRadius: radiusOf.get(source)!, targetRadius: radiusOf.get(target)! },
    ...extra,
  })

  // Structure: core -> area hubs -> their skills (skipped where a PART_OF relation already draws that line).
  const partOf = new Set(graph.edges.filter((e) => e.type === 'PART_OF').map((e) => `${e.from}>${e.to}`))
  const edges: Edge[] = []
  for (const area of areas) {
    const hubId = hubIdOf(area)
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
