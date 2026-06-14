import test from 'node:test'
import assert from 'node:assert/strict'
import { createUploadMessage, extractDroppedFile, knowledgeTypeLabel, uploadQuickActions } from './uploadActions.js'

test('knowledgeTypeLabel returns user-facing labels', () => {
  assert.equal(knowledgeTypeLabel('RESUME'), '简历库')
  assert.equal(knowledgeTypeLabel('PROJECT'), '项目库')
  assert.equal(knowledgeTypeLabel('STUDY'), '学习资料库')
})

test('createUploadMessage builds upload card state', () => {
  const message = createUploadMessage({
    filename: 'resume-demo-java-backend.md',
    knowledgeType: 'RESUME',
    chunkCount: 6,
    parseStatus: 'PARSED'
  }, 'STUDY')

  assert.equal(typeof message.id, 'string')
  assert.equal(message.kind, 'upload')
  assert.equal(message.upload.typeLabel, '简历库')
  assert.equal(message.upload.chunkCount, 6)
  assert.equal(message.quickActions.length, 5)
  assert.equal(message.quickActions[0].label, '总结这份文件')
})

test('uploadQuickActions include target filename in prompts', () => {
  const actions = uploadQuickActions('project-demo-ggmall.md', 'PROJECT')

  assert.equal(actions.length, 5)
  assert.ok(actions.every(action => action.prompt.includes('project-demo-ggmall.md')))
})

test('extractDroppedFile returns the first dropped file', () => {
  const first = { name: 'resume.md' }
  const second = { name: 'project.md' }
  const event = {
    dataTransfer: {
      files: [first, second]
    }
  }

  assert.equal(extractDroppedFile(event), first)
})

test('extractDroppedFile returns null when no file is dropped', () => {
  assert.equal(extractDroppedFile({ dataTransfer: { files: [] } }), null)
  assert.equal(extractDroppedFile({}), null)
})
