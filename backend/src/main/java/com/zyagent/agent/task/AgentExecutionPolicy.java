package com.zyagent.agent.task;

import java.util.Set;

/**
 * 根据 {@link AgentTaskLimits} 决定一次执行是否可以继续、是否允许重试。
 *
 * <p>重试只对声明为幂等只读的工具开放：本项目的工具都是检索/解析/生成类只读能力，
 * 因此这里显式列出白名单，新增有副作用的工具不会被自动重试。
 */
public final class AgentExecutionPolicy {
    private static final Set<String> IDEMPOTENT_READ_ONLY_TOOLS = Set.of(
        "search_personal_knowledge",
        "parse_job_description",
        "match_resume_job",
        "generate_interview_questions",
        "generate_study_plan",
        "analyze_project_experience",
        "generate_resume_suggestion"
    );

    private final AgentTaskLimits limits;

    public AgentExecutionPolicy(AgentTaskLimits limits) {
        this.limits = limits;
    }

    public AgentTaskLimits limits() {
        return limits;
    }

    public boolean isIdempotentReadOnly(String toolName) {
        return toolName != null && IDEMPOTENT_READ_ONLY_TOOLS.contains(toolName);
    }

    public boolean withinPlanSteps(int stepIndex) {
        return stepIndex < limits.maxPlanSteps();
    }

    public boolean withinToolCalls(int executedCalls) {
        return executedCalls < limits.maxToolCalls();
    }

    /** 已完成 {@code completedAttempts} 次尝试后，是否还允许再重试一次。 */
    public boolean canRetry(String toolName, int completedAttempts) {
        return isIdempotentReadOnly(toolName) && completedAttempts <= limits.maxRetries();
    }
}
