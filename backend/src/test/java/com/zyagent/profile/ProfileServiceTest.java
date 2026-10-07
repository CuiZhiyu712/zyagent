package com.zyagent.profile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileServiceTest {
    private static final String OWNER = "local-user";

    @Test
    void approvalPromotesSuggestionToConfirmedSkill() {
        FakeRepository repository = new FakeRepository();
        ProfileService service = new ProfileService(repository);

        ProfileSuggestion suggestion = service.suggest(OWNER, "Redis", SkillLevel.WORKING, "面试回答 turn-1", "INTERVIEW");
        UserSkill skill = service.approve(suggestion.id());

        assertEquals(SkillLevel.WORKING, skill.level());
        assertEquals(1, skill.version());
        assertEquals(ProfileSuggestionState.APPROVED, repository.suggestions.get(suggestion.id()).state());
        assertEquals(1, repository.history.size(), "audit history written");
        assertEquals(1, repository.evidence.size(), "evidence written");
    }

    @Test
    void approvalCapturesPreviousLevelForExistingSkill() {
        FakeRepository repository = new FakeRepository();
        ProfileService service = new ProfileService(repository);
        service.upsertSkill(OWNER, "Redis", SkillLevel.BASIC, null, "手动记录", null);

        ProfileSuggestion suggestion = service.suggest(OWNER, "Redis", SkillLevel.PROFICIENT, "项目证据", "RESUME");
        assertEquals(SkillLevel.BASIC, suggestion.previousLevel(), "previous level captured");

        UserSkill skill = service.approve(suggestion.id());
        assertEquals(SkillLevel.PROFICIENT, skill.level());
        assertEquals(2, skill.version(), "version incremented");
    }

    @Test
    void rejectDoesNotWriteSkill() {
        FakeRepository repository = new FakeRepository();
        ProfileService service = new ProfileService(repository);
        ProfileSuggestion suggestion = service.suggest(OWNER, "JVM", SkillLevel.WORKING, "证据", "INTERVIEW");

        ProfileSuggestion rejected = service.reject(suggestion.id(), "证据不足");

        assertEquals(ProfileSuggestionState.REJECTED, rejected.state());
        assertEquals("证据不足", rejected.note());
        assertTrue(repository.skills.isEmpty(), "no skill written on reject");
    }

    @Test
    void cannotApproveTwice() {
        FakeRepository repository = new FakeRepository();
        ProfileService service = new ProfileService(repository);
        ProfileSuggestion suggestion = service.suggest(OWNER, "MySQL", SkillLevel.WORKING, "证据", "INTERVIEW");
        service.approve(suggestion.id());

        assertThrows(IllegalStateException.class, () -> service.approve(suggestion.id()));
    }

    @Test
    void editedSuggestionUsesCorrectedLevelOnApproval() {
        FakeRepository repository = new FakeRepository();
        ProfileService service = new ProfileService(repository);
        ProfileSuggestion suggestion = service.suggest(OWNER, "并发", SkillLevel.WORKING, "证据", "INTERVIEW");

        ProfileSuggestion edited = service.editSuggestion(suggestion.id(), SkillLevel.PROFICIENT, "用户更正后的证据");
        assertEquals(ProfileSuggestionState.EDITED, edited.state());

        UserSkill skill = service.approve(suggestion.id());
        assertEquals(SkillLevel.PROFICIENT, skill.level());
        assertEquals("用户更正后的证据", skill.evidence());
    }

    @Test
    void rejectsStaleVersionOnManualUpdate() {
        FakeRepository repository = new FakeRepository();
        ProfileService service = new ProfileService(repository);
        UserSkill first = service.upsertSkill(OWNER, "Spring", SkillLevel.BASIC, null, "e1", null);

        assertThrows(ProfileVersionConflictException.class,
            () -> service.upsertSkill(OWNER, "Spring", SkillLevel.WORKING, null, "e2", first.version() - 1));
    }

    @Test
    void doesNotDuplicateIdenticalPendingSuggestion() {
        FakeRepository repository = new FakeRepository();
        ProfileService service = new ProfileService(repository);

        ProfileSuggestion first = service.suggest(OWNER, "Redis", SkillLevel.WORKING, "证据", "INTERVIEW", "s#1");
        ProfileSuggestion second = service.suggest(OWNER, "Redis", SkillLevel.WORKING, "证据", "INTERVIEW", "s#1");

        assertEquals(first.id(), second.id(), "duplicate pending suggestion reused");
    }

    @Test
    void deleteWritesHistoryEntry() {
        FakeRepository repository = new FakeRepository();
        ProfileService service = new ProfileService(repository);
        service.upsertSkill(OWNER, "Redis", SkillLevel.BASIC, null, "e", null);

        service.deleteSkill(OWNER, "Redis");

        assertTrue(repository.skills.isEmpty());
        assertEquals(SkillChangeType.DELETED, repository.history.get(repository.history.size() - 1).changeType());
    }

    private static final class FakeRepository implements ProfileRepositoryPort {
        private final Map<String, UserSkill> skills = new LinkedHashMap<>();
        private final Map<String, ProfileSuggestion> suggestions = new LinkedHashMap<>();
        private final List<SkillEvidence> evidence = new ArrayList<>();
        private final List<SkillHistoryEntry> history = new ArrayList<>();

        @Override
        public void saveSuggestion(ProfileSuggestion suggestion) {
            suggestions.put(suggestion.id(), suggestion);
        }

        @Override
        public Optional<ProfileSuggestion> findSuggestion(String id) {
            return Optional.ofNullable(suggestions.get(id));
        }

        @Override
        public void saveSkill(UserSkill skill) {
            skills.put(skill.skillKey(), skill);
        }

        @Override
        public Optional<UserSkill> findSkill(String ownerId, String skillKey) {
            return Optional.ofNullable(skills.get(skillKey));
        }

        @Override
        public void deleteSkill(String ownerId, String skillKey) {
            skills.remove(skillKey);
        }

        @Override
        public void saveEvidence(SkillEvidence value) {
            evidence.add(value);
        }

        @Override
        public void saveHistory(SkillHistoryEntry entry) {
            history.add(entry);
        }

        @Override
        public List<UserSkill> findSkills(String ownerId) {
            return new ArrayList<>(skills.values());
        }

        @Override
        public List<ProfileSuggestion> findSuggestions(String ownerId, ProfileSuggestionState state) {
            return suggestions.values().stream().filter(s -> s.state() == state).toList();
        }
    }
}
