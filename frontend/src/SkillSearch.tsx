import { useContext, useState } from 'react'
import type { KnowledgeGraph } from './api'
import { LangContext, pick, useT } from './i18n'
import { FOG_PREFIX } from './toFlow'

// Search (brief §33): type a name (English or Russian) or a key, pick a match, the map selects it.
// Client-side over the loaded graph. ponytail: fine for hundreds of skills; a SearchSkills endpoint when thousands.

const MAX_RESULTS = 8

type Match = { id: string; label: string; key: string; status: string }

export function SkillSearch({ graph, onSelect }: { graph: KnowledgeGraph; onSelect: (id: string) => void }) {
  const t = useT()
  const lang = useContext(LangContext)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)

  const q = query.trim().toLowerCase()
  const hits = (texts: (string | null)[]) => texts.some((text) => text?.toLowerCase().includes(q))
  const matches: Match[] = q
    ? [
        ...graph.nodes
          .filter((n) => hits([n.name, n.nameRu, n.key]))
          .map((n) => ({ id: n.key, label: pick(lang, n.name, n.nameRu), key: n.key, status: n.status.toLowerCase() })),
        ...graph.suggestions
          .filter((s) => hits([s.name, s.nameRu, s.key]))
          .map((s) => ({ id: FOG_PREFIX + s.key, label: pick(lang, s.name, s.nameRu), key: s.key, status: 'fog' })),
      ].slice(0, MAX_RESULTS)
    : []

  function choose(match: Match | undefined) {
    if (!match) return
    onSelect(match.id)
    setQuery('')
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const step = event.key === 'ArrowDown' ? 1 : -1
      setActive((i) => (matches.length ? (i + step + matches.length) % matches.length : 0))
    } else if (event.key === 'Enter') {
      choose(matches[active])
    } else if (event.key === 'Escape') {
      event.stopPropagation() // clear the search only, keep the selected skill
      setQuery('')
    }
  }

  return (
    <div className="search">
      <input
        type="search"
        className="search__input"
        placeholder={t('search.placeholder')}
        aria-label={t('search.label')}
        role="combobox"
        aria-expanded={matches.length > 0}
        aria-controls="search-results"
        aria-activedescendant={matches.length ? `search-${active}` : undefined}
        value={query}
        onChange={(e) => {
          setQuery(e.target.value)
          setActive(0)
        }}
        onKeyDown={onKeyDown}
      />
      {q && (
        <ul id="search-results" className="search__results" role="listbox">
          {matches.length === 0 && <li className="search__empty">{t('search.none')}</li>}
          {matches.map((m, i) => (
            <li
              key={m.id}
              id={`search-${i}`}
              role="option"
              aria-selected={i === active}
              className={i === active ? 'search__result search__result--active' : 'search__result'}
              onMouseEnter={() => setActive(i)}
              onMouseDown={(e) => e.preventDefault()} // keep focus in the input
              onClick={() => choose(m)}
            >
              <span className={`orb orb--${m.status}`} />
              <span>{m.label}</span>
              <small>{m.key}</small>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
