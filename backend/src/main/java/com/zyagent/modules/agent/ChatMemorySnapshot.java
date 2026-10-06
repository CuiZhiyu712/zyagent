package com.zyagent.modules.agent;

/**
 * 会话记忆快照 = 聊天历史 + 任务状态。
 *
 * <p>{@code activeSkillId} / {@code currentDay} 来自**持久化的会话任务状态**
 * （{@link ChatTaskState}），是路由的权威来源；聊天记录里的助手 Skill 只在任务状态缺失时兜底。
 */
public record ChatMemorySnapshot(
    int recentMessageCount,
    String summary,
    String activeSkillId,
    int currentDay
) {
    public ChatMemorySnapshot(int recentMessageCount, String summary, String activeSkillId) {
        this(recentMessageCount, summary, activeSkillId, 0);
    }

    public ChatMemorySnapshot(int recentMessageCount, String summary) {
        this(recentMessageCount, summary, null, 0);
    }

    public static ChatMemorySnapshot empty() {
        return new ChatMemorySnapshot(0, "", null, 0);
    }

    public boolean hasActiveSkill() {
        return activeSkillId != null && !activeSkillId.isBlank();
    }
}
