import { useQuery } from '@tanstack/react-query'
import { Controls, Panel, ReactFlow } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { fetchGraph, type SkillStatus } from './api'
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

  if (graph.isPending) return <p className="message">Loading the Skill Map…</p>
  if (graph.isError) return <p className="message">Could not load the Skill Map: {graph.error.message}. Is the backend running?</p>
  if (graph.data.nodes.length === 0) return <p className="message">The map is empty. Import a MINIBRAIN_UPDATE file to add your first skills.</p>

  const { nodes, edges } = toFlow(graph.data)

  return (
    <div className="skill-map">
      <ReactFlow
        nodes={nodes}
        edges={edges}
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
        <Panel position="top-left">
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
    </div>
  )
}
