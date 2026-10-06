package com.zyagent.infrastructure.storage;

import com.zyagent.modules.agent.task.AgentTaskStep;
import com.zyagent.modules.agent.task.AgentTaskStepRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcAgentTaskStepRepository implements AgentTaskStepRepositoryPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentTaskStepRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(AgentTaskStep step) {
        jdbcTemplate.update("""
            INSERT INTO agent_task_step(id, task_id, step_no, title, tool_name, state, input_summary,
                                        output_summary, error_message, attempt, duration_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE state = VALUES(state), output_summary = VALUES(output_summary),
                error_message = VALUES(error_message), attempt = VALUES(attempt), duration_ms = VALUES(duration_ms)
            """, step.id(), step.taskId(), step.stepNo(), step.title(), step.toolName(), step.status(),
            step.inputSummary(), step.outputSummary(), step.errorMessage(), step.attempt(), step.durationMs(),
            Timestamp.valueOf(step.createdAt()));
    }

    @Override
    public List<AgentTaskStep> findByTaskId(String taskId) {
        return jdbcTemplate.query("""
            SELECT id, task_id, step_no, title, tool_name, state, input_summary, output_summary,
                   error_message, attempt, duration_ms, created_at
            FROM agent_task_step WHERE task_id = ? ORDER BY step_no ASC
            """, (rs, rowNum) -> new AgentTaskStep(rs.getString("id"), rs.getString("task_id"),
            rs.getInt("step_no"), rs.getString("title"), rs.getString("tool_name"), rs.getString("state"),
            rs.getString("input_summary"), rs.getString("output_summary"), rs.getString("error_message"),
            rs.getInt("attempt"), rs.getLong("duration_ms"), rs.getTimestamp("created_at").toLocalDateTime()), taskId);
    }
}
