import { useQuery } from '@tanstack/react-query'
import { fetchGame } from './api'
import { useT } from './i18n'

// Hero badge (docs/game-design.md §11): title, level, XP bar to the next level, talent points.
// The game is recomputed by the backend; imports and unlocks invalidate every query, so it refreshes itself.

export function HeroBadge() {
  const t = useT()
  const game = useQuery({ queryKey: ['game'], queryFn: fetchGame })
  if (!game.data) return null
  const p = game.data.player
  const progress = (p.xp - p.levelStartXp) / (p.nextLevelXp - p.levelStartXp)

  return (
    <section className="hero" aria-label={t('hero.label')}>
      <p className="hero__title">{t(`hero.title.${p.title}`)}</p>
      <p className="hero__level">{t('hero.level', { level: p.level })}</p>
      <div className="hero__bar" role="progressbar" aria-valuemin={p.levelStartXp} aria-valuemax={p.nextLevelXp} aria-valuenow={p.xp}>
        <span style={{ width: `${Math.round(progress * 100)}%` }} />
      </div>
      <p className="hero__xp">{t('hero.xp', { xp: p.xp, next: p.nextLevelXp })}</p>
      {p.talentPoints > 0 && <p className="hero__points">{t('hero.points', { n: p.talentPoints })}</p>}
    </section>
  )
}
