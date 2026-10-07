package com.zyagent.profile;

import com.zyagent.common.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户技能画像闭环。
 *
 * <p>模型/规则只产生**待审核建议**，用户确认后才写入画像；每次写入都记录证据与版本历史，
 * 并校验归属、级别合法性与版本冲突。
 */
@Service
public class ProfileService {
    private static final String DEFAULT_OWNER = "local-user";

    private final ProfileRepositoryPort repository;
    private final CurrentUserProvider currentUserProvider;

    public ProfileService(ProfileRepositoryPort repository) {
        this(repository, null);
    }

    @Autowired
    public ProfileService(ProfileRepositoryPort repository, CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
    }

    public ProfileSuggestion suggest(String ownerId, String skillKey, SkillLevel level,
                                     String evidence, String sourceType) {
        return suggest(ownerId, skillKey, level, evidence, sourceType, null);
    }

    public ProfileSuggestion suggest(String ownerId, String skillKey, SkillLevel level,
                                     String evidence, String sourceType, String sourceId) {
        return suggest(ownerId, skillKey, level, evidence, sourceType, sourceId, defaultModelFor(sourceType));
    }

    /** 创建建议；同一 skillKey + 建议级别 + 来源 ID 的待审核建议不会重复创建。 */
    public ProfileSuggestion suggest(String ownerId, String skillKey, SkillLevel level, String evidence,
                                     String sourceType, String sourceId, String model) {
        String owner = requireOwner(ownerId);
        String key = requireSkillKey(skillKey);
        if (level == null) {
            throw new IllegalArgumentException("建议级别不能为空");
        }
        for (ProfileSuggestion existing : repository.findSuggestions(owner, ProfileSuggestionState.PENDING)) {
            if (existing.skillKey().equals(key)
                && existing.suggestedLevel() == level
                && Objects.equals(existing.sourceId(), sourceId)) {
                return existing;
            }
        }
        SkillLevel previousLevel = repository.findSkill(owner, key).map(UserSkill::level).orElse(null);
        ProfileSuggestion suggestion = ProfileSuggestion.pending(owner, key, level, previousLevel,
            evidence == null ? "" : evidence, evidence == null ? "" : evidence, sourceType, sourceId, model);
        repository.saveSuggestion(suggestion);
        return suggestion;
    }

    public UserSkill approve(String suggestionId) {
        ProfileSuggestion suggestion = requireSuggestion(suggestionId);
        if (!suggestion.isReviewable()) {
            throw new IllegalStateException("建议已处理，无法重复确认：" + suggestion.state());
        }
        UserSkill before = repository.findSkill(suggestion.ownerId(), suggestion.skillKey()).orElse(null);
        UserSkill skill = before == null
            ? UserSkill.of(suggestion.ownerId(), suggestion.skillKey(), suggestion.suggestedLevel(),
                defaultConfidence(suggestion.sourceType()), suggestion.evidence(), suggestion.sourceType(), suggestion.sourceId())
            : before.nextVersion(suggestion.suggestedLevel(), defaultConfidence(suggestion.sourceType()),
                suggestion.evidence(), suggestion.sourceType(), suggestion.sourceId());
        record(skill, before, before == null ? SkillChangeType.CREATED : SkillChangeType.SUGGESTION_APPROVED,
            "确认建议 " + suggestion.id());
        repository.saveSuggestion(suggestion.approved());
        return skill;
    }

    public ProfileSuggestion reject(String suggestionId, String note) {
        ProfileSuggestion suggestion = requireSuggestion(suggestionId);
        if (!suggestion.isReviewable()) {
            throw new IllegalStateException("建议已处理，无法重复拒绝：" + suggestion.state());
        }
        ProfileSuggestion rejected = suggestion.rejected(note);
        repository.saveSuggestion(rejected);
        return rejected;
    }

    public ProfileSuggestion editSuggestion(String suggestionId, SkillLevel level, String evidence) {
        ProfileSuggestion suggestion = requireSuggestion(suggestionId);
        if (suggestion.state() != ProfileSuggestionState.PENDING) {
            throw new IllegalStateException("只有待审核建议可以更正：" + suggestion.state());
        }
        ProfileSuggestion edited = suggestion.edited(level == null ? suggestion.suggestedLevel() : level, evidence);
        repository.saveSuggestion(edited);
        return edited;
    }

    /** 用户手动新增/更正技能；传入 {@code expectedVersion} 时做乐观并发校验。 */
    public UserSkill upsertSkill(String ownerId, String skillKey, SkillLevel level, Double confidence,
                                 String evidence, Integer expectedVersion) {
        String owner = requireOwner(ownerId);
        String key = requireSkillKey(skillKey);
        if (level == null) {
            throw new IllegalArgumentException("技能级别不能为空");
        }
        UserSkill before = repository.findSkill(owner, key).orElse(null);
        if (expectedVersion != null && before != null && before.version() != expectedVersion) {
            throw new ProfileVersionConflictException(
                "画像已被更新（当前版本 " + before.version() + "，提交版本 " + expectedVersion + "），请刷新后重试");
        }
        double resolvedConfidence = confidence == null
            ? (before == null ? 1.0 : before.confidence())
            : clampConfidence(confidence);
        String resolvedEvidence = evidence == null || evidence.isBlank()
            ? (before == null ? "" : before.evidence())
            : evidence;
        UserSkill skill = before == null
            ? UserSkill.of(owner, key, level, resolvedConfidence, resolvedEvidence, "MANUAL", null)
            : before.nextVersion(level, resolvedConfidence, resolvedEvidence, "MANUAL", null);
        record(skill, before, before == null ? SkillChangeType.CREATED : SkillChangeType.UPDATED, "用户手动维护");
        return skill;
    }

    public void deleteSkill(String ownerId, String skillKey) {
        String owner = requireOwner(ownerId);
        UserSkill before = repository.findSkill(owner, skillKey)
            .orElseThrow(() -> new IllegalArgumentException("技能不存在：" + skillKey));
        repository.deleteSkill(owner, skillKey);
        repository.saveHistory(SkillHistoryEntry.of(owner, skillKey, before, null, SkillChangeType.DELETED, "用户删除技能"));
    }

    public List<UserSkill> skills(String ownerId) {
        return repository.findSkills(requireOwner(ownerId));
    }

    public List<ProfileSuggestion> suggestions(String ownerId) {
        return repository.findSuggestions(requireOwner(ownerId));
    }

    public List<ProfileSuggestion> suggestions(String ownerId, ProfileSuggestionState state) {
        String owner = requireOwner(ownerId);
        return state == null ? repository.findSuggestions(owner) : repository.findSuggestions(owner, state);
    }

    public List<SkillEvidence> evidence(String ownerId, String skillKey) {
        return repository.findEvidence(requireOwner(ownerId), skillKey);
    }

    public List<SkillHistoryEntry> history(String ownerId, String skillKey) {
        return repository.findHistory(requireOwner(ownerId), skillKey);
    }

    public Optional<UserSkill> findSkill(String ownerId, String skillKey) {
        return repository.findSkill(requireOwner(ownerId), skillKey);
    }

    /** 当前用户（无认证时为配置的默认 owner）。 */
    public String currentOwnerId() {
        String owner = currentUserProvider == null ? null : currentUserProvider.currentUserId();
        return owner == null || owner.isBlank() ? DEFAULT_OWNER : owner;
    }

    private void record(UserSkill skill, UserSkill before, SkillChangeType changeType, String note) {
        repository.saveSkill(skill);
        repository.saveEvidence(SkillEvidence.of(skill.ownerId(), skill.skillKey(), skill.evidence(),
            skill.sourceType(), skill.sourceId()));
        repository.saveHistory(SkillHistoryEntry.of(skill.ownerId(), skill.skillKey(), before, skill, changeType, note));
    }

    private ProfileSuggestion requireSuggestion(String suggestionId) {
        return repository.findSuggestion(suggestionId)
            .orElseThrow(() -> new IllegalArgumentException("画像建议不存在：" + suggestionId));
    }

    private String requireOwner(String ownerId) {
        return ownerId == null || ownerId.isBlank() ? currentOwnerId() : ownerId;
    }

    private static String requireSkillKey(String skillKey) {
        if (skillKey == null || skillKey.isBlank()) {
            throw new IllegalArgumentException("技能名不能为空");
        }
        return skillKey.strip();
    }

    private static double clampConfidence(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** 系统置信度按来源区分，与用户熟练度分开记录。 */
    private static double defaultConfidence(String sourceType) {
        if (sourceType == null) {
            return 0.6;
        }
        return switch (sourceType.toUpperCase(Locale.ROOT)) {
            case "MANUAL" -> 1.0;
            case "RESUME" -> 0.7;
            case "INTERVIEW", "JOB_MATCH" -> 0.6;
            case "STUDY" -> 0.5;
            default -> 0.6;
        };
    }

    private static String defaultModelFor(String sourceType) {
        return "INTERVIEW".equalsIgnoreCase(sourceType) ? "rule-based-interviewer" : "manual";
    }
}
