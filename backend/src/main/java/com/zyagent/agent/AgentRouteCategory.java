package com.zyagent.agent;

public enum AgentRouteCategory {
    RESUME,
    JOB,
    INTERVIEW,
    STUDY_PLAN,
    KNOWLEDGE_QA,
    REVIEW,
    /** 闲聊、确认与对回答的元层面反馈：不调用业务工具。 */
    CHAT,
    /** 请求缺少明确对象：先向用户澄清，不硬选 Skill。 */
    CLARIFY,
    GENERAL
}
