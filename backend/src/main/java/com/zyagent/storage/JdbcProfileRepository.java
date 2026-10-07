package com.zyagent.storage;

import com.zyagent.profile.ProfileRepositoryPort;
import com.zyagent.profile.ProfileSuggestion;
import com.zyagent.profile.ProfileSuggestionState;
import com.zyagent.profile.SkillChangeType;
import com.zyagent.profile.SkillEvidence;
import com.zyagent.profile.SkillHistoryEntry;
import com.zyagent.profile.SkillLevel;
import com.zyagent.profile.UserSkill;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcProfileRepository implements ProfileRepositoryPort {
    private static final String SKILL_COLUMNS = "owner_id, skill_key, level, confidence, evidence, source_type, source_id, version, updated_at";
    private static final String SUGGESTION_COLUMNS = """
        id, owner_id, skill_key, suggested_level, previous_level, evidence, rationale, source_type, source_id,
        model, state, note, created_at, updated_at
        """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveSuggestion(ProfileSuggestion suggestion) {
        jdbcTemplate.update("""
            INSERT INTO profile_suggestion(id, owner_id, skill_key, suggested_level, previous_level, evidence,
                                           rationale, source_type, source_id, model, state, note, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE suggested_level = VALUES(suggested_level), evidence = VALUES(evidence),
                state = VALUES(state), note = VALUES(note), updated_at = VALUES(updated_at)
            """, suggestion.id(), suggestion.ownerId(), suggestion.skillKey(),
            suggestion.suggestedLevel().name(),
            suggestion.previousLevel() == null ? null : suggestion.previousLevel().name(),
            suggestion.evidence(), suggestion.rationale(), suggestion.sourceType(), suggestion.sourceId(),
            suggestion.model(), suggestion.state().name(), suggestion.note(),
            timestamp(suggestion.createdAt()), timestamp(suggestion.updatedAt()));
    }

    @Override
    public Optional<ProfileSuggestion> findSuggestion(String id) {
        return jdbcTemplate.query("""
            SELECT %s FROM profile_suggestion WHERE id = ? LIMIT 1
            """.formatted(SUGGESTION_COLUMNS), JdbcProfileRepository::mapSuggestion, id).stream().findFirst();
    }

    @Override
    public void saveSkill(UserSkill skill) {
        jdbcTemplate.update("""
            INSERT INTO user_skill(owner_id, skill_key, level, confidence, evidence, source_type, source_id, version, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE level = VALUES(level), confidence = VALUES(confidence), evidence = VALUES(evidence),
                source_type = VALUES(source_type), source_id = VALUES(source_id), version = VALUES(version),
                updated_at = VALUES(updated_at)
            """, skill.ownerId(), skill.skillKey(), skill.level().name(), skill.confidence(), skill.evidence(),
            skill.sourceType(), skill.sourceId(), skill.version(), timestamp(skill.updatedAt()));
    }

    @Override
    public Optional<UserSkill> findSkill(String ownerId, String skillKey) {
        return jdbcTemplate.query("""
            SELECT %s FROM user_skill WHERE owner_id = ? AND skill_key = ? LIMIT 1
            """.formatted(SKILL_COLUMNS), JdbcProfileRepository::mapSkill, ownerId, skillKey).stream().findFirst();
    }

    @Override
    public void deleteSkill(String ownerId, String skillKey) {
        jdbcTemplate.update("DELETE FROM user_skill WHERE owner_id = ? AND skill_key = ?", ownerId, skillKey);
    }

    @Override
    public void saveEvidence(SkillEvidence evidence) {
        jdbcTemplate.update("""
            INSERT INTO profile_skill_evidence(id, owner_id, skill_key, summary, source_type, source_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, evidence.id(), evidence.ownerId(), evidence.skillKey(), evidence.summary(),
            evidence.sourceType(), evidence.sourceId(), timestamp(evidence.createdAt()));
    }

    @Override
    public void saveHistory(SkillHistoryEntry entry) {
        jdbcTemplate.update("""
            INSERT INTO profile_skill_history(id, owner_id, skill_key, old_level, new_level, old_confidence,
                                              new_confidence, change_type, source_type, source_id, note, changed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, entry.id(), entry.ownerId(), entry.skillKey(),
            entry.oldLevel() == null ? null : entry.oldLevel().name(),
            entry.newLevel() == null ? null : entry.newLevel().name(),
            entry.oldConfidence(), entry.newConfidence(), entry.changeType().name(),
            entry.sourceType(), entry.sourceId(), entry.note(), timestamp(entry.changedAt()));
    }

    @Override
    public List<UserSkill> findSkills(String ownerId) {
        return jdbcTemplate.query("""
            SELECT %s FROM user_skill WHERE owner_id = ? ORDER BY updated_at DESC
            """.formatted(SKILL_COLUMNS), JdbcProfileRepository::mapSkill, ownerId);
    }

    @Override
    public List<ProfileSuggestion> findSuggestions(String ownerId) {
        return jdbcTemplate.query("""
            SELECT %s FROM profile_suggestion WHERE owner_id = ? ORDER BY created_at DESC
            """.formatted(SUGGESTION_COLUMNS), JdbcProfileRepository::mapSuggestion, ownerId);
    }

    @Override
    public List<ProfileSuggestion> findSuggestions(String ownerId, ProfileSuggestionState state) {
        return jdbcTemplate.query("""
            SELECT %s FROM profile_suggestion WHERE owner_id = ? AND state = ? ORDER BY created_at DESC
            """.formatted(SUGGESTION_COLUMNS), JdbcProfileRepository::mapSuggestion, ownerId, state.name());
    }

    @Override
    public List<SkillEvidence> findEvidence(String ownerId, String skillKey) {
        return jdbcTemplate.query("""
            SELECT id, owner_id, skill_key, summary, source_type, source_id, created_at
            FROM profile_skill_evidence WHERE owner_id = ? AND skill_key = ? ORDER BY created_at DESC
            """, (rs, n) -> new SkillEvidence(rs.getString("id"), rs.getString("owner_id"), rs.getString("skill_key"),
            rs.getString("summary"), rs.getString("source_type"), rs.getString("source_id"),
            timestampOf(rs, "created_at")), ownerId, skillKey);
    }

    @Override
    public List<SkillHistoryEntry> findHistory(String ownerId, String skillKey) {
        return jdbcTemplate.query("""
            SELECT id, owner_id, skill_key, old_level, new_level, old_confidence, new_confidence,
                   change_type, source_type, source_id, note, changed_at
            FROM profile_skill_history WHERE owner_id = ? AND skill_key = ? ORDER BY changed_at DESC
            """, (rs, n) -> new SkillHistoryEntry(rs.getString("id"), rs.getString("owner_id"), rs.getString("skill_key"),
            levelOf(rs.getString("old_level")), levelOf(rs.getString("new_level")),
            rs.getDouble("old_confidence"), rs.getDouble("new_confidence"),
            SkillChangeType.valueOf(rs.getString("change_type")), rs.getString("source_type"),
            rs.getString("source_id"), rs.getString("note"), timestampOf(rs, "changed_at")), ownerId, skillKey);
    }

    private static UserSkill mapSkill(ResultSet rs, int rowNum) throws SQLException {
        return new UserSkill(rs.getString("owner_id"), rs.getString("skill_key"),
            SkillLevel.valueOf(rs.getString("level")), rs.getDouble("confidence"), rs.getString("evidence"),
            rs.getString("source_type"), rs.getString("source_id"), rs.getInt("version"),
            timestampOf(rs, "updated_at"));
    }

    private static ProfileSuggestion mapSuggestion(ResultSet rs, int rowNum) throws SQLException {
        return new ProfileSuggestion(rs.getString("id"), rs.getString("owner_id"), rs.getString("skill_key"),
            SkillLevel.valueOf(rs.getString("suggested_level")), levelOf(rs.getString("previous_level")),
            rs.getString("evidence"), rs.getString("rationale"), rs.getString("source_type"),
            rs.getString("source_id"), rs.getString("model"),
            ProfileSuggestionState.valueOf(rs.getString("state")), rs.getString("note"),
            timestampOf(rs, "created_at"), timestampOf(rs, "updated_at"));
    }

    private static SkillLevel levelOf(String value) {
        return value == null || value.isBlank() ? null : SkillLevel.valueOf(value);
    }

    private static LocalDateTime timestampOf(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
