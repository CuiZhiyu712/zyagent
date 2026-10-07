package com.zyagent.profile;

import java.time.LocalDateTime;

/**
 * 已确认的用户技能。
 *
 * <p>{@code level} 是用户可掌控的熟练度，{@code confidence} 是**系统对证据的置信度**，两者独立记录。
 * {@code version} 用于乐观并发：手动更正时若版本不匹配则拒绝，避免覆盖他人修改。
 */
public record UserSkill(
    String ownerId,
    String skillKey,
    SkillLevel level,
    double confidence,
    String evidence,
    String sourceType,
    String sourceId,
    int version,
    LocalDateTime updatedAt
) {
    public static UserSkill of(String ownerId, String skillKey, SkillLevel level, double confidence,
                               String evidence, String sourceType, String sourceId) {
        return new UserSkill(ownerId, skillKey, level, confidence, evidence, sourceType, sourceId, 1, LocalDateTime.now());
    }

    public UserSkill nextVersion(SkillLevel nextLevel, double nextConfidence, String nextEvidence,
                                 String nextSourceType, String nextSourceId) {
        return new UserSkill(ownerId, skillKey, nextLevel, nextConfidence, nextEvidence,
            nextSourceType, nextSourceId, version + 1, LocalDateTime.now());
    }
}
