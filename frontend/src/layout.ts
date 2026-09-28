import type { GraphNode, GraphSuggestion } from './api'

// Radial skill-tree layout (Path of Exile style), pure: graph nodes in, coordinates out.
// Areas come from the key prefix ("ddd.aggregate" -> "ddd") and sit on a ring around the centre;
// the skills of an area fan outwards from the area hub. A skill whose key equals the area ("ddd") is the hub.
// ponytail: deterministic, no saved positions; hybrid AUTO/PINNED positions arrive in step 18.

export type Point = { x: number; y: number }
export type Area = { name: string; hub: Point; hubSkillKey?: string; members: string[] }
export type RadialLayout = { areas: Area[]; positions: Map<string, Point> }

export const areaOf = (key: string) => key.split('.')[0]

const AREA_RING_MIN = 400 // distance from the centre to an area hub
const AREA_RING_PER_AREA = 64
const FAN_STEP = 0.7 // radians between neighbouring skills of one area
const FAN_MAX = 1.7 * Math.PI
const SKILL_RING_MIN = 150 // distance from the hub to its skills
const SKILL_RING_PER_SKILL = 30
const FOG_DISTANCE = 120 // from the source skill, further out from the centre
const FOG_STEP = 1 // radians between suggestions of one source

export function radialLayout(nodes: GraphNode[]): RadialLayout {
  const byArea = new Map<string, GraphNode[]>()
  for (const node of nodes) {
    const area = areaOf(node.key)
    byArea.set(area, [...(byArea.get(area) ?? []), node])
  }

  const names = [...byArea.keys()].sort()
  const areaRing = Math.max(AREA_RING_MIN, names.length * AREA_RING_PER_AREA)
  const positions = new Map<string, Point>()

  const areas = names.map((name, i) => {
    const angle = (i / names.length) * 2 * Math.PI - Math.PI / 2 // first area at the top
    const hub = { x: Math.cos(angle) * areaRing, y: Math.sin(angle) * areaRing }
    const members = byArea.get(name)!
    const hubSkill = members.find((m) => m.key === name)
    if (hubSkill) positions.set(hubSkill.key, hub)

    const leaves = members.filter((m) => m !== hubSkill)
    const span = Math.min(FAN_MAX, (leaves.length - 1) * FAN_STEP)
    const ring = Math.max(SKILL_RING_MIN, leaves.length * SKILL_RING_PER_SKILL)
    leaves.forEach((leaf, j) => {
      const a = leaves.length > 1 ? angle - span / 2 + (j * span) / (leaves.length - 1) : angle
      positions.set(leaf.key, { x: hub.x + Math.cos(a) * ring, y: hub.y + Math.sin(a) * ring })
    })

    return { name, hub, hubSkillKey: hubSkill?.key, members: leaves.map((l) => l.key) }
  })

  return { areas, positions }
}

/**
 * Suggestions sit just outside their source skill (away from the centre), fanned when one source has several.
 * Without a known source they hang outside their area hub (area from the key), or on an outer ring.
 */
export function fogPositions(suggestions: GraphSuggestion[], layout: RadialLayout): Map<string, Point> {
  const hubs = new Map(layout.areas.map((a) => [a.name, a.hub]))
  const anchorOf = (s: GraphSuggestion) => (s.from && layout.positions.get(s.from)) || hubs.get(areaOf(s.key)) || null
  const siblings = new Map<string, number>() // anchor id -> suggestions placed there so far
  const positions = new Map<string, Point>()

  suggestions.forEach((s, i) => {
    const anchor = anchorOf(s) ?? { x: Math.cos(i) * 900, y: Math.sin(i) * 900 }
    const id = `${anchor.x},${anchor.y}`
    const n = siblings.get(id) ?? 0
    siblings.set(id, n + 1)
    const outward = Math.atan2(anchor.y, anchor.x)
    const a = outward + (n % 2 === 0 ? 1 : -1) * Math.ceil(n / 2) * FOG_STEP // 0, +1, -1, +2 ... steps around outward
    positions.set(s.key, { x: anchor.x + Math.cos(a) * FOG_DISTANCE, y: anchor.y + Math.sin(a) * FOG_DISTANCE })
  })
  return positions
}

/** Convex hull (Andrew's monotone chain), counter-clockwise. Used for the fog: everything inside is known land. */
export function convexHull(points: Point[]): Point[] {
  const sorted = [...points].sort((a, b) => a.x - b.x || a.y - b.y)
  if (sorted.length < 3) return sorted
  const cross = (o: Point, a: Point, b: Point) => (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
  const half = (list: Point[]) => {
    const chain: Point[] = []
    for (const p of list) {
      while (chain.length >= 2 && cross(chain[chain.length - 2], chain[chain.length - 1], p) <= 0) chain.pop()
      chain.push(p)
    }
    chain.pop() // the last point starts the other half
    return chain
  }
  return [...half(sorted), ...half([...sorted].reverse())]
}
