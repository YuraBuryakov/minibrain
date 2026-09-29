import { useQuery } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { fetchGame, type KnowledgeGraph } from './api'
import { HeroBody } from './HeroDialog'
import { useT } from './i18n'

// Hero badge (docs/game-design.md §11): title, level, XP bar to the next level, talent points. Click: hero window.
// The game is recomputed by the backend; imports and unlocks invalidate every query, so it refreshes itself.

export function HeroBadge({ graph }: { graph: KnowledgeGraph | undefined }) {
  const t = useT()
  const dialog = useRef<HTMLDialogElement>(null)
  const [open, setOpen] = useState(false)
  const game = useQuery({ queryKey: ['game'], queryFn: fetchGame })
  if (!game.data) return null
  const p = game.data.player
  const progress = (p.xp - p.levelStartXp) / (p.nextLevelXp - p.levelStartXp)

  return (
    <>
      <button
        type="button"
        className="hero"
        aria-label={t('hero.label')}
        title={t('hero.open')}
        onClick={() => {
          setOpen(true)
          dialog.current?.showModal()
        }}
      >
        <span className="hero__title">{t(`hero.title.${p.title}`)}</span>
        <span className="hero__level">{t('hero.level', { level: p.level })}</span>
        <span className="hero__bar" role="progressbar" aria-valuemin={p.levelStartXp} aria-valuemax={p.nextLevelXp} aria-valuenow={p.xp}>
          <span style={{ width: `${Math.round(progress * 100)}%` }} />
        </span>
        <span className="hero__xp">{t('hero.xp', { xp: p.xp, next: p.nextLevelXp })}</span>
        {p.talentPoints > 0 && <span className="hero__points">{t('hero.points', { n: p.talentPoints })}</span>}
      </button>
      <dialog ref={dialog} className="import" aria-label={t('hero.label')} onClose={() => setOpen(false)}>
        {open && <HeroBody game={game.data} graph={graph} onClose={() => dialog.current?.close()} />}
      </dialog>
    </>
  )
}
