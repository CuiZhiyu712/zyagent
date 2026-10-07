import assert from 'node:assert/strict'
import test from 'node:test'
import { formatInterviewCapabilityState } from './interviewCapabilityState.js'

test('formats loading as an in-progress capability check', () => {
  assert.deepEqual(formatInterviewCapabilityState({ status: 'loading' }), {
    label: '检测中',
    warning: false,
    retry: false
  })
})

test('reports unavailable only after a successful capability response', () => {
  assert.deepEqual(formatInterviewCapabilityState({
    status: 'ready',
    capability: { provider: 'llm', available: false, label: 'DeepSeek AI 面试官' }
  }), {
    label: 'DeepSeek AI 面试官 · 未配置',
    warning: true,
    retry: false
  })
})

test('keeps the rule demo as a warning-labeled ready state', () => {
  assert.deepEqual(formatInterviewCapabilityState({
    status: 'ready',
    capability: { provider: 'rule_demo', available: true, label: '规则演示模式' }
  }), {
    label: '规则演示模式',
    warning: true,
    retry: false
  })
})

test('shows an available LLM provider as a non-warning ready state', () => {
  assert.deepEqual(formatInterviewCapabilityState({
    status: 'ready',
    capability: { provider: 'llm', available: true, label: 'DeepSeek AI 面试官' }
  }), {
    label: 'DeepSeek AI 面试官',
    warning: false,
    retry: false
  })
})

test('reports request errors as unknown and offers retry', () => {
  assert.deepEqual(formatInterviewCapabilityState({ status: 'error' }), {
    label: '面试官状态未知',
    warning: true,
    retry: true
  })
})
