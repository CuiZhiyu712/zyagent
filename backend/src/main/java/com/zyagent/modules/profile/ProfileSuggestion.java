package com.zyagent.modules.profile;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 待审核的画像更新建议。
 *
 * <p>模型/规则只提出建议，不静默修改画像；用户确认后才写入，并可拒绝或更正。
 * {@code previousLevel} 保存旧值，便于前端展示"旧值 → 建议值"。
 */
public record ProfileSuggestion(
    String id,
    String ownerId,
    String skillKey,
    SkillLevel suggestedLevel,
    SkillLevel previousLevel,
    String evidence,
    String rationale,
    String sourceType,
    String sourceId,
    String model,
    ProfileSuggestionState state,
    String note,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ProfileSuggestion pending(String ownerId, String skillKey, SkillLevel level, SkillLevel previousLevel,
                                            String evidence, String rationale, String sourceType, String sourceId,
                                            String model) {
        LocalDateTime now = LocalDateTime.now();
        return new ProfileSuggestion(UUID.randomUUID().toString(), ownerId, skillKey, level, previousLevel,
            evidence, rationale, sourceType, sourceId, model, ProfileSuggestionState.PENDING, "", now, now);
    }

    public boolean isReviewable() {
        return state == ProfileSuggestionState.PENDING || state == ProfileSuggestionState.EDITED;
    }

    public ProfileSuggestion edited(SkillLevel level, String editedEvidence) {
        return copy(ProfileSuggestionState.EDITED, level, editedEvidence, note);
    }

    public ProfileSuggestion approved() {
        return copy(ProfileSuggestionState.APPROVED, suggestedLevel, evidence, note);
    }

    public ProfileSuggestion rejected(String reason) {
        return copy(ProfileSuggestionState.REJECTED, suggestedLevel, evidence, reason == null ? "" : reason);
    }

    private ProfileSuggestion copy(ProfileSuggestionState nextState, SkillLevel level, String nextEvidence, String nextNote) {
        return new ProfileSuggestion(id, ownerId, skillKey, level, previousLevel, nextEvidence, rationale,
            sourceType, sourceId, model, nextState, nextNote, createdAt, LocalDateTime.now());
    }
}
