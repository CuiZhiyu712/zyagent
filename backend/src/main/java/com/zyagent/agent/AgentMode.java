package com.zyagent.agent;

public enum AgentMode {
    LEARNING_TUTOR("学习导师 Agent", "负责知识讲解、学习计划和资料总结。回答要清晰、分步骤，并结合用户上传的资料。"),
    RESUME_COACH("简历顾问 Agent", "负责简历优化、项目亮点提炼和 STAR 表达。输出要突出 Java 后端求职优势。"),
    JOB_ANALYST("岗位分析 Agent", "负责 JD 解析、岗位关键词提取和岗位匹配。输出要包含技能要求、匹配点和补强建议。"),
    INTERVIEWER("技术面试官 Agent", "负责模拟面试、连续追问和回答评分。问题要贴近 Java 后端真实面试。"),
    REVIEW_COACH("复盘教练 Agent", "负责面试复盘、薄弱点归纳和后续学习建议。输出要可执行。");

    private final String title;
    private final String prompt;

    AgentMode(String title, String prompt) {
        this.title = title;
        this.prompt = prompt;
    }

    public String title() {
        return title;
    }

    public String prompt() {
        return prompt;
    }
}
