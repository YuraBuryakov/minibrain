import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { applyImport, previewImport, type ImportItem, type ImportPreview, type ImportSection } from './api'

// Import a MINIBRAIN_UPDATE: paste text or pick a file -> preview with checkboxes -> apply the ticked items.

const SECTION_TITLE: Record<ImportSection, string> = {
  NEW_SKILLS: 'New skills',
  STATUS_CHANGES: 'Status changes',
  EVIDENCE: 'Evidence',
  OPEN_QUESTIONS: 'Open questions',
  RELATIONS: 'Relations',
  SUGGESTED_SKILLS: 'Suggested skills (tick to unlock as Discovered)',
  SESSION_NOTES: 'Study notes (saved with the skills this import touches)',
}
const SECTIONS = Object.keys(SECTION_TITLE) as ImportSection[]

export function ImportButton() {
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
        Import
      </button>
      <dialog ref={dialog} className="import" aria-label="Import a MiniBrain update">
        <ImportBody key={session} onDone={() => dialog.current?.close()} />
      </dialog>
    </>
  )
}

function ImportBody({ onDone }: { onDone: () => void }) {
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<string | null>(null)

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
      setResult(`Applied ${r.applied} change${r.applied === 1 ? '' : 's'}.`)
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

  if (result) {
    return (
      <div className="import__body">
        <h2 className="import__title">Import finished</h2>
        <p>{result}</p>
        <div className="import__actions">
          <button type="button" className="card__action" onClick={onDone} autoFocus>
            Back to the map
          </button>
        </div>
      </div>
    )
  }

  if (!preview) {
    return (
      <div className="import__body">
        <h2 className="import__title">Import an update</h2>
        <p className="import__hint">
          Paste the AI's answer (the whole message is fine, notes included) or choose a <code>.json</code> file.
        </p>
        <textarea
          className="import__text"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder='{ "type": "MINIBRAIN_UPDATE", ... }'
          aria-label="MINIBRAIN_UPDATE text"
          autoFocus
        />
        <label className="import__file">
          Or choose a file:{' '}
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
            Preview changes
          </button>
          <button type="button" className="import__secondary" onClick={onDone}>
            Cancel
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
      <h2 className="import__title">{preview.topic ? `Preview: ${preview.topic}` : 'Preview'}</h2>
      {preview.documentIssues.map((issue) => (
        <p key={issue.code} className="import__error" role="alert">{issue.message}</p>
      ))}
      {preview.documentIssues.length === 0 && (
        <p className="import__hint">
          {counts.ready} ready, {counts.present} already in MiniBrain, {counts.invalid} with errors. Only ticked lines are applied.
        </p>
      )}
      <div className="import__sections">
        {SECTIONS.map((section) => {
          const items = preview.items.filter((i) => i.section === section)
          if (items.length === 0) return null
          return (
            <section key={section}>
              <h3>{SECTION_TITLE[section]}</h3>
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
          Apply {selected.size} selected
        </button>
        <button type="button" className="import__secondary" onClick={() => setPreview(null)} disabled={busy}>
          Back
        </button>
      </div>
    </div>
  )
}

function PreviewLine({ item, checked, onToggle }: { item: ImportItem; checked: boolean; onToggle: () => void }) {
  const ready = item.verdict === 'READY'
  return (
    <li className={`import__item import__item--${item.verdict.toLowerCase()}`}>
      <label>
        <input type="checkbox" checked={ready && checked} disabled={!ready} onChange={onToggle} />
        <span>
          {item.label}
          {item.verdict === 'ALREADY_PRESENT' && <em className="import__tag"> already in MiniBrain</em>}
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
