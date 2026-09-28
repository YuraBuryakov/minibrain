import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Controls, Panel, ReactFlow, type ReactFlowInstance } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { fetchGraph, type SkillStatus } from './api'
import { ImportButton } from './ImportDialog'
import { SkillCard } from './SkillCard'
import { edgeTypes, nodeTypes } from './skillMapParts'
import './skillMap.css'
import { toFlow } from './toFlow'

const STATUS_LEGEND: [SkillStatus, string][] = [
  ['DISCOVERED', 'Know it exists'],
  ['LEARNING', 'Working on it'],
  ['UNDERSTOOD', 'Can explain it'],
  ['APPLIED', 'Used it for real'],
  ['MASTERED', 'Know the trade-offs'],
]

const RELATION_LEGEND: [string, string][] = [
  ['requires', 'Requires'],
  ['leads-to', 'Leads to'],
  ['related-to', 'Related to'],
  ['part-of', 'Part of'],
]

export default function App() {
  const graph = useQuery({ queryKey: ['graph'], queryFn: fetchGraph })
  const [selected, setSelected] = useState<string | null>(null)
  const flow = useMemo(() => (graph.data ? toFlow(graph.data, selected) : null), [graph.data, selected])
  const [map, setMap] = useState<ReactFlowInstance | null>(null)

  // Bring the selected skill into view, left of the card (which covers the right 400px).
  useEffect(() => {
    const node = selected && map?.getNode(selected)
    if (!node || !map) return
    const zoom = map.getZoom()
    const cardOffset = Math.min(400, window.innerWidth) / 2 / zoom
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    map.setCenter(node.position.x + cardOffset, node.position.y, { zoom, duration: reduceMotion ? 0 : 400 })
  }, [selected, map])

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => event.key === 'Escape' && setSelected(null)
    window.addEventListener('keydown', closeOnEscape)
    return () => window.removeEventListener('keydown', closeOnEscape)
  }, [])

  if (graph.isPending) return <p className="message">Loading the Skill Map…</p>
  if (graph.isError) return <p className="message">Could not load the Skill Map: {graph.error.message}. Is the backend running?</p>

  return (
    <div className={selected ? 'skill-map skill-map--with-card' : 'skill-map'}>
      <ReactFlow
        nodes={flow!.nodes}
        edges={flow!.edges}
        onNodeClick={(_, node) => node.type === 'skill' && setSelected(node.id)}
        onPaneClick={() => setSelected(null)}
        onInit={setMap}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        nodeOrigin={[0.5, 0.5]}
        colorMode="dark"
        nodesDraggable={false}
        nodesConnectable={false}
        minZoom={0.2}
        fitView
        fitViewOptions={{ padding: 0.12 }}
      >
        <Controls showInteractive={false} />
        <Panel position="top-left" className="map-toolbar">
          <ImportButton />
          <details className="legend">
            <summary>Legend</summary>
            <ul>
              {STATUS_LEGEND.map(([status, meaning]) => (
                <li key={status}>
                  <span className={`orb orb--${status.toLowerCase()}`} />
                  {meaning}
                </li>
              ))}
              {RELATION_LEGEND.map(([type, label]) => (
                <li key={type}>
                  <span className={`legend__line legend__line--${type}`} />
                  {label}
                </li>
              ))}
            </ul>
          </details>
        </Panel>
      </ReactFlow>
      {selected && <SkillCard skillKey={selected} onSelect={setSelected} onClose={() => setSelected(null)} />}
    </div>
  )
}
