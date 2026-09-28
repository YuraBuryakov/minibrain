import { useQuery } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import translatePrompt from './ai/translate-prompt.md?raw'
import { fetchMissingTranslations, type TranslationRequest } from './api'
import { useT } from './i18n'
import { HistorySection } from './HistorySection'
import { ImportBody } from './ImportDialog'

// "Manage" window. First section: export texts without a Russian version for an AI to translate
// (all at once, as a file, or skill by skill). The AI's answer comes back through Import.

type Skill = TranslationRequest['skills'][number]

const textCount = (s: Skill) => (s.name ? 1 : 0) + (s.description ? 1 : 0) + (s.evidence?.length ?? 0) + (s.openQuestions?.length ?? 0)

/** Instructions + the request JSON, ready for an AI chat. */
function forAi(request: TranslationRequest) {
  const fence = '```'
  return `${translatePrompt.trimEnd()}\n\n${fence}json\n${JSON.stringify(request, null, 2)}\n${fence}\n`
}

export function ManageButton() {
  const t = useT()
  const dialog = useRef<HTMLDialogElement>(null)
  const [open, setOpen] = useState(false)

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
        {t('manage')}
      </button>
      <dialog ref={dialog} className="import" aria-label={t('manage.label')} onClose={() => setOpen(false)}>
        {open && <ManageBody onClose={() => dialog.current?.close()} />}
      </dialog>
    </>
  )
}

function ManageBody({ onClose }: { onClose: () => void }) {
  const t = useT()
  // Fresh data on every opening: imports may have translated things in the meantime.
  const missing = useQuery({ queryKey: ['translations', 'missing'], queryFn: () => fetchMissingTranslations(), staleTime: 0 })
  const [status, setStatus] = useState<string | null>(null)
  // Importing the AI answer happens inside this window; the list refreshes afterwards (Import invalidates queries).
  const [importing, setImporting] = useState(false)

  async function copy(skill?: string) {
    try {
      const request = skill ? await fetchMissingTranslations(skill) : missing.data!
      await navigator.clipboard.writeText(forAi(request))
      setStatus(t('manage.copied'))
    } catch {
      setStatus(t('manage.failed'))
    }
  }

  function download() {
    const blob = new Blob([forAi(missing.data!)], { type: 'text/markdown;charset=utf-8' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `minibrain-translation-${new Date().toISOString().slice(0, 10)}.md`
    link.click()
    URL.revokeObjectURL(link.href)
  }

  const skills = missing.data?.skills ?? []
  const total = skills.reduce((sum, s) => sum + textCount(s), 0)

  if (importing) {
    return <ImportBody onDone={() => setImporting(false)} doneLabel={t('manage.back')} />
  }

  return (
    <div className="import__body">
      <header className="notes__head">
        <h2 className="import__title">{t('manage')}</h2>
        <button type="button" className="card__close" onClick={onClose} aria-label={t('manage.close')}>
          ×
        </button>
      </header>

      <section className="manage__section">
        <h3>{t('manage.translation')}</h3>
        {missing.isPending && <p className="import__hint">{t('manage.loading')}</p>}
        {missing.isError && <p className="import__error">{missing.error.message}</p>}
        {missing.isSuccess && skills.length === 0 && <p className="import__hint">{t('manage.none')}</p>}
        {missing.isSuccess && skills.length > 0 && (
          <>
            <p className="import__hint">
              {t('manage.summary', { skills: skills.length, texts: total })} {t('manage.hint')}
            </p>
            <div className="import__actions">
              <button type="button" className="card__action" onClick={() => copy()}>
                {t('manage.copyAll')}
              </button>
              <button type="button" className="import__secondary" onClick={download}>
                {t('manage.download')}
              </button>
              <button type="button" className="import__secondary" onClick={() => setImporting(true)}>
                {t('manage.importAnswer')}
              </button>
            </div>
            <p className="card__hint" role="status">{status}</p>
            <ul className="manage__list">
              {skills.map((s) => (
                <li key={s.key}>
                  <span>
                    {s.key}
                    <small>{t('manage.texts', { n: textCount(s) })}</small>
                  </span>
                  <button type="button" className="import__secondary" onClick={() => copy(s.key)}>
                    {t('manage.copyOne')}
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      <HistorySection />
    </div>
  )
}
