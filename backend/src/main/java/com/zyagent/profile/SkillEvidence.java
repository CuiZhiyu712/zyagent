package com.zyagent.profile;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 一条技能证据：只存来源摘要与引用，不默认长期复制完整原始文档。
 *
 * <p>{@code sourceType} 区分用户手动、简历解析、岗位匹配、面试评价、学习复盘等来源。
 */
public record SkillEvidence(
    String id,
    String ownerId,
    String skillKey,
    String summary,
    String sourceType,
    String sourceId,
    LocalDateTime createdAt
) {
    public static SkillEvidence of(String ownerId, String skillKey, String summary,
                                   String sourceType, String sourceId) {
        return new SkillEvidence(UUID.randomUUID().toString(), ownerId, skillKey,
            summary == null ? "" : summary, sourceType, sourceId, LocalDateTime.now());
    }
}
