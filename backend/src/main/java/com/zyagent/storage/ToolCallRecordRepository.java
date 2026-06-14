package com.zyagent.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.tool.ToolResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ToolCallRecordRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ToolCallRecordRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(String sessionId, String skillId, ToolResult result) {
        jdbcTemplate.update("""
            INSERT INTO tool_call_record(id, session_id, tool_name, input_json, output_summary, success, error_message, called_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID().toString(),
            sessionId,
            result.toolName(),
            "{\"skill\":\"" + escape(skillId) + "\"}",
            summarize(result.output()),
            result.success(),
            result.errorMessage(),
            Timestamp.valueOf(LocalDateTime.now())
        );
    }

    public void saveAll(String sessionId, String skillId, List<ToolResult> results) {
        if (results == null) {
            return;
        }
        for (ToolResult result : results) {
            save(sessionId, skillId, result);
        }
    }

    private String summarize(Object value) {
        String json = json(value);
        return json.length() > 1800 ? json.substring(0, 1800) : json;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
