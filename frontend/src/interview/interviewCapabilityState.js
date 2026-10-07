export function formatInterviewCapabilityState(state) {
  if (state?.status === 'loading') {
    return { label: '检测中', warning: false, retry: false }
  }

  if (state?.status === 'ready') {
    const capability = state.capability
    if (capability?.available === false) {
      return {
        label: `${capability.label || 'AI 面试官'} · 未配置`,
        warning: true,
        retry: false
      }
    }
    if (capability?.provider === 'rule_demo') {
      return { label: '规则演示模式', warning: true, retry: false }
    }
    if (capability?.available === true) {
      return {
        label: capability.label || 'AI 面试官',
        warning: false,
        retry: false
      }
    }
  }

  return { label: '面试官状态未知', warning: true, retry: true }
}
