import { useQuery } from '@tanstack/react-query'
import { useContext, useRef, useState } from 'react'
import Markdown from 'react-markdown'
import { fetchSessions, type SessionNotes } from './api'
import { LangContext, useT, type Lang } from './i18n'

// A button on the Skill card that opens a centred reading window with the study notes of this skill's sessions.
// The card itself stays light: notes are only read here.

export function SessionNotesButton({ skillKey, skillName }: { skillKey: string; skillName: string }) {
  const sessions = useQuery({ queryKey: ['sessions', skillKey], queryFn: () => fetchSessions(skillKey) })
  const t = useT()
  const appLang = useContext(LangContext)
  const dialog = useRef<HTMLDialogElement>(null)
  // Starts in the app language; switching here only changes this window.
  const [language, setLanguage] = useState<Lang>(appLang)

  const count = sessions.data?.length ?? 0
  if (count === 0) return null

  return (
    <>
      <button type="button" className="card__action card__action--quiet" onClick={() => dialog.current?.showModal()}>
        {t('notes.button', { n: count })}
      </button>
      <dialog ref={dialog} className="import notes" aria-label={`Study notes: ${skillName}`}>
        <div className="import__body">
          <header className="notes__head">
            <h2 className="import__title">{skillName}</h2>
            <div className="notes__languages" role="group" aria-label={t('language')}>
              {(['en', 'ru'] as Lang[]).map((lang) => (
                <button key={lang} type="button" aria-pressed={language === lang} onClick={() => setLanguage(lang)}>
                  {lang === 'en' ? 'English' : 'Русский'}
                </button>
              ))}
            </div>
            <button type="button" className="card__close" onClick={() => dialog.current?.close()} aria-label={t('notes.close')}>
              ×
            </button>
          </header>
          <div className="notes__sessions">
            {sessions.data!.map((session) => (
              <Session key={session.id} session={session} language={language} />
            ))}
          </div>
        </div>
      </dialog>
    </>
  )
}

function Session({ session, language }: { session: SessionNotes; language: Lang }) {
  const t = useT()
  const formatDate = (iso: string) => new Date(iso).toLocaleDateString(language, { day: 'numeric', month: 'long', year: 'numeric' })
  const wanted = language === 'en' ? session.notesEn : session.notesRu
  const fallback = language === 'en' ? session.notesRu : session.notesEn
  return (
    <article className="notes__session">
      <h3>
        {session.topic ?? t('notes.session')}
        <time dateTime={session.createdAt}>{formatDate(session.createdAt)}</time>
      </h3>
      {!wanted && fallback && <p className="notes__missing">{t('notes.otherLanguage')}</p>}
      <div className="notes__text">
        <Markdown>{wanted ?? fallback ?? ''}</Markdown>
      </div>
    </article>
  )
}
