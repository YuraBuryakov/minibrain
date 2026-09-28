import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Controls, Panel, ReactFlow, ViewportPortal, type ReactFlowInstance } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { fetchGraph, type RelationType, type SkillStatus } from './api'
import { initialLang, LangContext, pick, saveLang, useT, type Lang } from './i18n'
import { ImportButton } from './ImportDialog'
import { ManageButton } from './ManageDialog'
import { SkillCard } from './SkillCard'
import { SkillSearch } from './SkillSearch'
import { SuggestionCard } from './SuggestionCard'
import { edgeTypes, FogOfWar, nodeTypes } from './skillMapParts'
import './skillMap.css'
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
  const flow = useMemo(() => (graph.data ? toFlow(graph.data, selected, lang, statuses) : null), [graph.data, selected, lang, statuses])
  const toggleStatus = (status: SkillStatus) =>
    setStatuses((current) => {
      const next = new Set(current)
      if (!next.delete(status)) next.add(status)
      return next
    })
  const [map, setMap] = useState<ReactFlowInstance | null>(null)
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
        nodes={flow!.nodes}
        edges={flow!.edges}
        onNodeClick={(_, node) => (node.type === 'skill' || node.type === 'fog') && setSelected(node.id)}
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
        <ViewportPortal>
          <FogOfWar known={known} />
        </ViewportPortal>
        <Controls showInteractive={false} />
        <Panel position="top-left" className="map-toolbar">
          <SkillSearch graph={graph.data} onSelect={setSelected} />
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
          onUnlocked={setSelected}
          onClose={() => setSelected(null)}
        />
      ) : (
        selected && !selected.startsWith(FOG_PREFIX) && <SkillCard skillKey={selected} onSelect={setSelected} onClose={() => setSelected(null)} />
      )}
    </div>
  )
}
