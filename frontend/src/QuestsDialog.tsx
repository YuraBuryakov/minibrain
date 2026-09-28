import { useQuery } from '@tanstack/react-query'
import { useContext, useRef, useState } from 'react'
import { copyAiSession } from './aiSession'
import { fetchQuests, type KnowledgeGraph, type Quest } from './api'
import { LangContext, pick, useT } from './i18n'

// "Quests" window (docs/game-design.md §10): every open question on the map, grouped by area.
// A quest is finished only through an AI session (openQuestionsResolved), never by a button here.

type Props = { graph: KnowledgeGraph | undefined; onSelect: (key: string) => void }

export function QuestsButton({ graph, onSelect }: Props) {
  const t = useT()
  const dialog = useRef<HTMLDialogElement>(null)
  const [open, setOpen] = useState(false)
  // Imports invalidate every query, so the count follows resolved and new questions.
  const quests = useQuery({ queryKey: ['quests'], queryFn: fetchQuests })

  return (
    <>
      <button
        type="button"
        className="map-button"
        onClick={() => {
          setOpen(true)
          dialog.current?.showModal()
        }}
      >
        {t('quests', { n: quests.data?.length ?? 0 })}
      </button>
      <dialog ref={dialog} className="import" aria-label={t('quests.label')} onClose={() => setOpen(false)}>
        {open && (
          <QuestsBody
            quests={quests.data ?? []}
            graph={graph}
            onSelect={onSelect}
            onClose={() => dialog.current?.close()}
          />
        )}
      </dialog>
    </>
  )
}

function QuestsBody({ quests, graph, onSelect, onClose }: { quests: Quest[]; onClose: () => void } & Props) {
  const t = useT()
  const lang = useContext(LangContext)
  // Which row was copied (or failed): "<skill key>\n<question>".
  const [status, setStatus] = useState<{ id: string; ok: boolean } | null>(null)

  // The backend sends quests ordered by area, so grouping keeps that order.
  const areas = new Map<string, Quest[]>()
  for (const q of quests) areas.set(q.area, [...(areas.get(q.area) ?? []), q])

  // Area label: the hub skill's name when the area has one, else the key.
  function areaLabel(area: string) {
    const hub = graph?.nodes.find((n) => n.key === area)
    return hub ? pick(lang, hub.name, hub.nameRu) : area
  }

  async function take(q: Quest) {
    const id = `${q.skillKey}\n${q.question}`
    try {
      await copyAiSession(q.skillKey, q.question) // the goal in English: the AI context is English-first
      setStatus({ id, ok: true })
    } catch {
      setStatus({ id, ok: false })
    }
    onSelect(q.skillKey)
  }

  return (
    <div className="import__body">
      <header className="notes__head">
        <h2 className="import__title">{t('quests', { n: quests.length })}</h2>
        <button type="button" className="card__close" onClick={onClose} aria-label={t('manage.close')}>
          ×
        </button>
      </header>
      <p className="import__hint">{quests.length === 0 ? t('quests.none') : t('quests.hint')}</p>

      {[...areas].map(([area, rows]) => (
        <section key={area} className="manage__section">
          <h3>{areaLabel(area)}</h3>
          <ul className="manage__list">
            {rows.map((q) => {
              const id = `${q.skillKey}\n${q.question}`
              return (
                <li key={id}>
                  <span className="quests__text">
                    <button
                      type="button"
                      className="quests__skill"
                      onClick={() => {
                        onSelect(q.skillKey)
                        onClose()
                      }}
                    >
                      {pick(lang, q.skillName, q.skillNameRu)}
                    </button>
                    {pick(lang, q.question, q.questionRu)}
                    <small>+{q.xp} XP</small>
                    {status?.id === id && (
                      <em className="card__hint" role="status">
                        {status.ok ? t('quests.copied') : t('ai.copyFailed')}
                      </em>
                    )}
                  </span>
                  <button type="button" className="import__secondary" onClick={() => take(q)}>
                    {t('quests.take')}
                  </button>
                </li>
              )
            })}
          </ul>
        </section>
      ))}
    </div>
  )
}
