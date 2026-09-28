import sessionPrompt from './ai/session-prompt.md?raw'
import { fetchAiContext } from './api'

/** Copies the session instructions + MINIBRAIN_CONTEXT as one text, ready to paste into an AI chat. Throws on failure. */
export async function copyAiSession(skillKey: string, goal: string): Promise<void> {
  const context = await fetchAiContext(skillKey, goal)
  const fence = '```'
  const text = `${sessionPrompt.trimEnd()}\n\n${fence}json\n${JSON.stringify(context, null, 2)}\n${fence}\n`
  await navigator.clipboard.writeText(text)
}
