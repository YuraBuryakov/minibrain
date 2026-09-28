import { useQuery } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import Markdown from 'react-markdown'
import { fetchSessions, type SessionNotes } from './api'

// A button on the Skill card that opens a centred reading window with the study notes of this skill's sessions.
// The card itself stays light: notes are only read here.

type Language = 'en' | 'ru'
const initialLanguage: Language = navigator.language.toLowerCase().startsWith('ru') ? 'ru' : 'en'

const formatDate = (iso: string) => new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })

export function SessionNotesButton({ skillKey, skillName }: { skillKey: string; skillName: string }) {
  const sessions = useQuery({ queryKey: ['sessions', skillKey], queryFn: () => fetchSessions(skillKey) })
  const dialog = useRef<HTMLDialogElement>(null)
  const [language, setLanguage] = useState<Language>(initialLanguage)

  const count = sessions.data?.length ?? 0
  if (count === 0) return null

  return (
    <>
      <button type="button" className="card__action card__action--quiet" onClick={() => dialog.current?.showModal()}>
        Study notes ({count})
      </button>
      <dialog ref={dialog} className="import notes" aria-label={`Study notes: ${skillName}`}>
        <div className="import__body">
          <header className="notes__head">
            <h2 className="import__title">{skillName}</h2>
            <div className="notes__languages" role="group" aria-label="Language">
              {(['en', 'ru'] as Language[]).map((lang) => (
                <button key={lang} type="button" aria-pressed={language === lang} onClick={() => setLanguage(lang)}>
                  {lang === 'en' ? 'English' : 'Русский'}
                </button>
              ))}
            </div>
            <button type="button" className="card__close" onClick={() => dialog.current?.close()} aria-label="Close the notes">
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

function Session({ session, language }: { session: SessionNotes; language: Language }) {
  const wanted = language === 'en' ? session.notesEn : session.notesRu
  const fallback = language === 'en' ? session.notesRu : session.notesEn
  return (
    <article className="notes__session">
      <h3>
        {session.topic ?? 'Session'}
        <time dateTime={session.createdAt}>{formatDate(session.createdAt)}</time>
      </h3>
      {!wanted && fallback && <p className="notes__missing">Not available in this language; showing the other one.</p>}
      <div className="notes__text">
        <Markdown>{wanted ?? fallback ?? ''}</Markdown>
      </div>
    </article>
  )
}
