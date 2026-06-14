<template>
  <div
    :class="['composer-wrap', dragging ? 'dragging' : '']"
    @dragenter.prevent="handleDragEnter"
    @dragover.prevent="handleDragOver"
    @dragleave.prevent="handleDragLeave"
    @drop.prevent="handleDrop"
  >
    <div class="upload-strip">
      <label>
        <span>资料类型</span>
        <select v-model="knowledgeType" :disabled="streaming || uploading">
          <option value="RESUME">简历</option>
          <option value="PROJECT">项目</option>
          <option value="STUDY">学习资料</option>
          <option value="JOB">岗位 JD</option>
          <option value="INTERVIEW_QUESTION">面试题</option>
          <option value="REVIEW">复盘</option>
        </select>
      </label>
      <input
        ref="fileInput"
        class="file-input"
        type="file"
        accept=".pdf,.doc,.docx,.md,.txt"
        :disabled="streaming || uploading"
        @change="handleFileChange"
      />
      <button class="upload-button" type="button" :disabled="streaming || uploading" @click="pickFile">
        {{ uploading ? '上传中...' : '上传文件' }}
      </button>
      <span class="drop-hint">{{ dragging ? '松开即可上传' : '支持拖拽文件到这里' }}</span>
    </div>
    <div class="composer">
      <textarea
        v-model="draft"
        rows="1"
        placeholder="给 zyagent 发送消息"
        @keydown.enter="handleEnter"
        @input="resize"
        ref="textarea"
      ></textarea>
      <button v-if="streaming" class="send-button stop" type="button" title="停止生成" @click="$emit('stop')">■</button>
      <button v-else class="send-button" type="button" title="发送" :disabled="!draft.trim()" @click="submit">↑</button>
    </div>
    <div class="composer-actions">
      <span>Enter 发送，Shift + Enter 换行</span>
      <button type="button" :disabled="!canRegenerate || streaming" @click="$emit('regenerate')">重新生成</button>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { extractDroppedFile } from '../../chat/uploadActions'

const props = defineProps({
  streaming: {
    type: Boolean,
    default: false
  },
  canRegenerate: {
    type: Boolean,
    default: false
  },
  uploading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['send', 'stop', 'regenerate', 'upload-file'])

const draft = ref('')
const textarea = ref(null)
const fileInput = ref(null)
const knowledgeType = ref('STUDY')
const dragging = ref(false)
let dragDepth = 0

function submit() {
  const content = draft.value.trim()
  if (!content) return
  emit('send', content)
  draft.value = ''
  nextTick(resize)
}

function handleEnter(event) {
  if (event.shiftKey) return
  event.preventDefault()
  submit()
}

function resize() {
  const el = textarea.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 180)}px`
}

function pickFile() {
  fileInput.value?.click()
}

function emitUpload(file) {
  if (!file || propsDisabled()) return
  emit('upload-file', { file, knowledgeType: knowledgeType.value })
}

function handleFileChange(event) {
  const file = event.target.files?.[0]
  if (!file) return
  emitUpload(file)
  event.target.value = ''
}

function handleDragEnter() {
  if (propsDisabled()) return
  dragDepth += 1
  dragging.value = true
}

function handleDragOver() {
  if (propsDisabled()) return
  dragging.value = true
}

function handleDragLeave() {
  dragDepth = Math.max(0, dragDepth - 1)
  if (dragDepth === 0) {
    dragging.value = false
  }
}

function handleDrop(event) {
  dragDepth = 0
  dragging.value = false
  emitUpload(extractDroppedFile(event))
}

function propsDisabled() {
  return props.streaming || props.uploading
}

watch(draft, () => nextTick(resize))
</script>
