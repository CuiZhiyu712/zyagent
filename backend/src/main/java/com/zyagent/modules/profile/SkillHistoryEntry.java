package com.zyagent.modules.profile;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 画像技能的版本/审计历史：记录每次变更的旧值、新值与来源，支持回溯与纠正。
 */
public record SkillHistoryEntry(
    String id,
    String ownerId,
    String skillKey,
    SkillLevel oldLevel,
    SkillLevel newLevel,
    double oldConfidence,
    double newConfidence,
    SkillChangeType changeType,
    String sourceType,
    String sourceId,
    String note,
    LocalDateTime changedAt
) {
    public static SkillHistoryEntry of(String ownerId, String skillKey, UserSkill before, UserSkill after,
                                       SkillChangeType changeType, String note) {
        return new SkillHistoryEntry(UUID.randomUUID().toString(), ownerId, skillKey,
            before == null ? null : before.level(), after == null ? null : after.level(),
            before == null ? 0.0 : before.confidence(), after == null ? 0.0 : after.confidence(),
            changeType,
            after != null ? after.sourceType() : (before == null ? null : before.sourceType()),
            after != null ? after.sourceId() : (before == null ? null : before.sourceId()),
            note == null ? "" : note, LocalDateTime.now());
    }
}
