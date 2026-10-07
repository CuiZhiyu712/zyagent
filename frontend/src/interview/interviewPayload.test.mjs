import assert from 'node:assert/strict'
import test from 'node:test'
import { buildInterviewPayload } from './interviewPayload.js'
import { api } from '../api.js'

test('uses raw JD only for the selected job and returns the four interview fields', () => {
  const form = { jobId: 'job-42', interviewType: '系统设计', difficulty: '中等' }
  const selectedJob = {
    id: 'job-42',
    rawText: '岗位要求：熟悉分布式系统',
    company: '示例公司',
    sourceUrl: 'https://example.com/job',
    salary: '面议'
  }

  assert.deepEqual(buildInterviewPayload(form, selectedJob), {
    jobId: 'job-42',
    jdSnapshot: '岗位要求：熟悉分布式系统',
    interviewType: '系统设计',
    difficulty: '中等'
  })
})

test('uses an empty JD snapshot when no selected job is available', () => {
  const form = { jobId: 'job-42', interviewType: '项目深挖', difficulty: '简单' }

  assert.deepEqual(buildInterviewPayload(form, null), {
    jobId: 'job-42',
    jdSnapshot: '',
    interviewType: '项目深挖',
    difficulty: '简单'
  })
})

test('does not leak another job JD when the selected job ID does not match the form', () => {
  const form = { jobId: 'job-42', interviewType: '项目深挖', difficulty: '中等' }
  const selectedJob = { id: 'job-99', rawText: '其他岗位的保密 JD' }

  assert.deepEqual(buildInterviewPayload(form, selectedJob), {
    jobId: 'job-42',
    jdSnapshot: '',
    interviewType: '项目深挖',
    difficulty: '中等'
  })
})

test('does not include unrelated selected-job fields in the request payload', () => {
  const result = buildInterviewPayload(
    { jobId: 'job-42', interviewType: 'Java 基础', difficulty: '困难' },
    { id: 'job-42', rawText: 'JD', company: 'ACME', sourceUrl: 'https://example.com', skills: ['Java'] }
  )

  assert.deepEqual(Object.keys(result).sort(), ['difficulty', 'interviewType', 'jdSnapshot', 'jobId'])
})

test('gets interview capabilities through the existing API response parser', async () => {
  const originalFetch = globalThis.fetch
  let requestedUrl
  let requestOptions
  const capability = { provider: 'rule_demo', available: true, label: '规则演示模式' }
  globalThis.fetch = async (url, options) => {
    requestedUrl = url
    requestOptions = options
    return { ok: true, json: async () => ({ success: true, data: capability }) }
  }

  try {
    assert.deepEqual(await api.getInterviewCapabilities(), capability)
    assert.equal(requestedUrl, '/api/interviews/capabilities')
    assert.equal(requestOptions, undefined)
  } finally {
    globalThis.fetch = originalFetch
  }
})
