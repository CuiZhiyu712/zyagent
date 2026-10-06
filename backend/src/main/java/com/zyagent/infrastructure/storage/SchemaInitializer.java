package com.zyagent.infrastructure.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@Configuration
public class SchemaInitializer {
    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    @Bean
    ApplicationRunner zyagentSchemaRunner(DataSource dataSource) {
        return ignored -> {
            try {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
                populator.execute(dataSource);
                applyForwardCompatibleMigrations(new JdbcTemplate(dataSource));
                log.info("zyagent MySQL schema initialized.");
            } catch (RuntimeException ex) {
                log.warn("zyagent MySQL schema initialization skipped: {}", ex.getMessage());
            }
        };
    }

    /**
     * 对已经存在的表做向前兼容补列。{@code CREATE TABLE IF NOT EXISTS} 不会修改既有表，
     * 因此这里显式检查列是否存在再补，保证重复执行安全。
     */
    private void applyForwardCompatibleMigrations(JdbcTemplate jdbcTemplate) {
        addColumnIfMissing(jdbcTemplate, "agent_task", "idempotency_key",
            "ALTER TABLE agent_task ADD COLUMN idempotency_key VARCHAR(128) NULL");
        addIndexIfMissing(jdbcTemplate, "agent_task", "uk_agent_task_idem",
            "CREATE UNIQUE INDEX uk_agent_task_idem ON agent_task (idempotency_key)");
        addColumnIfMissing(jdbcTemplate, "interview_session", "owner_id",
            "ALTER TABLE interview_session ADD COLUMN owner_id VARCHAR(128) NULL");
        addColumnIfMissing(jdbcTemplate, "interview_session", "jd_snapshot",
            "ALTER TABLE interview_session ADD COLUMN jd_snapshot TEXT NULL");
        addColumnIfMissing(jdbcTemplate, "interview_session", "max_follow_ups",
            "ALTER TABLE interview_session ADD COLUMN max_follow_ups INT NOT NULL DEFAULT 1");
        addColumnIfMissing(jdbcTemplate, "interview_session", "summary",
            "ALTER TABLE interview_session ADD COLUMN summary TEXT NULL");
        addColumnIfMissing(jdbcTemplate, "interview_turn", "state",
            "ALTER TABLE interview_turn ADD COLUMN state VARCHAR(32) NOT NULL DEFAULT 'NEXT_QUESTION_READY'");
        addColumnIfMissing(jdbcTemplate, "interview_turn", "request_id",
            "ALTER TABLE interview_turn ADD COLUMN request_id VARCHAR(128) NULL");
        addColumnIfMissing(jdbcTemplate, "interview_turn", "updated_at",
            "ALTER TABLE interview_turn ADD COLUMN updated_at TIMESTAMP NULL");
        addColumnIfMissing(jdbcTemplate, "user_skill", "version",
            "ALTER TABLE user_skill ADD COLUMN version INT NOT NULL DEFAULT 1");
        addColumnIfMissing(jdbcTemplate, "profile_suggestion", "previous_level",
            "ALTER TABLE profile_suggestion ADD COLUMN previous_level VARCHAR(32) NULL");
        addColumnIfMissing(jdbcTemplate, "profile_suggestion", "rationale",
            "ALTER TABLE profile_suggestion ADD COLUMN rationale TEXT NULL");
        addColumnIfMissing(jdbcTemplate, "profile_suggestion", "model",
            "ALTER TABLE profile_suggestion ADD COLUMN model VARCHAR(64) NULL");
        addColumnIfMissing(jdbcTemplate, "profile_suggestion", "note",
            "ALTER TABLE profile_suggestion ADD COLUMN note TEXT NULL");
        addColumnIfMissing(jdbcTemplate, "profile_suggestion", "updated_at",
            "ALTER TABLE profile_suggestion ADD COLUMN updated_at TIMESTAMP NULL");
        addColumnIfMissing(jdbcTemplate, "chat_session", "task_type",
            "ALTER TABLE chat_session ADD COLUMN task_type VARCHAR(64) NULL");
        addColumnIfMissing(jdbcTemplate, "chat_session", "active_skill",
            "ALTER TABLE chat_session ADD COLUMN active_skill VARCHAR(64) NULL");
        addColumnIfMissing(jdbcTemplate, "chat_session", "current_day",
            "ALTER TABLE chat_session ADD COLUMN current_day INT NOT NULL DEFAULT 0");
    }

    private void addIndexIfMissing(JdbcTemplate jdbcTemplate, String table, String index, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.indexes
            WHERE LOWER(table_name) = LOWER(?) AND LOWER(index_name) = LOWER(?)
            """, Integer.class, table, index);
        if (count != null && count == 0) {
            jdbcTemplate.execute(ddl);
            log.info("zyagent schema migration: added index {} on {}", index, table);
        }
    }

    private void addColumnIfMissing(JdbcTemplate jdbcTemplate, String table, String column, String alterSql) {
        // 不绑定具体 schema，兼容 MySQL 与 H2（两者 information_schema 大小写规则不同，统一 LOWER 比较）。
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)
            """, Integer.class, table, column);
        if (count != null && count == 0) {
            jdbcTemplate.execute(alterSql);
            log.info("zyagent schema migration: added {}.{}", table, column);
        }
    }
}
