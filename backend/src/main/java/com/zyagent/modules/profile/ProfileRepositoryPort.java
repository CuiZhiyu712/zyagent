package com.zyagent.modules.profile;

import java.util.List;
import java.util.Optional;

public interface ProfileRepositoryPort {
    void saveSuggestion(ProfileSuggestion suggestion);

    Optional<ProfileSuggestion> findSuggestion(String id);

    void saveSkill(UserSkill skill);

    default Optional<UserSkill> findSkill(String ownerId, String skillKey) {
        return Optional.empty();
    }

    default void deleteSkill(String ownerId, String skillKey) {
    }

    default void saveEvidence(SkillEvidence evidence) {
    }

    default void saveHistory(SkillHistoryEntry entry) {
    }

    default List<UserSkill> findSkills(String ownerId) {
        return List.of();
    }

    default List<ProfileSuggestion> findSuggestions(String ownerId) {
        return List.of();
    }

    default List<ProfileSuggestion> findSuggestions(String ownerId, ProfileSuggestionState state) {
        return List.of();
    }

    default List<SkillEvidence> findEvidence(String ownerId, String skillKey) {
        return List.of();
    }

    default List<SkillHistoryEntry> findHistory(String ownerId, String skillKey) {
        return List.of();
    }
}
