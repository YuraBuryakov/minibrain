import { useQuery } from '@tanstack/react-query'
import { fetchSkillDetails, type RelationType, type SkillDetails, type SkillStatus } from './api'

// Read-only Skill card. Knowledge changes arrive through imports, not through this panel.

const STATUS_TEXT: Record<SkillStatus, string> = {
  DISCOVERED: 'Discovered',
  LEARNING: 'Learning',
  UNDERSTOOD: 'Understood',
  APPLIED: 'Applied',
  MASTERED: 'Mastered',
}

// How a relation reads from the selected skill's side.
const RELATION_TEXT: Record<RelationType, { outgoing: string; incoming: string }> = {
  REQUIRES: { outgoing: 'Requires', incoming: 'Required by' },
  LEADS_TO: { outgoing: 'Leads to', incoming: 'Comes after' },
  PART_OF: { outgoing: 'Part of', incoming: 'Includes' },
  RELATED_TO: { outgoing: 'Related to', incoming: 'Related to' },
}

const formatDate = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })

type Props = { skillKey: string; onSelect: (key: string) => void; onClose: () => void }

export function SkillCard({ skillKey, onSelect, onClose }: Props) {
  const details = useQuery({ queryKey: ['skill', skillKey], queryFn: () => fetchSkillDetails(skillKey) })

  return (
    <aside className="card" aria-label="Skill card">
      <button type="button" className="card__close" onClick={onClose} aria-label="Close the skill card">
        ×
      </button>
      {details.isPending && <p className="card__note">Opening the tome…</p>}
      {details.isError && <p className="card__note">Could not load this skill: {details.error.message}</p>}
      {details.isSuccess && <CardBody skill={details.data} onSelect={onSelect} />}
    </aside>
  )
}

function CardBody({ skill, onSelect }: { skill: SkillDetails; onSelect: (key: string) => void }) {
  const open = skill.openQuestions.filter((q) => !q.resolvedAt)
  const closed = skill.openQuestions.filter((q) => q.resolvedAt)

  return (
    <>
      <header className="card__head">
        <h1 className="card__title">{skill.name}</h1>
        <p className="card__status">
          <span className={`orb orb--${skill.status.toLowerCase()}`} />
          {STATUS_TEXT[skill.status]}
        </p>
        {skill.description && <p className="card__description">{skill.description}</p>}
      </header>

      <section className="card__section">
        <h2>What I demonstrated</h2>
        {skill.evidence.length === 0 ? (
          <p className="card__empty">No evidence yet. It appears after a learning session is imported.</p>
        ) : (
          <ul className="card__list">
            {skill.evidence.map((e) => (
              <li key={e.text}>
                {e.text}
                <time className="card__date" dateTime={e.createdAt}>{formatDate(e.createdAt)}</time>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card__section">
        <h2>Open questions</h2>
        {open.length === 0 ? (
          <p className="card__empty">No open questions.</p>
        ) : (
          <ul className="card__list">
            {open.map((q) => <li key={q.text}>{q.text}</li>)}
          </ul>
        )}
        {closed.length > 0 && (
          <ul className="card__list card__list--closed">
            {closed.map((q) => (
              <li key={q.text}>
                {q.text}
                <time className="card__date" dateTime={q.resolvedAt!}>Closed {formatDate(q.resolvedAt!)}</time>
              </li>
            ))}
          </ul>
        )}
      </section>

      {skill.relations.length > 0 && (
        <section className="card__section">
          <h2>Connections</h2>
          <ul className="card__relations">
            {skill.relations.map((r) => (
              <li key={`${r.outgoing}|${r.type}|${r.key}`}>
                <span className="card__relation-type">{RELATION_TEXT[r.type][r.outgoing ? 'outgoing' : 'incoming']}</span>
                <button type="button" className="card__link" onClick={() => onSelect(r.key)}>
                  <span className={`orb orb--${r.status.toLowerCase()}`} />
                  {r.name}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  )
}
