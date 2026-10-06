package com.zyagent.modules.agent;

public enum AgentMode {
    LEARNING_TUTOR("学习导师 Agent"),
    RESUME_COACH("简历顾问 Agent"),
    JOB_ANALYST("岗位分析 Agent"),
    INTERVIEWER("技术面试官 Agent"),
    REVIEW_COACH("复盘教练 Agent"),
    CHAT("对话澄清 Agent"),
    CLARIFY("意图澄清 Agent");

    private final String title;
    AgentMode(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

}
