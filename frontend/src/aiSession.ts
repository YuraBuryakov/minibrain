import sessionPrompt from './ai/session-prompt.md?raw'
import questPrompt from './ai/quest-prompt.md?raw'
import { fetchAiContext } from './api'

/**
 * Copies the session instructions + MINIBRAIN_CONTEXT as one text, ready to paste into an AI chat. Throws on failure.
 * `questQuestion`: quest mode. The goal is an open question I want to answer myself; the AI first asks it in the
 * words I see (my UI language), while `goal` stays the exact context text so the import can resolve it.
 */
export async function copyAiSession(skillKey: string, goal: string, questQuestion?: string): Promise<void> {
  const context = await fetchAiContext(skillKey, goal)
  const fence = '```'
  const parts = [sessionPrompt.trimEnd()]
  if (questQuestion) parts.push(questPrompt.trimEnd().replace('{question}', () => questQuestion))
  parts.push(`## MINIBRAIN_CONTEXT\n\n${fence}json\n${JSON.stringify(context, null, 2)}\n${fence}\n`)
  await navigator.clipboard.writeText(parts.join('\n\n'))
}
