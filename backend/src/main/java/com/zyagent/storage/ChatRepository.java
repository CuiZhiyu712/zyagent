package com.zyagent.storage;

import com.zyagent.agent.AgentMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ChatRepository {
    private final JdbcTemplate jdbcTemplate;

    public ChatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatSessionView createSession(String requestedId, String title, AgentMode mode) {
        String id = requestedId == null || requestedId.isBlank() ? UUID.randomUUID().toString() : requestedId;
        LocalDateTime now = LocalDateTime.now();
        String agentMode = mode == null ? "AUTO" : mode.name();
        jdbcTemplate.update("""
            INSERT INTO chat_session(id, title, agent_mode, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)
            """, id, normalizeTitle(title), agentMode, Timestamp.valueOf(now), Timestamp.valueOf(now));
        return new ChatSessionView(id, normalizeTitle(title), agentMode, 0, now, now);
    }

    public void ensureSession(String id, String title, AgentMode mode) {
        createSession(id, title, mode);
    }

    public void touchSession(String id, String title, AgentMode mode) {
        jdbcTemplate.update("""
            UPDATE chat_session
            SET title = CASE WHEN title LIKE '新对话%' OR title = '默认对话' THEN ? ELSE title END,
                agent_mode = ?,
                updated_at = ?
            WHERE id = ?
            """, normalizeTitle(title), mode == null ? "AUTO" : mode.name(), Timestamp.valueOf(LocalDateTime.now()), id);
    }

    public List<ChatSessionView> listSessions() {
        return jdbcTemplate.query("""
            SELECT s.id, s.title, s.agent_mode, s.created_at, s.updated_at, COUNT(m.id) AS message_count
            FROM chat_session s
            LEFT JOIN chat_message m ON m.session_id = s.id
            GROUP BY s.id, s.title, s.agent_mode, s.created_at, s.updated_at
            ORDER BY s.updated_at DESC
            """, (rs, rowNum) -> new ChatSessionView(
            rs.getString("id"),
            rs.getString("title"),
            rs.getString("agent_mode"),
            rs.getInt("message_count"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        ));
    }

    public List<ChatMessageView> listMessages(String sessionId) {
        return jdbcTemplate.query("""
            SELECT id, session_id, role, content, references_json, created_at
            FROM chat_message
            WHERE session_id = ?
            ORDER BY created_at ASC
            """, (rs, rowNum) -> new ChatMessageView(
            rs.getString("id"),
            rs.getString("session_id"),
            rs.getString("role"),
            rs.getString("content"),
            rs.getString("references_json"),
            rs.getTimestamp("created_at").toLocalDateTime()
        ), sessionId);
    }

    public void saveMessage(String sessionId, String role, String content, String referencesJson) {
        jdbcTemplate.update("""
            INSERT INTO chat_message(id, session_id, role, content, references_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID().toString(), sessionId, role, content == null ? "" : content, referencesJson, Timestamp.valueOf(LocalDateTime.now()));
        jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now()), sessionId);
    }

    public int deleteSession(String sessionId) {
        jdbcTemplate.update("DELETE FROM tool_call_record WHERE session_id = ?", sessionId);
        int messages = jdbcTemplate.update("DELETE FROM chat_message WHERE session_id = ?", sessionId);
        int sessions = jdbcTemplate.update("DELETE FROM chat_session WHERE id = ?", sessionId);
        return sessions;
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "新对话";
        }
        return title.length() > 40 ? title.substring(0, 40) : title;
    }
}
