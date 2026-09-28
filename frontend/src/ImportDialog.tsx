import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { applyImport, previewImport, type ImportItem, type ImportPreview, type ImportSection } from './api'
import { useT } from './i18n'

// Import a MINIBRAIN_UPDATE: paste text or pick a file -> preview with checkboxes -> apply the ticked items.

// Display order; titles come from the i18n dictionary ("section.*").
// Server-side labels and issue messages stay in English (they quote the imported texts anyway).
const SECTIONS: ImportSection[] = ['NEW_SKILLS', 'STATUS_CHANGES', 'EVIDENCE', 'OPEN_QUESTIONS', 'RELATIONS', 'SUGGESTED_SKILLS', 'SESSION_NOTES']

export function ImportButton() {
  const t = useT()
  const dialog = useRef<HTMLDialogElement>(null)
  // Remounting the body on every open starts each import from a clean state.
  const [session, setSession] = useState(0)

  return (
    <>
      <button
        type="button"
        className="map-button"
        onClick={() => {
          setSession((n) => n + 1)
          dialog.current?.showModal()
        }}
      >
        {t('import')}
      </button>
      <dialog ref={dialog} className="import" aria-label={t('import.label')}>
        <ImportBody key={session} onDone={() => dialog.current?.close()} />
      </dialog>
    </>
  )
}

function ImportBody({ onDone }: { onDone: () => void }) {
  const t = useT()
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<number | null>(null)

  async function runPreview() {
    setBusy(true)
    setError(null)
    try {
      const p = await previewImport(text)
      setPreview(p)
      setSelected(new Set(p.items.filter((i) => i.selected).map((i) => i.id)))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function runApply() {
    setBusy(true)
    setError(null)
    try {
      const r = await applyImport(text, [...selected])
      await queryClient.invalidateQueries() // the map and any open card show the new state
      setResult(r.applied)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  function toggle(id: number) {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  if (result !== null) {
    return (
      <div className="import__body">
        <h2 className="import__title">{t('import.finished')}</h2>
        <p>{t('import.applied', { n: result })}</p>
        <div className="import__actions">
          <button type="button" className="card__action" onClick={onDone} autoFocus>
            {t('import.toMap')}
          </button>
        </div>
      </div>
    )
  }

  if (!preview) {
    return (
      <div className="import__body">
        <h2 className="import__title">{t('import.title')}</h2>
        <p className="import__hint">{t('import.hint')}</p>
        <textarea
          className="import__text"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder='{ "type": "MINIBRAIN_UPDATE", ... }'
          aria-label={t('import.textLabel')}
          autoFocus
        />
        <label className="import__file">
          {t('import.file')}{' '}
          <input
            type="file"
            accept=".json,.txt,.md,application/json,text/plain"
            onChange={async (e) => {
              const file = e.target.files?.[0]
              if (file) setText(await file.text())
            }}
          />
        </label>
        {error && <p className="import__error" role="alert">{error}</p>}
        <div className="import__actions">
          <button type="button" className="card__action" onClick={runPreview} disabled={busy || !text.trim()}>
            {t('import.preview')}
          </button>
          <button type="button" className="import__secondary" onClick={onDone}>
            {t('import.cancel')}
          </button>
        </div>
      </div>
    )
  }

  const counts = {
    ready: preview.items.filter((i) => i.verdict === 'READY').length,
    present: preview.items.filter((i) => i.verdict === 'ALREADY_PRESENT').length,
    invalid: preview.items.filter((i) => i.verdict === 'INVALID').length,
  }

  return (
    <div className="import__body">
      <h2 className="import__title">{preview.topic ? `${t('import.previewTitle')}: ${preview.topic}` : t('import.previewTitle')}</h2>
      {preview.documentIssues.map((issue) => (
        <p key={issue.code} className="import__error" role="alert">{issue.message}</p>
      ))}
      {preview.documentIssues.length === 0 && (
        <p className="import__hint">
          {t('import.summary', counts)}
        </p>
      )}
      <div className="import__sections">
        {SECTIONS.map((section) => {
          const items = preview.items.filter((i) => i.section === section)
          if (items.length === 0) return null
          return (
            <section key={section}>
              <h3>{t(`section.${section}`)}</h3>
              <ul className="import__items">
                {items.map((item) => (
                  <PreviewLine key={item.id} item={item} checked={selected.has(item.id)} onToggle={() => toggle(item.id)} />
                ))}
              </ul>
            </section>
          )
        })}
      </div>
      {error && <p className="import__error" role="alert">{error}</p>}
      <div className="import__actions">
        <button type="button" className="card__action" onClick={runApply} disabled={busy || selected.size === 0}>
          {t('import.apply', { n: selected.size })}
        </button>
        <button type="button" className="import__secondary" onClick={() => setPreview(null)} disabled={busy}>
          {t('import.back')}
        </button>
      </div>
    </div>
  )
}

function PreviewLine({ item, checked, onToggle }: { item: ImportItem; checked: boolean; onToggle: () => void }) {
  const t = useT()
  const ready = item.verdict === 'READY'
  return (
    <li className={`import__item import__item--${item.verdict.toLowerCase()}`}>
      <label>
        <input type="checkbox" checked={ready && checked} disabled={!ready} onChange={onToggle} />
        <span>
          {item.label}
          {item.verdict === 'ALREADY_PRESENT' && <em className="import__tag"> {t('import.alreadyPresent')}</em>}
        </span>
      </label>
      {item.issues.map((issue) => (
        <p key={issue.code} className={issue.severity === 'ERROR' ? 'import__issue import__issue--error' : 'import__issue'}>
          {issue.message}
        </p>
      ))}
    </li>
  )
}
