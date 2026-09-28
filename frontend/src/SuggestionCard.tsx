import { useQueryClient } from '@tanstack/react-query'
import { useContext, useState } from 'react'
import { dismissSuggestion, unlockSuggestion, type GraphSuggestion } from './api'
import { LangContext, pick, useT } from './i18n'

// Card of a suggested skill in the fog (brief §14): why the AI proposed it, then my decision.
// Unlock makes it a DISCOVERED skill (and opens its Skill card); dismiss hides it for good.

type Props = { suggestion: GraphSuggestion; sourceName: string | null; onUnlocked: (key: string) => void; onClose: () => void }

export function SuggestionCard({ suggestion, sourceName, onUnlocked, onClose }: Props) {
  const t = useT()
  const lang = useContext(LangContext)
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const reason = pick(lang, suggestion.reason, suggestion.reasonRu)

  async function decide(action: 'unlock' | 'dismiss') {
    try {
      await (action === 'unlock' ? unlockSuggestion : dismissSuggestion)(suggestion.key)
      await queryClient.invalidateQueries()
      if (action === 'unlock') onUnlocked(suggestion.key)
      else onClose()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  return (
    <aside className="card" aria-label={t('fog.label')}>
      <button type="button" className="card__close" onClick={onClose} aria-label={t('card.close')}>
        ×
      </button>
      <header className="card__head">
        <h1 className="card__title">{pick(lang, suggestion.name, suggestion.nameRu)}</h1>
        <p className="card__status">
          <span className="orb orb--fog" />
          {t('fog.status')}
        </p>
        {reason && <p className="card__description">{reason}</p>}
        {sourceName && <p className="card__note">{t('fog.from', { name: sourceName })}</p>}
      </header>
      <section className="card__section">
        <p className="card__empty">{t('fog.hint')}</p>
        <div className="import__actions">
          <button type="button" className="card__action" onClick={() => decide('unlock')}>
            {t('fog.unlock')}
          </button>
          <button type="button" className="import__secondary" onClick={() => decide('dismiss')}>
            {t('fog.dismiss')}
          </button>
        </div>
        {error && <p className="import__error">{error}</p>}
      </section>
    </aside>
  )
}
