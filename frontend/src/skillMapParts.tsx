import { BaseEdge, Handle, Position, type Edge, type EdgeProps, type Node, type NodeProps } from '@xyflow/react'
import type { SkillStatus } from './api'

// Custom React Flow node and edge renderers for the skill tree. Styling lives in skillMap.css.

export type SkillNodeData = { name: string; status: SkillStatus; hub: boolean }
export type AreaNodeData = { name: string }
export type RuneEdgeData = { sourceRadius: number; targetRadius: number }

export type SkillNode = Node<SkillNodeData, 'skill'>
export type AreaNode = Node<AreaNodeData, 'area'>
export type CoreNode = Node<Record<string, never>, 'core'>
export type RuneEdge = Edge<RuneEdgeData, 'rune'>

// Edges attach to the centre of a node; the rune edge trims itself to the orb's rim.
function CentreHandles() {
  return (
    <>
      <Handle type="target" position={Position.Top} className="centre-handle" isConnectable={false} />
      <Handle type="source" position={Position.Bottom} className="centre-handle" isConnectable={false} />
    </>
  )
}

function SkillOrb({ data }: NodeProps<SkillNode>) {
  return (
    <div className={`orb orb--${data.status.toLowerCase()}${data.hub ? ' orb--hub' : ''}`} title={`${data.name}: ${data.status.toLowerCase()}`}>
      <CentreHandles />
      <span className={data.hub ? 'orb__label orb__label--area' : 'orb__label'}>{data.name}</span>
    </div>
  )
}

function AreaSigil({ data }: NodeProps<AreaNode>) {
  return (
    <div className="sigil">
      <CentreHandles />
      <span className="orb__label orb__label--area">{data.name}</span>
    </div>
  )
}

function Core() {
  return (
    <div className="core">
      <CentreHandles />
    </div>
  )
}

function RuneLine({ id, sourceX, sourceY, targetX, targetY, data, markerEnd, style }: EdgeProps<RuneEdge>) {
  const dx = targetX - sourceX
  const dy = targetY - sourceY
  const length = Math.hypot(dx, dy) || 1
  const [ux, uy] = [dx / length, dy / length]
  const from = { x: sourceX + ux * (data?.sourceRadius ?? 0), y: sourceY + uy * (data?.sourceRadius ?? 0) }
  const to = { x: targetX - ux * (data?.targetRadius ?? 0), y: targetY - uy * (data?.targetRadius ?? 0) }
  return <BaseEdge id={id} path={`M ${from.x} ${from.y} L ${to.x} ${to.y}`} markerEnd={markerEnd} style={style} />
}

// Defined at module level: React Flow re-mounts every node if these objects change identity.
export const nodeTypes = { skill: SkillOrb, area: AreaSigil, core: Core }
export const edgeTypes = { rune: RuneLine }

// Radii in px, must match the orb sizes in skillMap.css.
export const RADIUS = { core: 30, hub: 26, skill: 15 }
