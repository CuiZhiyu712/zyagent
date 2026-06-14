export const knowledgeTypeLabels = {
  RESUME: '简历库',
  PROJECT: '项目库',
  STUDY: '学习资料库',
  JOB: '岗位库',
  INTERVIEW_QUESTION: '面试题库',
  REVIEW: '复盘库'
}

export function knowledgeTypeLabel(type) {
  return knowledgeTypeLabels[type] || type || '知识库'
}

export function extractDroppedFile(event) {
  const files = event?.dataTransfer?.files
  return files && files.length > 0 ? files[0] : null
}

export function createUploadMessage(record, knowledgeType) {
  const filename = record?.filename || '未命名文件'
  const typeLabel = knowledgeTypeLabel(record?.knowledgeType || knowledgeType)
  return {
    id: crypto.randomUUID(),
    role: 'assistant',
    kind: 'upload',
    content: `已上传：${filename}\n类型：${typeLabel}\n切片数：${record?.chunkCount ?? 0}\n状态：${record?.parseStatus || 'PARSED'}`,
    upload: {
      filename,
      typeLabel,
      chunkCount: record?.chunkCount ?? 0,
      parseStatus: record?.parseStatus || 'PARSED',
      knowledgeType: record?.knowledgeType || knowledgeType
    },
    quickActions: uploadQuickActions(filename, record?.knowledgeType || knowledgeType),
    streaming: false,
    stopped: false,
    error: ''
  }
}

export function uploadQuickActions(filename, knowledgeType) {
  const name = filename || '刚上传的文件'
  return [
    `请总结这份文件：${name}`,
    `根据这份文件提炼 3 个面试亮点：${name}`,
    `结合我上传的岗位 JD 和资料，分析匹配优势与短板：${name}`,
    `根据这份文件生成 8 个面试问题：${name}`,
    `基于这份文件为我制定 7 天学习计划：${name}`
  ].map((prompt, index) => ({
    id: `${knowledgeType || 'FILE'}-${index}`,
    label: ['总结这份文件', '提炼面试亮点', '结合岗位做匹配', '生成面试问题', '制定学习计划'][index],
    prompt
  }))
}
