const jsonHeaders = { 'Content-Type': 'application/json' }

async function parse(response) {
  const data = await response.json()
  if (!response.ok || data.success === false) {
    throw new Error(data.message || '请求失败')
  }
  return data.data
}

export const api = {
  listDocuments: () => fetch('/api/documents').then(parse),
  uploadDocument: (file, knowledgeType) => {
    const form = new FormData()
    form.append('file', file)
    form.append('knowledgeType', knowledgeType)
    return fetch('/api/files/upload', { method: 'POST', body: form }).then(parse)
  },
  listJobs: keyword => fetch(`/api/jobs${keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''}`).then(parse),
  importJobText: payload => fetch('/api/jobs/import-text', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  collectJobs: () => fetch('/api/jobs/collect', { method: 'POST' }).then(parse),
  openBossSession: () => fetch('/api/boss/session/open', { method: 'POST' }).then(parse),
  bossSessionStatus: () => fetch('/api/boss/session/status').then(parse),
  collectBossJobs: payload => fetch('/api/boss/collect', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  deleteInvalidCollectedJobs: () => fetch('/api/jobs/invalid-collected', { method: 'DELETE' }).then(parse),
  listJobCollectLogs: () => fetch('/api/jobs/collect/logs').then(parse),
  listJobSources: () => fetch('/api/job-sources').then(parse),
  saveJobSource: payload => fetch('/api/job-sources', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  updateJobSource: (id, payload) => fetch(`/api/job-sources/${id}`, { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  collectJobSource: id => fetch(`/api/job-sources/${id}/collect`, { method: 'POST' }).then(parse),
  matchResume: (jobId, payload) => fetch(`/api/jobs/${jobId}/match-resume`, { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  chat: payload => fetch('/api/chat/complete', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  listChatSessions: () => fetch('/api/chat/sessions').then(parse),
  createChatSession: payload => fetch('/api/chat/sessions', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  listChatMessages: sessionId => fetch(`/api/chat/sessions/${sessionId}/messages`).then(parse),
  deleteChatSession: sessionId => fetch(`/api/chat/sessions/${sessionId}`, { method: 'DELETE' }).then(parse),
  deleteDocument: documentId => fetch(`/api/documents/${documentId}`, { method: 'DELETE' }).then(parse),
  simulateInterview: payload => fetch('/api/interviews/simulate', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  submitReview: payload => fetch('/api/reviews', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse)
}
