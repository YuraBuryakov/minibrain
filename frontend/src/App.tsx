import { useQuery } from '@tanstack/react-query'
import { Background, Controls, ReactFlow } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { fetchGraph } from './api'
import { toFlow } from './toFlow'

export default function App() {
  const graph = useQuery({ queryKey: ['graph'], queryFn: fetchGraph })

  if (graph.isPending) return <p className="message">Loading Skill Map…</p>
  if (graph.isError) return <p className="message">Could not load the graph: {graph.error.message}</p>

  const { nodes, edges } = toFlow(graph.data)

  return (
    <ReactFlow nodes={nodes} edges={edges} fitView>
      <Background />
      <Controls />
    </ReactFlow>
  )
}
