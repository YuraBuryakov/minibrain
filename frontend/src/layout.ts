import type { GraphNode } from './api'

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
