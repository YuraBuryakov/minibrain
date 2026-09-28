import { useQueryClient } from '@tanstack/react-query'
import { useContext, useState } from 'react'
import { dismissSuggestion, unlockSuggestion, type GraphSuggestion } from './api'
import { LangContext, pick, useT } from './i18n'

// Card of a suggested skill in the fog (brief §14): why the AI proposed it, then my decision.
// Unlocking costs a talent point (game G2) and opens the new skill's card; dismiss hides it for good.
// Beyond the character's vision the topic stays a mystery: no name, no reason, no actions.

type Props = {
  suggestion: GraphSuggestion
  sourceName: string | null
  points: number
  hidden: boolean
  onUnlocked: (key: string) => void
  onClose: () => void
}

export function SuggestionCard({ suggestion, sourceName, points, hidden, onUnlocked, onClose }: Props) {
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
      const message = e instanceof Error ? e.message : String(e)
      setError(message === '409' ? t('fog.noPoints') : message)
    }
  }

  return (
    <aside className="card" aria-label={t('fog.label')}>
      <button type="button" className="card__close" onClick={onClose} aria-label={t('card.close')}>
        ×
      </button>
      <header className="card__head">
        <h1 className="card__title">{hidden ? '?' : pick(lang, suggestion.name, suggestion.nameRu)}</h1>
        <p className="card__status">
          <span className="orb orb--fog" />
          {t('fog.status')}
        </p>
        {!hidden && reason && <p className="card__description">{reason}</p>}
        {sourceName && <p className="card__note">{t('fog.from', { name: sourceName })}</p>}
      </header>
      <section className="card__section">
        {hidden ? (
          <p className="card__empty">{t('fog.hidden')}</p>
        ) : (
          <>
            <p className="card__empty">{points > 0 ? t('fog.hint') : t('fog.noPoints')}</p>
            <div className="import__actions">
              <button type="button" className="card__action" disabled={points < 1} onClick={() => decide('unlock')}>
                {t('fog.unlockCost')}
              </button>
              <button type="button" className="import__secondary" onClick={() => decide('dismiss')}>
                {t('fog.dismiss')}
              </button>
            </div>
          </>
        )}
        {error && <p className="import__error">{error}</p>}
      </section>
    </aside>
  )
}
