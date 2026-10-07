export function buildInterviewPayload(form, selectedJob) {
  const jobId = form?.jobId
  const jdSnapshot = selectedJob?.id === jobId && typeof selectedJob.rawText === 'string'
    ? selectedJob.rawText
    : ''

  return {
    jobId,
    jdSnapshot,
    interviewType: form?.interviewType,
    difficulty: form?.difficulty
  }
}
