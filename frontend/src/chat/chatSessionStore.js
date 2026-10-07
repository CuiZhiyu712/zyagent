export const CHAT_CACHE_KEY = 'zyagent.chat.sessions.v1'

function resolveStorage(storage) {
  if (storage) return storage
  try {
    return globalThis.localStorage
  } catch {
    return null
  }
}

export function readChatCache(storage) {
  const target = resolveStorage(storage)
  if (!target) return null
  try {
    const raw = target.getItem(CHAT_CACHE_KEY)
    if (!raw) return null
    const value = JSON.parse(raw)
    if (!value || !Array.isArray(value.sessions)) return null
    return {
      sessions: value.sessions,
      activeSessionId: value.activeSessionId || null
    }
  } catch {
    return null
  }
}

export function writeChatCache(sessions, activeSessionId, storage) {
  const target = resolveStorage(storage)
  if (!target) return
  try {
    target.setItem(CHAT_CACHE_KEY, JSON.stringify({
      sessions,
      activeSessionId: activeSessionId || null
    }))
  } catch {
    // Storage quota and private browsing failures should not block chat.
  }
}

export function restoreActiveSessionId(sessions, cache) {
  if (!Array.isArray(sessions) || sessions.length === 0) return null
  const preferred = cache?.activeSessionId
  return sessions.some(session => session.id === preferred)
    ? preferred
    : sessions[0].id
}

export function mergeChatMessages(currentMessages, loadedMessages) {
  const currentHasAnswer = Array.isArray(currentMessages)
    && currentMessages.some(message => message?.role === 'assistant' && message?.content)
  const loadedHasAnswer = Array.isArray(loadedMessages)
    && loadedMessages.some(message => message?.role === 'assistant' && message?.content)
  if (Array.isArray(loadedMessages)
    && loadedMessages.length >= (currentMessages?.length || 0)
    && (!currentHasAnswer || loadedHasAnswer)) {
    return loadedMessages
  }
  return Array.isArray(currentMessages) ? currentMessages : []
}
