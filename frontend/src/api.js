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
  createInterview: payload => fetch('/api/interviews', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  submitInterviewTurn: (sessionId, answer, requestId) => fetch(`/api/interviews/${sessionId}/turns`, { method: 'POST', headers: jsonHeaders, body: JSON.stringify({ answer, requestId }) }).then(parse),
  getInterview: sessionId => fetch(`/api/interviews/${sessionId}`).then(parse),
  listInterviews: (page = 0, size = 20) => fetch(`/api/interviews?page=${page}&size=${size}`).then(parse),
  answerInterview: (sessionId, answer) => fetch(`/api/interviews/${sessionId}/answer`, { method: 'POST', headers: jsonHeaders, body: JSON.stringify({ answer }) }).then(parse),
  completeInterview: sessionId => fetch(`/api/interviews/${sessionId}/complete`, { method: 'POST' }).then(parse),
  getInterviewCapabilities: () => fetch('/api/interviews/capabilities').then(parse),
  getProfile: () => fetch('/api/profile').then(parse),
  listProfileSkills: () => fetch('/api/profile/skills').then(parse),
  upsertProfileSkill: (skillKey, payload) => fetch(`/api/profile/skills/${encodeURIComponent(skillKey)}`, { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  deleteProfileSkill: skillKey => fetch(`/api/profile/skills/${encodeURIComponent(skillKey)}`, { method: 'DELETE' }).then(parse),
  listSkillEvidence: skillKey => fetch(`/api/profile/skills/${encodeURIComponent(skillKey)}/evidence`).then(parse),
  listSkillHistory: skillKey => fetch(`/api/profile/skills/${encodeURIComponent(skillKey)}/history`).then(parse),
  listProfileSuggestions: (state) => fetch(`/api/profile/suggestions${state ? `?state=${encodeURIComponent(state)}` : ''}`).then(parse),
  suggestProfileSkill: payload => fetch('/api/profile/suggestions', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  approveProfileSuggestion: id => fetch(`/api/profile/suggestions/${id}/approve`, { method: 'POST' }).then(parse),
  rejectProfileSuggestion: (id, note) => fetch(`/api/profile/suggestions/${id}/reject`, { method: 'POST', headers: jsonHeaders, body: JSON.stringify({ note }) }).then(parse),
  editProfileSuggestion: (id, payload) => fetch(`/api/profile/suggestions/${id}`, { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse),
  getAgentTask: taskId => fetch(`/api/agent/tasks/${encodeURIComponent(taskId)}`).then(parse),
  getAgentTaskSteps: taskId => fetch(`/api/agent/tasks/${encodeURIComponent(taskId)}/steps`).then(parse),
  retryAgentTask: taskId => fetch(`/api/agent/tasks/${encodeURIComponent(taskId)}/retry`, { method: 'POST' }).then(parse),
  submitReview: payload => fetch('/api/reviews', { method: 'POST', headers: jsonHeaders, body: JSON.stringify(payload) }).then(parse)
}
