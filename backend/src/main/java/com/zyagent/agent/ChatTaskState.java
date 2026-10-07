package com.zyagent.agent;

/**
 * 会话级的任务状态 —— 路由的**权威来源**。
 *
 * <p>回答"用户现在正在做什么"：任务类型、当前由哪个 Skill 负责、执行到第几天。
 * 聊天记录只回答"之前聊过什么"，助手消息里的 Skill 只作为审计与兜底，不应当作事实来源。
 */
public record ChatTaskState(
    String taskType,
    String activeSkill,
    int currentDay
) {
    public static ChatTaskState empty() {
        return new ChatTaskState(null, null, 0);
    }

    public boolean hasActiveSkill() {
        return activeSkill != null && !activeSkill.isBlank();
    }

    public boolean sameSkill(String skillId) {
        return skillId != null && skillId.equals(activeSkill);
    }
}
