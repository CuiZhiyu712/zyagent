export function buildInterviewPayload(form, selectedJob) {
  const jobId = form?.jobId
  const selectedJobId = normalizeInterviewJobId(selectedJob?.id)
  const jdSnapshot = selectedJobId && selectedJobId === normalizeInterviewJobId(jobId)
    && typeof selectedJob.rawText === 'string'
    ? selectedJob.rawText
    : ''

  return {
    jobId,
    jdSnapshot,
    interviewType: form?.interviewType,
    difficulty: form?.difficulty
  }
}

export function findInterviewJob(jobs, jobId) {
  const targetId = normalizeInterviewJobId(jobId)
  if (!targetId || !Array.isArray(jobs)) return null

  return jobs.find(job => normalizeInterviewJobId(job?.id) === targetId) ?? null
}

function normalizeInterviewJobId(id) {
  if (typeof id !== 'string' && typeof id !== 'number') return ''
  if (typeof id === 'number' && !Number.isFinite(id)) return ''
  return String(id).trim()
}
