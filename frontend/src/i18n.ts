import { createContext, useContext } from 'react'

// UI texts in English and Russian: a plain dictionary, no i18n library. Knowledge texts (skill names, evidence...)
// come from the backend in both languages and are chosen with pick().

export type Lang = 'en' | 'ru'

const en = {
  loading: 'Loading the Skill Map…',
  loadFailed: 'Could not load the Skill Map: {error}. Is the backend running?',
  import: 'Import',
  legend: 'Legend',
  'status.DISCOVERED': 'Discovered',
  'status.LEARNING': 'Learning',
  'status.UNDERSTOOD': 'Understood',
  'status.APPLIED': 'Applied',
  'status.MASTERED': 'Mastered',
  'legend.DISCOVERED': 'Know it exists',
  'legend.LEARNING': 'Working on it',
  'legend.UNDERSTOOD': 'Can explain it',
  'legend.APPLIED': 'Used it for real',
  'legend.MASTERED': 'Know the trade-offs',
  'relation.REQUIRES': 'Requires',
  'relation.LEADS_TO': 'Leads to',
  'relation.RELATED_TO': 'Related to',
  'relation.PART_OF': 'Part of',
  'relationIn.REQUIRES': 'Required by',
  'relationIn.LEADS_TO': 'Comes after',
  'relationIn.RELATED_TO': 'Related to',
  'relationIn.PART_OF': 'Includes',
  'card.label': 'Skill card',
  'card.close': 'Close the skill card',
  'card.loading': 'Opening the tome…',
  'card.loadFailed': 'Could not load this skill: {error}',
  'card.evidence': 'What I demonstrated',
  'card.noEvidence': 'No evidence yet. It appears after a learning session is imported.',
  'card.questions': 'Open questions',
  'card.noQuestions': 'No open questions.',
  'card.closedOn': 'Closed {date}',
  'card.connections': 'Connections',
  'ai.title': 'Study with AI',
  'ai.goal': 'Session goal (optional)',
  'ai.goalPlaceholder': 'e.g. learn how to choose Aggregate boundaries',
  'ai.copy': 'Copy for AI session',
  'ai.copied': 'Copied. Paste it into a new AI chat and start the session.',
  'ai.copyFailed': 'Could not copy. Check that the backend is running and try again.',
  'notes.button': 'Study notes ({n})',
  'notes.close': 'Close the notes',
  'notes.session': 'Session',
  'notes.otherLanguage': 'Not available in this language; showing the other one.',
  'import.label': 'Import a MiniBrain update',
  'import.title': 'Import an update',
  'import.hint': "Paste the AI's answer (the whole message is fine) or choose a .json file.",
  'import.textLabel': 'MINIBRAIN_UPDATE text',
  'import.file': 'Or choose a file:',
  'import.preview': 'Preview changes',
  'import.cancel': 'Cancel',
  'import.previewTitle': 'Preview',
  'import.summary': '{ready} ready, {present} already in MiniBrain, {invalid} with errors. Only ticked lines are applied.',
  'import.apply': 'Apply {n} selected',
  'import.back': 'Back',
  'import.alreadyPresent': 'already in MiniBrain',
  'import.finished': 'Import finished',
  'import.applied': 'Applied {n} changes.',
  'import.toMap': 'Back to the map',
  'section.NEW_SKILLS': 'New skills',
  'section.STATUS_CHANGES': 'Status changes',
  'section.EVIDENCE': 'Evidence',
  'section.OPEN_QUESTIONS': 'Open questions',
  'section.RELATIONS': 'Relations',
  'section.SUGGESTED_SKILLS': 'Suggested skills (tick to unlock as Discovered)',
  'section.SESSION_NOTES': 'Study notes (saved with the skills this update refers to)',
  'section.TRANSLATIONS': 'Translations (replacing an existing one must be ticked by hand)',
  language: 'Language',
  manage: 'Manage',
  'manage.label': 'Manage MiniBrain',
  'manage.close': 'Close',
  'manage.translation': 'Translation into Russian',
  'manage.hint': 'Copy the request into an AI chat (or attach the file), then paste the AI answer into Import.',
  'manage.summary': '{skills} skills have texts without a Russian version ({texts} texts in total).',
  'manage.none': 'Everything is translated.',
  'manage.copyAll': 'Copy all for AI',
  'manage.download': 'Download as file',
  'manage.copyOne': 'Copy',
  'manage.texts': '{n} texts',
  'manage.copied': 'Copied. Paste it into an AI chat.',
  'manage.failed': 'Could not copy. Try again.',
  'manage.loading': 'Looking for untranslated texts…',
  'manage.importAnswer': 'Import the AI answer',
  'manage.back': 'Back to Manage',
  'manage.export': 'Full export',
  'manage.exportHint': 'Everything MiniBrain knows in one file (current.json): skills, relations, evidence, questions, study notes. Keep it as a backup.',
  'manage.exportButton': 'Download current.json',
  history: 'History',
  'history.empty': 'No changes recorded yet.',
  'history.changes': '{n} changes',
  'history.source.INITIAL': 'Initial state',
  'history.source.IMPORT': 'Import',
  'history.source.MANUAL': 'Manual',
  'history.SKILL_CREATED': 'New skill',
  'history.SKILL_STATUS_CHANGED': 'Status',
  'history.EVIDENCE_ADDED': 'Evidence',
  'history.QUESTION_ADDED': 'Question',
  'history.QUESTION_RESOLVED': 'Question resolved',
  'history.RELATION_ADDED': 'Relation',
  'history.TRANSLATION_ADDED': 'Translation',
  'history.NOTES_SAVED': 'Study notes',
}

type Key = keyof typeof en

const ru: Record<Key, string> = {
  loading: 'Загружаю карту навыков…',
  loadFailed: 'Не удалось загрузить карту: {error}. Бэкенд запущен?',
  import: 'Импорт',
  legend: 'Легенда',
  'status.DISCOVERED': 'Обнаружен',
  'status.LEARNING': 'Изучаю',
  'status.UNDERSTOOD': 'Понимаю',
  'status.APPLIED': 'Применял',
  'status.MASTERED': 'Мастер',
  'legend.DISCOVERED': 'Знаю, что есть',
  'legend.LEARNING': 'Работаю над этим',
  'legend.UNDERSTOOD': 'Могу объяснить',
  'legend.APPLIED': 'Применял на деле',
  'legend.MASTERED': 'Знаю компромиссы',
  'relation.REQUIRES': 'Требует',
  'relation.LEADS_TO': 'Ведёт к',
  'relation.RELATED_TO': 'Связано с',
  'relation.PART_OF': 'Часть',
  'relationIn.REQUIRES': 'Нужен для',
  'relationIn.LEADS_TO': 'Идёт после',
  'relationIn.RELATED_TO': 'Связано с',
  'relationIn.PART_OF': 'Включает',
  'card.label': 'Карточка навыка',
  'card.close': 'Закрыть карточку',
  'card.loading': 'Открываю фолиант…',
  'card.loadFailed': 'Не удалось загрузить навык: {error}',
  'card.evidence': 'Что я показал',
  'card.noEvidence': 'Пока пусто. Появится после импорта учебной сессии.',
  'card.questions': 'Открытые вопросы',
  'card.noQuestions': 'Открытых вопросов нет.',
  'card.closedOn': 'Закрыт {date}',
  'card.connections': 'Связи',
  'ai.title': 'Учиться с AI',
  'ai.goal': 'Цель сессии (необязательно)',
  'ai.goalPlaceholder': 'например, научиться выбирать границы агрегата',
  'ai.copy': 'Скопировать для AI-сессии',
  'ai.copied': 'Скопировано. Вставь в новый чат с AI и начинай сессию.',
  'ai.copyFailed': 'Не удалось скопировать. Проверь, что бэкенд запущен, и попробуй снова.',
  'notes.button': 'Конспекты ({n})',
  'notes.close': 'Закрыть конспекты',
  'notes.session': 'Сессия',
  'notes.otherLanguage': 'На этом языке нет, показываю на другом.',
  'import.label': 'Импорт обновления MiniBrain',
  'import.title': 'Импорт обновления',
  'import.hint': 'Вставь ответ AI (можно целиком) или выбери файл .json.',
  'import.textLabel': 'Текст MINIBRAIN_UPDATE',
  'import.file': 'Или выбери файл:',
  'import.preview': 'Показать изменения',
  'import.cancel': 'Отмена',
  'import.previewTitle': 'Предпросмотр',
  'import.summary': 'Готово: {ready}, уже есть в MiniBrain: {present}, с ошибками: {invalid}. Применяются только отмеченные строки.',
  'import.apply': 'Применить отмеченное ({n})',
  'import.back': 'Назад',
  'import.alreadyPresent': 'уже есть в MiniBrain',
  'import.finished': 'Импорт завершён',
  'import.applied': 'Применено изменений: {n}.',
  'import.toMap': 'Вернуться к карте',
  'section.NEW_SKILLS': 'Новые навыки',
  'section.STATUS_CHANGES': 'Смена статуса',
  'section.EVIDENCE': 'Доказательства (Evidence)',
  'section.OPEN_QUESTIONS': 'Открытые вопросы',
  'section.RELATIONS': 'Связи',
  'section.SUGGESTED_SKILLS': 'Предложенные навыки (отметь, чтобы открыть как «Обнаружен»)',
  'section.SESSION_NOTES': 'Конспект (сохранится у навыков из этого обновления)',
  'section.TRANSLATIONS': 'Переводы (замену существующего перевода отмечай вручную)',
  language: 'Язык',
  manage: 'Управление',
  'manage.label': 'Управление MiniBrain',
  'manage.close': 'Закрыть',
  'manage.translation': 'Перевод на русский',
  'manage.hint': 'Скопируй запрос в чат с AI (или приложи файл), а ответ AI вставь в «Импорт».',
  'manage.summary': 'Навыков с текстами без русской версии: {skills} (всего текстов: {texts}).',
  'manage.none': 'Всё переведено.',
  'manage.copyAll': 'Скопировать всё для AI',
  'manage.download': 'Скачать файлом',
  'manage.copyOne': 'Скопировать',
  'manage.texts': 'текстов: {n}',
  'manage.copied': 'Скопировано. Вставь в чат с AI.',
  'manage.failed': 'Не удалось скопировать. Попробуй ещё раз.',
  'manage.loading': 'Ищу непереведённые тексты…',
  'manage.importAnswer': 'Импортировать ответ AI',
  'manage.back': 'Назад в «Управление»',
  'manage.export': 'Полный экспорт',
  'manage.exportHint': 'Всё, что знает MiniBrain, одним файлом (current.json): навыки, связи, доказательства, вопросы, конспекты. Храни как резервную копию.',
  'manage.exportButton': 'Скачать current.json',
  history: 'История',
  'history.empty': 'Изменений пока нет.',
  'history.changes': 'изменений: {n}',
  'history.source.INITIAL': 'Начальное состояние',
  'history.source.IMPORT': 'Импорт',
  'history.source.MANUAL': 'Вручную',
  'history.SKILL_CREATED': 'Новый навык',
  'history.SKILL_STATUS_CHANGED': 'Статус',
  'history.EVIDENCE_ADDED': 'Доказательство',
  'history.QUESTION_ADDED': 'Вопрос',
  'history.QUESTION_RESOLVED': 'Вопрос закрыт',
  'history.RELATION_ADDED': 'Связь',
  'history.TRANSLATION_ADDED': 'Перевод',
  'history.NOTES_SAVED': 'Конспект',
}

const dictionaries: Record<Lang, Record<Key, string>> = { en, ru }

export const LangContext = createContext<Lang>('en')

/** t('import.apply', { n: 3 }) → "Apply 3 selected" in the current language. */
export function useT() {
  const lang = useContext(LangContext)
  return (key: Key, values: Record<string, string | number> = {}) =>
    dictionaries[lang][key].replace(/\{(\w+)\}/g, (_, name) => String(values[name] ?? ''))
}

/** A knowledge text in the current language, falling back to English when there is no Russian version. */
export function pick(lang: Lang, en: string, ru: string | null | undefined): string
export function pick(lang: Lang, en: string | null, ru: string | null | undefined): string | null
export function pick(lang: Lang, en: string | null, ru: string | null | undefined) {
  return lang === 'ru' && ru ? ru : en
}

export function initialLang(): Lang {
  try {
    const saved = localStorage.getItem('minibrain.lang')
    if (saved === 'en' || saved === 'ru') return saved
  } catch {
    // storage blocked: fall back to the browser language
  }
  return navigator.language.toLowerCase().startsWith('ru') ? 'ru' : 'en'
}

export function saveLang(lang: Lang) {
  try {
    localStorage.setItem('minibrain.lang', lang)
  } catch {
    // not critical: the choice just is not remembered
  }
}
