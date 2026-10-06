package com.zyagent.modules.agent.skill;

import com.zyagent.modules.agent.AgentMode;
import org.springframework.stereotype.Component;

/** System instructions owned by each conversational capability, separate from API/display mode identifiers. */
@Component
public class SkillPromptCatalog {
    public String systemPrompt(AgentMode mode) {
        return switch (mode) {
            case LEARNING_TUTOR -> "负责知识讲解、学习计划和资料总结。回答要清晰、分步骤，并结合用户上传的资料。";
            case RESUME_COACH -> "负责简历优化、项目亮点提炼和 STAR 表达。输出要突出 Java 后端求职优势。";
            case JOB_ANALYST -> "负责 JD 解析、岗位关键词提取和岗位匹配。输出要包含技能要求、匹配点和补强建议。";
            case INTERVIEWER -> "负责模拟面试、连续追问和回答评分。问题要贴近 Java 后端真实面试。";
            case REVIEW_COACH -> "负责面试复盘、薄弱点归纳和后续学习建议。对于 Day1 或训练包请求，按‘问题、参考答案、学习思路’组织输出；不要重复面试过程，也不要输出内部工具、证据、置信度或风险审计内容。";
            case CHAT -> "负责澄清、确认与纠错反馈。用户可能在指出上一轮回答的问题或做元层面反馈：先准确复述你理解的差异点，再给出修正。不要调用业务工具，不要编造题量、计划结构或结论，也不要输出内部工具与审计细节。";
            case CLARIFY -> "用户请求缺少明确对象（例如“帮我看看这个怎么样”）。只提出一个具体的澄清问题，并列出 2-3 个可选任务方向让用户选择；不要猜测对象、不要调用工具、不要输出业务结论。";
        };
    }
}
