import { useQuery } from '@tanstack/react-query'
import { useContext, useState } from 'react'
import sessionPrompt from './ai/session-prompt.md?raw'
import { fetchAiContext, fetchSkillDetails, type SkillDetails } from './api'
import { LangContext, pick, useT } from './i18n'
import { SessionNotesButton } from './SessionNotesButton'

// Read-only Skill card. Knowledge changes arrive through imports, not through this panel.

type Props = { skillKey: string; onSelect: (key: string) => void; onClose: () => void }

export function SkillCard({ skillKey, onSelect, onClose }: Props) {
  const t = useT()
  const details = useQuery({ queryKey: ['skill', skillKey], queryFn: () => fetchSkillDetails(skillKey) })

  return (
    <aside className="card" aria-label={t('card.label')}>
      <button type="button" className="card__close" onClick={onClose} aria-label={t('card.close')}>
        ×
      </button>
      {details.isPending && <p className="card__note">{t('card.loading')}</p>}
      {details.isError && <p className="card__note">{t('card.loadFailed', { error: details.error.message })}</p>}
      {details.isSuccess && <CardBody key={skillKey} skill={details.data} onSelect={onSelect} />}
    </aside>
  )
}

function CardBody({ skill, onSelect }: { skill: SkillDetails; onSelect: (key: string) => void }) {
  const t = useT()
  const lang = useContext(LangContext)
  const formatDate = (iso: string) => new Date(iso).toLocaleDateString(lang, { day: 'numeric', month: 'short', year: 'numeric' })
  const open = skill.openQuestions.filter((q) => !q.resolvedAt)
  const closed = skill.openQuestions.filter((q) => q.resolvedAt)
  const name = pick(lang, skill.name, skill.nameRu)
  const description = pick(lang, skill.description, skill.descriptionRu)

  return (
    <>
      <header className="card__head">
        <h1 className="card__title">{name}</h1>
        <p className="card__status">
          <span className={`orb orb--${skill.status.toLowerCase()}`} />
          {t(`status.${skill.status}`)}
        </p>
        {description && <p className="card__description">{description}</p>}
        <SessionNotesButton skillKey={skill.key} skillName={name} />
      </header>

      <StudyWithAi skillKey={skill.key} />

      <section className="card__section">
        <h2>{t('card.evidence')}</h2>
        {skill.evidence.length === 0 ? (
          <p className="card__empty">{t('card.noEvidence')}</p>
        ) : (
          <ul className="card__list">
            {skill.evidence.map((e) => (
              <li key={e.text}>
                {pick(lang, e.text, e.textRu)}
                <time className="card__date" dateTime={e.createdAt}>{formatDate(e.createdAt)}</time>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card__section">
        <h2>{t('card.questions')}</h2>
        {open.length === 0 ? (
          <p className="card__empty">{t('card.noQuestions')}</p>
        ) : (
          <ul className="card__list">
            {open.map((q) => <li key={q.text}>{pick(lang, q.text, q.textRu)}</li>)}
          </ul>
        )}
        {closed.length > 0 && (
          <ul className="card__list card__list--closed">
            {closed.map((q) => (
              <li key={q.text}>
                {pick(lang, q.text, q.textRu)}
                <time className="card__date" dateTime={q.resolvedAt!}>{t('card.closedOn', { date: formatDate(q.resolvedAt!) })}</time>
              </li>
            ))}
          </ul>
        )}
      </section>

      {skill.relations.length > 0 && (
        <section className="card__section">
          <h2>{t('card.connections')}</h2>
          <ul className="card__relations">
            {skill.relations.map((r) => (
              <li key={`${r.outgoing}|${r.type}|${r.key}`}>
                <span className="card__relation-type">{t(r.outgoing ? `relation.${r.type}` : `relationIn.${r.type}`)}</span>
                <button type="button" className="card__link" onClick={() => onSelect(r.key)}>
                  <span className={`orb orb--${r.status.toLowerCase()}`} />
                  {pick(lang, r.name, r.nameRu)}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  )
}

// Copies the session instructions + MINIBRAIN_CONTEXT as one text, ready to paste into an AI chat.
function StudyWithAi({ skillKey }: { skillKey: string }) {
  const t = useT()
  const [goal, setGoal] = useState('')
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle')

  async function copy() {
    try {
      const context = await fetchAiContext(skillKey, goal)
      const fence = '```'
      const text = `${sessionPrompt.trimEnd()}\n\n${fence}json\n${JSON.stringify(context, null, 2)}\n${fence}\n`
      await navigator.clipboard.writeText(text)
      setState('copied')
    } catch {
      setState('failed')
    }
  }

  return (
    <section className="card__section">
      <h2>{t('ai.title')}</h2>
      <label className="card__label" htmlFor="session-goal">
        {t('ai.goal')}
      </label>
      <input
        id="session-goal"
        className="card__input"
        value={goal}
        onChange={(e) => {
          setGoal(e.target.value)
          setState('idle')
        }}
        placeholder={t('ai.goalPlaceholder')}
      />
      <button type="button" className="card__action" onClick={copy}>
        {t('ai.copy')}
      </button>
      <p className="card__hint" role="status">
        {state === 'copied' && t('ai.copied')}
        {state === 'failed' && t('ai.copyFailed')}
      </p>
    </section>
  )
}
