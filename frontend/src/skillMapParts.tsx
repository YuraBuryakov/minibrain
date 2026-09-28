import { BaseEdge, Handle, Position, type Edge, type EdgeProps, type Node, type NodeProps } from '@xyflow/react'
import type { SkillStatus } from './api'
import { convexHull } from './layout'

// Custom React Flow node and edge renderers for the skill tree. Styling lives in skillMap.css.

export type SkillNodeData = { name: string; status: SkillStatus; hub: boolean }
export type AreaNodeData = { name: string }
export type FogNodeData = { name: string }
export type RuneEdgeData = { sourceRadius: number; targetRadius: number }

export type SkillNode = Node<SkillNodeData, 'skill'>
export type AreaNode = Node<AreaNodeData, 'area'>
export type CoreNode = Node<Record<string, never>, 'core'>
export type FogNode = Node<FogNodeData, 'fog'>
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

// A suggested skill: not a skill yet, a "?" in the fog (brief §14: the look may change, the model does not care).
function FogOrb({ data }: NodeProps<FogNode>) {
  return (
    <div className="orb orb--fog" title={data.name}>
      <CentreHandles />
      <span className="orb__fog-mark" aria-hidden="true">?</span>
      <span className="orb__label">{data.name}</span>
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

const FOG_MARGIN = 90 // px of clear land around the outermost known nodes
const FOG_SOFTNESS = 45 // blur of the fog edge
const FOG_EXTENT = 20000 // the fog sheet reaches far beyond any map

/**
 * Fog of war (brief §14, presentation only): a dark sheet over everything outside the known land.
 * Known land = the convex hull of the known nodes, widened by FOG_MARGIN with a soft edge, so there is no fog
 * between areas, only beyond the outermost skills. Suggestions sit outside, in the fog.
 * Drawn in flow coordinates, so it pans and zooms with the map. Must be rendered inside <ViewportPortal>.
 * ponytail: static gradient edge, no animated smoke; add an SVG turbulence filter if it should look alive.
 */
export function FogOfWar({ known }: { known: { x: number; y: number }[] }) {
  const hull = convexHull(known)
  if (hull.length === 0) return null
  const sheet = { x: -FOG_EXTENT, y: -FOG_EXTENT, width: 2 * FOG_EXTENT, height: 2 * FOG_EXTENT }
  return (
    <svg className="fog-of-war" width={1} height={1} aria-hidden="true">
      <defs>
        <filter id="fog-soft" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation={FOG_SOFTNESS} />
        </filter>
        <mask id="fog-mask" maskUnits="userSpaceOnUse" {...sheet}>
          <rect {...sheet} fill="#fff" />
          {/* A round stroke as wide as 2 x margin widens the hull by the margin on every side. */}
          <polygon
            points={hull.map((p) => `${p.x},${p.y}`).join(' ')}
            fill="#000"
            stroke="#000"
            strokeWidth={2 * FOG_MARGIN}
            strokeLinejoin="round"
            filter="url(#fog-soft)"
          />
        </mask>
      </defs>
      <rect {...sheet} mask="url(#fog-mask)" className="fog-of-war__sheet" />
    </svg>
  )
}

// Defined at module level: React Flow re-mounts every node if these objects change identity.
export const nodeTypes = { skill: SkillOrb, fog: FogOrb, area: AreaSigil, core: Core }
export const edgeTypes = { rune: RuneLine }

// Radii in px, must match the orb sizes in skillMap.css.
export const RADIUS = { core: 30, hub: 26, skill: 15 }
