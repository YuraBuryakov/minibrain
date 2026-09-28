import { MarkerType, Position, type Edge, type Node } from '@xyflow/react'
import type { KnowledgeGraph, SkillStatus } from './api'

// The only place where React Flow types meet our graph (brief §31: frontend adapts, backend stays generic).

const STATUS_ORDER: SkillStatus[] = ['DISCOVERED', 'LEARNING', 'UNDERSTOOD', 'APPLIED', 'MASTERED']

const STATUS_COLOR: Record<SkillStatus, string> = {
  DISCOVERED: '#e5e7eb',
  LEARNING: '#fde68a',
  UNDERSTOOD: '#bfdbfe',
  APPLIED: '#bbf7d0',
  MASTERED: '#c4b5fd',
}

const COLUMN_WIDTH = 240
const ROW_HEIGHT = 90

// ponytail: status columns instead of a real layout; replaced by hybrid AUTO/PINNED layout in step 18.
export function toFlow(graph: KnowledgeGraph): { nodes: Node[]; edges: Edge[] } {
  const rowInColumn = new Map<SkillStatus, number>()

  const nodes: Node[] = graph.nodes.map((skill) => {
    const row = rowInColumn.get(skill.status) ?? 0
    rowInColumn.set(skill.status, row + 1)
    return {
      id: skill.key,
      position: { x: STATUS_ORDER.indexOf(skill.status) * COLUMN_WIDTH, y: row * ROW_HEIGHT },
      data: { label: skill.name },
      style: { background: STATUS_COLOR[skill.status] },
      // Columns run left to right, so edges leave on the right and enter on the left.
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
    }
  })

  const edges: Edge[] = graph.edges.map((relation) => ({
    id: `${relation.from}|${relation.type}|${relation.to}`,
    source: relation.from,
    target: relation.to,
    label: relation.type,
    markerEnd: { type: MarkerType.ArrowClosed },
  }))

  return { nodes, edges }
}
