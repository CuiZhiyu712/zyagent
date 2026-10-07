package com.zyagent.storage;

import com.zyagent.agent.AgentMode;
import com.zyagent.agent.task.AgentTask;
import com.zyagent.agent.task.AgentTaskRepositoryPort;
import com.zyagent.agent.task.AgentTaskState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAgentTaskRepository implements AgentTaskRepositoryPort {
    private static final String COLUMNS = """
        id, owner_id, session_id, agent_mode, state, request_text, result_text, error_code,
        error_message, created_at, started_at, finished_at, updated_at, idempotency_key
        """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 先按主键更新，未命中再插入。
     *
     * <p>刻意不用 {@code ON DUPLICATE KEY UPDATE}：那会在幂等键冲突时**改写已存在的行**，
     * 把另一个任务的记录悄悄覆盖掉。这里让插入路径上的幂等键冲突直接报错，
     * 由 {@link com.zyagent.agent.task.AgentTaskService} 决定如何处置。
     */
    @Override
    public void save(AgentTask task) {
        int updated = jdbcTemplate.update("""
            UPDATE agent_task SET state = ?, request_text = ?, result_text = ?, error_code = ?, error_message = ?,
                started_at = ?, finished_at = ?, updated_at = ?
            WHERE id = ?
            """,
            task.state().name(), task.requestText(), task.resultText(), task.errorCode(), task.errorMessage(),
            timestamp(task.startedAt()), timestamp(task.finishedAt()), timestamp(task.updatedAt()), task.id());
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
            INSERT INTO agent_task(id, owner_id, session_id, agent_mode, state, request_text, result_text,
                                   error_code, error_message, created_at, started_at, finished_at, updated_at, idempotency_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            task.id(), task.ownerId(), task.sessionId(), task.mode().name(), task.state().name(), task.requestText(),
            task.resultText(), task.errorCode(), task.errorMessage(), timestamp(task.createdAt()), timestamp(task.startedAt()),
            timestamp(task.finishedAt()), timestamp(task.updatedAt()), task.idempotencyKey());
    }

    @Override
    public Optional<AgentTask> findById(String ownerId, String taskId) {
        return query("WHERE id = ? AND owner_id = ? LIMIT 1", taskId, ownerId).stream().findFirst();
    }

    @Override
    public Optional<AgentTask> findByIdempotencyKey(String ownerId, String idempotencyKey) {
        if (ownerId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return query("WHERE owner_id = ? AND idempotency_key = ? LIMIT 1", ownerId, idempotencyKey).stream().findFirst();
    }

    @Override
    public void markRunningTasksInterrupted() {
        jdbcTemplate.update("""
            UPDATE agent_task SET state = 'INTERRUPTED', error_code = 'PROCESS_INTERRUPTED',
                error_message = '应用重启时任务仍处于运行中', finished_at = ?, updated_at = ?
            WHERE state = 'RUNNING'
            """, Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()));
    }

    private List<AgentTask> query(String whereClause, Object... args) {
        return jdbcTemplate.query("""
            SELECT %s FROM agent_task %s
            """.formatted(COLUMNS, whereClause), (rs, rowNum) -> new AgentTask(
            rs.getString("id"), rs.getString("owner_id"), rs.getString("session_id"),
            AgentMode.valueOf(rs.getString("agent_mode")), AgentTaskState.valueOf(rs.getString("state")),
            rs.getString("request_text"), rs.getString("result_text"), rs.getString("error_code"),
            rs.getString("error_message"), date(rs.getTimestamp("created_at")), date(rs.getTimestamp("started_at")),
            date(rs.getTimestamp("finished_at")), date(rs.getTimestamp("updated_at")), rs.getString("idempotency_key")
        ), args);
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime date(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
