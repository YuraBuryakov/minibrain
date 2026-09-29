import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Controls, Panel, ReactFlow, ViewportPortal, type Node, type NodeChange, type NodeDimensionChange, type ReactFlowInstance } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { fetchGame, fetchGraph, fetchPositions, pinPosition, type NodePosition, type RelationType, type SkillStatus } from './api'
import { initialLang, LangContext, pick, saveLang, useT, type Lang } from './i18n'
import { HeroBadge } from './HeroBadge'
import { ImportButton } from './ImportDialog'
import { ManageButton } from './ManageDialog'
import { QuestsButton } from './QuestsDialog'
import { SkillCard } from './SkillCard'
import { SkillSearch } from './SkillSearch'
import { SuggestionCard } from './SuggestionCard'
import { edgeTypes, FogOfWar, nodeTypes } from './skillMapParts'
import './skillMap.css'
import { FOG_DISTANCE } from './layout'
import { FOG_PREFIX, toFlow } from './toFlow'

const STATUSES: SkillStatus[] = ['DISCOVERED', 'LEARNING', 'UNDERSTOOD', 'APPLIED', 'MASTERED']
const RELATIONS: RelationType[] = ['REQUIRES', 'LEADS_TO', 'RELATED_TO', 'PART_OF']

export default function App() {
  const [lang, setLang] = useState<Lang>(initialLang)

  useEffect(() => {
    document.documentElement.lang = lang
    saveLang(lang)
  }, [lang])

  return (
    <LangContext.Provider value={lang}>
      <SkillMap lang={lang} onLangChange={setLang} />
    </LangContext.Provider>
  )
}

function SkillMap({ lang, onLangChange }: { lang: Lang; onLangChange: (lang: Lang) => void }) {
  const t = useT()
  const graph = useQuery({ queryKey: ['graph'], queryFn: fetchGraph })
  const [selected, setSelected] = useState<string | null>(null)
  // Status filter in the legend: empty = all statuses. Dims the others, never hides them.
  const [statuses, setStatuses] = useState<ReadonlySet<SkillStatus>>(new Set())
  // Pinned positions (brief §30). While dragging, the cache is updated live; on drop the position is saved.
  const queryClient = useQueryClient()
  const positions = useQuery({ queryKey: ['positions'], queryFn: fetchPositions })
  const pins = useMemo(() => new Map((positions.data ?? []).map((p) => [p.id, { x: p.x, y: p.y }])), [positions.data])
  const movePin = (pin: NodePosition) =>
    queryClient.setQueryData<NodePosition[]>(['positions'], (old = []) => [...old.filter((p) => p.id !== pin.id), pin])
  // The game (G1): area ranks go under the hubs.
  const game = useQuery({ queryKey: ['game'], queryFn: fetchGame })
  const vision = game.data?.player.vision
  const fogHidden = vision !== undefined && vision < FOG_DISTANCE
  const ranks = useMemo(() => new Map((game.data?.areas ?? []).map((a) => [a.key, a.rank])), [game.data])
  // Mastery gate (G3b): fog topics whose source skill is below unlockStatus. No source on the map = no gate.
  const unlockStatus = game.data?.unlockStatus
  const gated = useMemo(() => {
    if (!graph.data || !unlockStatus) return new Set<string>()
    const statusOf = new Map(graph.data.nodes.map((n) => [n.key, n.status]))
    const below = (key: string | null) => !!key && statusOf.has(key) && STATUSES.indexOf(statusOf.get(key)!) < STATUSES.indexOf(unlockStatus)
    return new Set(graph.data.suggestions.filter((s) => below(s.from)).map((s) => s.key))
  }, [graph.data, unlockStatus])
  const flow = useMemo(
    () => (graph.data ? toFlow(graph.data, { selectedKey: selected, lang, statuses, pins, ranks, vision, gated }) : null),
    [graph.data, selected, lang, statuses, pins, ranks, vision, gated],
  )
  const toggleStatus = (status: SkillStatus) =>
    setStatuses((current) => {
      const next = new Set(current)
      if (!next.delete(status)) next.add(status)
      return next
    })
  const [map, setMap] = useState<ReactFlowInstance | null>(null)
  // React Flow keeps measured sizes on its node objects; toFlow rebuilds them on every drag step, which would drop
  // them ("node is not initialized", a jumpy cursor). So the sizes React Flow reports are kept and put back.
  const [measured, setMeasured] = useState<ReadonlyMap<string, Node['measured']>>(new Map())
  const nodes = useMemo(() => flow?.nodes.map((n) => ({ ...n, measured: measured.get(n.id) })) ?? [], [flow, measured])
  const onNodesChange = (changes: NodeChange[]) => {
    const sizes = changes.filter((c): c is NodeDimensionChange => c.type === 'dimensions' && c.dimensions !== undefined)
    if (sizes.length) setMeasured((current) => new Map([...current, ...sizes.map((c) => [c.id, c.dimensions] as const)]))
    // Drag steps go into the pin cache, so a dragged hub carries its area along live.
    for (const c of changes) if (c.type === 'position' && c.position) movePin({ id: c.id, ...c.position })
  }
  // Known land clears the fog: every node except the suggestions themselves.
  const known = useMemo(() => flow?.nodes.filter((n) => n.type !== 'fog').map((n) => n.position) ?? [], [flow])

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

  // A selected fog node shows the suggestion card instead of the Skill card.
  const suggestion = selected?.startsWith(FOG_PREFIX)
    ? graph.data?.suggestions.find((s) => FOG_PREFIX + s.key === selected)
    : undefined
  const source = suggestion?.from ? graph.data?.nodes.find((n) => n.key === suggestion.from) : undefined
  const sourceName = source ? pick(lang, source.name, source.nameRu) : null

  if (graph.isPending) return <p className="message">{t('loading')}</p>
  if (graph.isError) return <p className="message">{t('loadFailed', { error: graph.error.message })}</p>

  return (
    <div className={selected ? 'skill-map skill-map--with-card' : 'skill-map'}>
      <ReactFlow
        nodes={nodes}
        edges={flow!.edges}
        onNodeClick={(_, node) => (node.type === 'skill' || node.type === 'fog') && setSelected(node.id)}
        onPaneClick={() => setSelected(null)}
        onInit={setMap}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        nodeOrigin={[0.5, 0.5]}
        colorMode="dark"
        onNodesChange={onNodesChange}
        onNodeDragStop={(_, node) => {
          const pin = queryClient.getQueryData<NodePosition[]>(['positions'])?.find((p) => p.id === node.id)
          if (pin) pinPosition(pin).catch(() => queryClient.invalidateQueries({ queryKey: ['positions'] }))
        }}
        nodesConnectable={false}
        minZoom={0.2}
        fitView
        fitViewOptions={{ padding: 0.12 }}
      >
        <ViewportPortal>
          <FogOfWar known={known} margin={vision} />
        </ViewportPortal>
        <Panel position="top-right">
          <HeroBadge />
        </Panel>
        <Controls showInteractive={false} />
        <Panel position="top-left" className="map-toolbar">
          <SkillSearch graph={graph.data} hideFog={fogHidden} onSelect={setSelected} />
          <QuestsButton graph={graph.data} onSelect={setSelected} />
          <ImportButton />
          <ManageButton />
          <div className="lang-switch" role="group" aria-label={t('language')}>
            {(['en', 'ru'] as Lang[]).map((l) => (
              <button key={l} type="button" aria-pressed={lang === l} onClick={() => onLangChange(l)}>
                {l === 'en' ? 'EN' : 'RU'}
              </button>
            ))}
          </div>
          <details className="legend">
            <summary>{t('legend')}</summary>
            <p className="legend__hint">{t('legend.filterHint')}</p>
            <ul>
              {STATUSES.map((status) => (
                <li key={status}>
                  <button type="button" className="legend__filter" aria-pressed={statuses.has(status)} onClick={() => toggleStatus(status)}>
                    <span className={`orb orb--${status.toLowerCase()}`} />
                    {t(`legend.${status}`)}
                  </button>
                </li>
              ))}
              {statuses.size > 0 && (
                <li>
                  <button type="button" className="legend__reset" onClick={() => setStatuses(new Set())}>
                    {t('legend.showAll')}
                  </button>
                </li>
              )}
              <li>
                <span className="orb orb--fog" />
                {t('legend.fog')}
              </li>
              {RELATIONS.map((type) => (
                <li key={type}>
                  <span className={`legend__line legend__line--${type.toLowerCase().replace('_', '-')}`} />
                  {t(`relation.${type}`)}
                </li>
              ))}
            </ul>
          </details>
        </Panel>
      </ReactFlow>
      {suggestion ? (
        <SuggestionCard
          key={suggestion.key}
          suggestion={suggestion}
          sourceName={sourceName}
          points={game.data?.player.talentPoints ?? 0}
          gate={gated.has(suggestion.key) ? t(`legend.${unlockStatus!}`) : null}
          hidden={fogHidden}
          onUnlocked={setSelected}
          onClose={() => setSelected(null)}
        />
      ) : (
        selected && !selected.startsWith(FOG_PREFIX) && <SkillCard skillKey={selected} onSelect={setSelected} onClose={() => setSelected(null)} />
      )}
    </div>
  )
}
