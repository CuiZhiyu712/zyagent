package com.zyagent.interview;

import java.util.List;

/**
 * 面试官 Agent 端口：分别生成**单一**问题与结构化评价。
 *
 * <p>返回原始 JSON 文本（与 LLM 输出形态一致），由 {@link InterviewAgentService} 负责解析、
 * 校验与降级，端口实现本身不保证 JSON 一定合法。
 */
public interface InterviewAgent {
    default String provider() {
        return "test";
    }

    default boolean available() {
        return true;
    }

    default String label() {
        return "测试面试官";
    }

    /** 期望 JSON：{@code {"question":"...","focus":"..."}}。 */
    String nextQuestion(InterviewSession session, List<InterviewTurn> history);

    /**
     * 期望 JSON：四维分数（0-5）、{@code explanations}、{@code evidence}，
     * 以及 {@code followUp}/{@code followUpQuestion} 追问决策。
     */
    String evaluateAnswer(InterviewSession session, String question, String answer, boolean allowFollowUp);
}
