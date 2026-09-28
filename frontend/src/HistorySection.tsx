import { useQuery } from '@tanstack/react-query'
import { useContext } from 'react'
import { fetchRevisions, type RevisionChange } from './api'
import { LangContext, useT } from './i18n'

// Revision history (brief §22) in the Manage window: newest first, each revision expands to its changes.

export function HistorySection() {
  const t = useT()
  const lang = useContext(LangContext)
  const revisions = useQuery({ queryKey: ['revisions'], queryFn: () => fetchRevisions(30), staleTime: 0 })
  const formatDate = (iso: string) =>
    new Date(iso).toLocaleString(lang, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })

  return (
    <section className="manage__section">
      <h3>{t('history')}</h3>
      {revisions.isPending && <p className="import__hint">{t('manage.loading')}</p>}
      {revisions.isError && <p className="import__error">{revisions.error.message}</p>}
      {revisions.isSuccess && revisions.data.length === 0 && <p className="import__hint">{t('history.empty')}</p>}
      {revisions.isSuccess && revisions.data.length > 0 && (
        <ul className="history">
          {revisions.data.map((revision) => (
            <li key={revision.id}>
              <details>
                <summary>
                  <time dateTime={revision.createdAt}>{formatDate(revision.createdAt)}</time>
                  <span className="history__source">{t(`history.source.${revision.source}`)}</span>
                  {revision.topic && <span>{revision.topic}</span>}
                  <small>{t('history.changes', { n: revision.changes.length })}</small>
                </summary>
                <ul className="history__changes">
                  {revision.changes.map((change, i) => (
                    <li key={i}>
                      <ChangeLine change={change} />
                    </li>
                  ))}
                </ul>
              </details>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function ChangeLine({ change: c }: { change: RevisionChange }) {
  const t = useT()
  const status = (s: RevisionChange['toStatus']) => (s ? t(`status.${s}`) : '')
  switch (c.type) {
    case 'SKILL_CREATED':
      return <>{t('history.SKILL_CREATED')}: <b>{c.text}</b> ({c.skillKey}), {status(c.toStatus)}</>
    case 'SKILL_STATUS_CHANGED':
      return <>{c.skillKey}: {status(c.fromStatus)} → <b>{status(c.toStatus)}</b></>
    case 'RELATION_ADDED':
      return <>{t('history.RELATION_ADDED')}: {c.skillKey} {c.relationType && t(`relation.${c.relationType}`).toLowerCase()} {c.relatedKey}</>
    case 'NOTES_SAVED':
      return <>{t('history.NOTES_SAVED')}{c.text ? `: ${c.text}` : ''}</>
    default:
      return <>{t(`history.${c.type}`)}: {c.skillKey}: {c.text}</>
  }
}
