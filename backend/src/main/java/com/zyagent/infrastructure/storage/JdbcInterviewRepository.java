package com.zyagent.infrastructure.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.modules.interview.InterviewEvaluation;
import com.zyagent.modules.interview.InterviewRepositoryPort;
import com.zyagent.modules.interview.InterviewSession;
import com.zyagent.modules.interview.InterviewSessionState;
import com.zyagent.modules.interview.InterviewTurn;
import com.zyagent.modules.interview.InterviewTurnState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcInterviewRepository implements InterviewRepositoryPort {
    private static final String SESSION_COLUMNS = """
        id, owner_id, job_id, jd_snapshot, interview_type, difficulty, state, current_turn, max_turns,
        max_follow_ups, summary, created_at, updated_at
        """;
    private static final String TURN_COLUMNS = """
        id, session_id, turn_no, state, question, answer, evaluation, next_question, request_id,
        created_at, updated_at
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcInterviewRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveSession(InterviewSession session) {
        jdbcTemplate.update("""
            INSERT INTO interview_session(id, owner_id, job_id, jd_snapshot, interview_type, difficulty, state,
                                          current_turn, max_turns, max_follow_ups, summary, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE state = VALUES(state), current_turn = VALUES(current_turn),
                max_follow_ups = VALUES(max_follow_ups), summary = VALUES(summary), updated_at = VALUES(updated_at)
            """, session.id(), session.ownerId(), session.jobId(), session.jdSnapshot(), session.interviewType(),
            session.difficulty(), session.state().name(), session.currentTurn(), session.maxTurns(),
            session.maxFollowUps(), session.summary(), timestamp(session.createdAt()), timestamp(session.updatedAt()));
    }

    @Override
    public Optional<InterviewSession> findSession(String id) {
        return jdbcTemplate.query("""
            SELECT %s FROM interview_session WHERE id = ? LIMIT 1
            """.formatted(SESSION_COLUMNS), JdbcInterviewRepository::mapSession, id).stream().findFirst();
    }

    @Override
    public void saveTurn(InterviewTurn turn) {
        jdbcTemplate.update("""
            INSERT INTO interview_turn(id, session_id, turn_no, state, question, answer, evaluation,
                                       next_question, request_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE state = VALUES(state), answer = VALUES(answer), evaluation = VALUES(evaluation),
                next_question = VALUES(next_question), request_id = VALUES(request_id), updated_at = VALUES(updated_at)
            """, turn.id(), turn.sessionId(), turn.turnNo(), turn.state().name(), turn.question(), turn.answer(),
            writeEvaluation(turn.evaluation()), turn.followUpQuestion(), turn.requestId(),
            timestamp(turn.createdAt()), timestamp(turn.updatedAt()));
    }

    @Override
    public Optional<InterviewTurn> findTurn(String sessionId, int turnNo) {
        return jdbcTemplate.query("""
            SELECT %s FROM interview_turn WHERE session_id = ? AND turn_no = ? LIMIT 1
            """.formatted(TURN_COLUMNS), this::mapTurn, sessionId, turnNo).stream().findFirst();
    }

    @Override
    public Optional<InterviewTurn> findTurnByRequestId(String sessionId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
            SELECT %s FROM interview_turn WHERE session_id = ? AND request_id = ? LIMIT 1
            """.formatted(TURN_COLUMNS), this::mapTurn, sessionId, requestId).stream().findFirst();
    }

    @Override
    public List<InterviewTurn> findTurns(String sessionId) {
        return jdbcTemplate.query("""
            SELECT %s FROM interview_turn WHERE session_id = ? ORDER BY turn_no ASC
            """.formatted(TURN_COLUMNS), this::mapTurn, sessionId);
    }

    @Override
    public List<InterviewSession> findSessions(String ownerId, int offset, int limit) {
        List<Object> args = new ArrayList<>();
        String where = ownerFilter(ownerId, args);
        args.add(Math.max(1, limit));
        args.add(Math.max(0, offset));
        return jdbcTemplate.query("""
            SELECT %s FROM interview_session %s ORDER BY updated_at DESC LIMIT ? OFFSET ?
            """.formatted(SESSION_COLUMNS, where), JdbcInterviewRepository::mapSession, args.toArray());
    }

    @Override
    public long countSessions(String ownerId) {
        List<Object> args = new ArrayList<>();
        String where = ownerFilter(ownerId, args);
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM interview_session " + where, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    /** 归属过滤；兼容历史数据中 owner_id 为 NULL 的会话。 */
    private String ownerFilter(String ownerId, List<Object> args) {
        if (ownerId == null || ownerId.isBlank()) {
            return "";
        }
        args.add(ownerId);
        return "WHERE (owner_id = ? OR owner_id IS NULL)";
    }

    private static InterviewSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        return new InterviewSession(
            rs.getString("id"), rs.getString("owner_id"), rs.getString("job_id"), rs.getString("jd_snapshot"),
            rs.getString("interview_type"), rs.getString("difficulty"),
            InterviewSessionState.valueOf(rs.getString("state")), rs.getInt("current_turn"), rs.getInt("max_turns"),
            rs.getInt("max_follow_ups"), rs.getString("summary"),
            rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime());
    }

    private InterviewTurn mapTurn(ResultSet rs, int rowNum) throws SQLException {
        String state = rs.getString("state");
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new InterviewTurn(
            rs.getString("id"), rs.getString("session_id"), rs.getInt("turn_no"),
            state == null ? InterviewTurnState.NEXT_QUESTION_READY : InterviewTurnState.valueOf(state),
            rs.getString("question"), rs.getString("answer"), readEvaluation(rs.getString("evaluation")),
            rs.getString("next_question"), rs.getString("request_id"),
            createdAt.toLocalDateTime(),
            updatedAt == null ? createdAt.toLocalDateTime() : updatedAt.toLocalDateTime());
    }

    private String writeEvaluation(InterviewEvaluation evaluation) {
        if (evaluation == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(evaluation);
        } catch (Exception ex) {
            return null;
        }
    }

    private InterviewEvaluation readEvaluation(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InterviewEvaluation.class);
        } catch (Exception ex) {
            return InterviewEvaluation.fallback("历史评价无法解析，视为不可用");
        }
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
