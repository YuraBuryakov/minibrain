import { useQuery } from '@tanstack/react-query'
import { useContext } from 'react'
import { fetchHero, type AchievementId, type GameState, type HeroView, type JournalEntry, type KnowledgeGraph } from './api'
import { LangContext, pick, useT } from './i18n'

// Hero window (docs/game-design.md §11): summary, XP over time, area ranks. Achievements and journal: G4c / G4d.

const W = 520
const H = 180
const PAD = { left: 44, right: 12, top: 10, bottom: 24 }
const DAY = 86_400_000
const levelStart = (level: number) => 25 * (level - 1) ** 2 // same curve as GameRules.xpForLevel

type Props = { game: GameState; graph: KnowledgeGraph | undefined; onClose: () => void }

export function HeroBody({ game, graph, onClose }: Props) {
  const t = useT()
  const lang = useContext(LangContext)
  const hero = useQuery({ queryKey: ['hero'], queryFn: fetchHero })
  const p = game.player
  const nameOf = (key: string) => {
    const hub = graph?.nodes.find((n) => n.key === key)
    return hub ? pick(lang, hub.name, hub.nameRu) : key
  }
  const areas = [...game.areas].sort((a, b) => b.xp - a.xp)
  const maxXp = Math.max(1, ...areas.map((a) => a.xp))

  return (
    <div className="import__body">
      <header className="notes__head">
        <h2 className="import__title">{t(`hero.title.${p.title}`)}</h2>
        <button type="button" className="card__close" onClick={onClose} aria-label={t('manage.close')}>
          ×
        </button>
      </header>
      <p className="import__hint">
        {t('hero.level', { level: p.level })} · {t('hero.xp', { xp: p.xp, next: p.nextLevelXp })}
        {p.talentPoints > 0 && ` · ${t('hero.points', { n: p.talentPoints })}`}
      </p>

      {hero.data && <Achievements list={hero.data.achievements} />}

      <section className="manage__section">
        <h3>{t('hero.chart')}</h3>
        {hero.data && hero.data.xpByDay.length > 0 && <XpChart points={hero.data.xpByDay} />}
      </section>

      <section className="manage__section">
        <h3>{t('hero.ranks')}</h3>
        <ul className="hero-ranks">
          {areas.map((a) => (
            <li key={a.key} className={a.complete ? 'is-constellation' : undefined}>
              <span className="hero-ranks__name">
                {nameOf(a.key)} · {t(`rank.${Math.min(a.rank, 5) as 1 | 2 | 3 | 4 | 5}`)}
              </span>
              <span className="hero-ranks__bar">
                <span style={{ width: `${(a.xp / maxXp) * 100}%` }} />
              </span>
              <span className="hero-ranks__xp">{a.xp}</span>
            </li>
          ))}
        </ul>
      </section>

      {hero.data && hero.data.journal.length > 0 && <Journal entries={hero.data.journal} nameOf={nameOf} />}
    </div>
  )
}

const LOSSES = new Set(['LEVEL_DOWN', 'RANK_DOWN', 'CONSTELLATION_LOST'])

// Journal of deeds (§11): newest first, grouped by local day. Losses are shown too, in ember (§3: the game is honest).
function Journal({ entries, nameOf }: { entries: JournalEntry[]; nameOf: (key: string) => string }) {
  const t = useT()
  const lang = useContext(LangContext)
  const days = new Map<string, JournalEntry[]>()
  for (const e of entries) {
    const day = new Date(e.at).toLocaleDateString(lang)
    days.set(day, [...(days.get(day) ?? []), e])
  }
  const text = (e: JournalEntry) => {
    const rank = t(`rank.${Math.min(Math.max(e.value, 1), 5) as 1 | 2 | 3 | 4 | 5}`)
    const name = e.kind === 'ACHIEVEMENT' ? t(`achievement.${e.subject as AchievementId}`) : e.subject ? nameOf(e.subject) : ''
    return t(`deed.${e.kind}`, { n: e.value, rank, name })
  }
  return (
    <section className="manage__section">
      <h3>{t('hero.journal')}</h3>
      {[...days].map(([day, list]) => (
        <div key={day} className="journal__day">
          <h4>{day}</h4>
          <ul className="journal">
            {list.map((e, i) => (
              <li key={i} className={LOSSES.has(e.kind) ? 'is-loss' : e.kind === 'ACHIEVEMENT' ? 'is-achievement' : undefined}>
                {text(e)}
              </li>
            ))}
          </ul>
        </div>
      ))}
    </section>
  )
}

// Earned first (oldest first), then locked ones dimmed with their condition (§9). Earned once = kept.
function Achievements({ list }: { list: HeroView['achievements'] }) {
  const t = useT()
  const lang = useContext(LangContext)
  const earned = list.filter((a) => a.earnedAt).sort((a, b) => a.earnedAt!.localeCompare(b.earnedAt!))
  const locked = list.filter((a) => !a.earnedAt)
  return (
    <section className="manage__section">
      <h3>{t('hero.achievements', { n: earned.length, total: list.length })}</h3>
      <ul className="achievements">
        {[...earned, ...locked].map((a) => (
          <li key={a.id} className={a.earnedAt ? 'achievement is-earned' : 'achievement'}>
            <span className="achievement__name">{t(`achievement.${a.id}`)}</span>
            <span className="achievement__how">{t(`achievement.${a.id}.how`)}</span>
            {a.earnedAt && <span className="achievement__date">{new Date(a.earnedAt).toLocaleDateString(lang)}</span>}
          </li>
        ))}
      </ul>
    </section>
  )
}

// XP over real time: x proportional to the date; dashed lines where levels start; the top line is the next level.
// ponytail: one line per level, dense past level ~10; thin them out when it bites.
function XpChart({ points }: { points: { day: string; xp: number }[] }) {
  const t = useT()
  const times = points.map((pt) => Date.parse(pt.day))
  const t0 = times[0]
  const span = Math.max(DAY, times[times.length - 1] - t0) // at least a day, so a single point sits on the left
  const top = Math.max(...points.map((pt) => pt.xp))
  let next = 2
  while (levelStart(next) <= top) next++
  const yMax = levelStart(next)
  const x = (time: number) => PAD.left + ((time - t0) / span) * (W - PAD.left - PAD.right)
  const y = (xp: number) => H - PAD.bottom - (xp / yMax) * (H - PAD.top - PAD.bottom)
  const levels = Array.from({ length: next - 1 }, (_, i) => i + 2) // 2 .. next
  const line = points.map((pt, i) => `${i ? 'L' : 'M'}${x(times[i]).toFixed(1)},${y(pt.xp).toFixed(1)}`).join(' ')

  return (
    <svg className="xp-chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={t('hero.chart')}>
      {levels.map((level) => (
        <g key={level}>
          <line className="xp-chart__level" x1={PAD.left} x2={W - PAD.right} y1={y(levelStart(level))} y2={y(levelStart(level))} />
          <text className="xp-chart__label" x={PAD.left - 6} y={y(levelStart(level)) + 4} textAnchor="end">
            {t('hero.levelShort', { level })}
          </text>
        </g>
      ))}
      <path className="xp-chart__line" d={line} />
      {points.map((pt, i) => (
        <circle key={pt.day} className="xp-chart__dot" cx={x(times[i])} cy={y(pt.xp)} r={3.5}>
          <title>{`${pt.day}: ${pt.xp} XP`}</title>
        </circle>
      ))}
      <text className="xp-chart__label" x={PAD.left} y={H - 6}>
        {points[0].day}
      </text>
      <text className="xp-chart__label" x={W - PAD.right} y={H - 6} textAnchor="end">
        {points[points.length - 1].day}
      </text>
    </svg>
  )
}
