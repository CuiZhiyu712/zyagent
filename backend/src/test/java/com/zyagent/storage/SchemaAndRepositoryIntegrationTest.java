package com.zyagent.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.agent.AgentMode;
import com.zyagent.agent.task.AgentTask;
import com.zyagent.agent.task.AgentTaskState;
import com.zyagent.agent.task.AgentTaskStep;
import com.zyagent.interview.InterviewSession;
import com.zyagent.interview.InterviewSessionState;
import com.zyagent.interview.InterviewTurn;
import com.zyagent.interview.InterviewTurnState;
import com.zyagent.interview.InterviewEvaluation;
import com.zyagent.profile.ProfileSuggestion;
import com.zyagent.profile.ProfileSuggestionState;
import com.zyagent.profile.SkillChangeType;
import com.zyagent.profile.SkillEvidence;
import com.zyagent.profile.SkillHistoryEntry;
import com.zyagent.profile.SkillLevel;
import com.zyagent.profile.UserSkill;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 针对**真实 schema.sql** 的数据库集成测试（H2 MySQL 兼容模式）。
 *
 * <p>覆盖计划 §8 要求的：唯一约束、owner 过滤、启动恢复、schema 迁移可重复执行。
 * 应用层单元测试用内存假实现，这里验证的是 SQL 与约束本身。
 */
class SchemaAndRepositoryIntegrationTest {
    private JdbcTemplate jdbc;
    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:zyagent_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void idempotencyKeyIsUniqueAcrossTasks() {
        JdbcAgentTaskRepository repository = new JdbcAgentTaskRepository(jdbc);
        repository.save(task("task-1", "key-1"));

        assertThrows(DataIntegrityViolationException.class,
            () -> repository.save(task("task-2", "key-1")), "duplicate idempotency key rejected");
    }

    @Test
    void taskStepUpsertsOnSameTaskAndStepNo() {
        JdbcAgentTaskRepository taskRepository = new JdbcAgentTaskRepository(jdbc);
        taskRepository.save(task("task-1", null));
        JdbcAgentTaskStepRepository stepRepository = new JdbcAgentTaskStepRepository(jdbc);

        stepRepository.save(step("task-1", 1, "RUNNING"));
        stepRepository.save(step("task-1", 1, "SUCCESS"));

        List<AgentTaskStep> steps = stepRepository.findByTaskId("task-1");
        assertEquals(1, steps.size(), "unique (task_id, step_no) keeps a single row");
        assertEquals("SUCCESS", steps.get(0).status(), "upsert updated the state");
    }

    @Test
    void startupRecoveryMarksRunningTasksInterrupted() {
        JdbcAgentTaskRepository repository = new JdbcAgentTaskRepository(jdbc);
        repository.save(task("task-1", null));
        repository.save(new AgentTask("task-2", "local-user", "s", AgentMode.JOB_ANALYST,
            AgentTaskState.RUNNING, "req", null, null, null, LocalDateTime.now(), LocalDateTime.now(), null,
            LocalDateTime.now(), null));

        repository.markRunningTasksInterrupted();

        AgentTask interrupted = repository.findById("local-user", "task-2").orElseThrow();
        assertEquals(AgentTaskState.INTERRUPTED, interrupted.state());
        assertEquals("PROCESS_INTERRUPTED", interrupted.errorCode());
        assertEquals(AgentTaskState.PENDING, repository.findById("local-user", "task-1").orElseThrow().state(),
            "non-running tasks untouched");
    }

    @Test
    void interviewTurnRequestIdIsQueryableAndTurnNumberUnique() {
        JdbcInterviewRepository repository = new JdbcInterviewRepository(jdbc, new ObjectMapper());
        repository.saveSession(session("session-1", "owner-a"));

        repository.saveTurn(turn("session-1", 1, "req-1"));
        assertTrue(repository.findTurnByRequestId("session-1", "req-1").isPresent());

        repository.saveTurn(turn("session-1", 1, "req-1")
            .answered("我做过缓存优化", "req-1")
            .evaluated(new InterviewEvaluation(3, 3, 3, 3, List.of(), List.of(), true, ""), null));

        assertEquals(1, repository.findTurns("session-1").size(), "unique (session_id, turn_no)");
        assertEquals(InterviewTurnState.NEXT_QUESTION_READY, repository.findTurns("session-1").get(0).state());
    }

    @Test
    void interviewSessionsAreFilteredByOwner() {
        JdbcInterviewRepository repository = new JdbcInterviewRepository(jdbc, new ObjectMapper());
        repository.saveSession(session("session-a", "owner-a"));
        repository.saveSession(session("session-b", "owner-b"));

        List<InterviewSession> ownerA = repository.findSessions("owner-a", 0, 10);

        assertEquals(1, ownerA.size(), "only owner-a sessions returned");
        assertEquals("session-a", ownerA.get(0).id());
        assertEquals(1L, repository.countSessions("owner-a"));
    }

    @Test
    void profileSkillVersionIncrementsAndAuditRowsPersist() {
        JdbcProfileRepository repository = new JdbcProfileRepository(jdbc);
        UserSkill first = UserSkill.of("owner-a", "Redis", SkillLevel.BASIC, 0.7, "简历", "RESUME", null);
        repository.saveSkill(first);

        UserSkill second = first.nextVersion(SkillLevel.WORKING, 0.8, "面试", "INTERVIEW", "session-1#turn-1");
        repository.saveSkill(second);

        assertEquals(2, repository.findSkill("owner-a", "Redis").orElseThrow().version(), "version incremented");

        repository.saveEvidence(SkillEvidence.of("owner-a", "Redis", "面试第 1 轮平均分 4.0", "INTERVIEW", "session-1#turn-1"));
        repository.saveHistory(SkillHistoryEntry.of("owner-a", "Redis", first, second,
            SkillChangeType.SUGGESTION_APPROVED, "确认建议"));

        assertEquals(1, repository.findEvidence("owner-a", "Redis").size());
        assertEquals(1, repository.findHistory("owner-a", "Redis").size());
        assertEquals(SkillLevel.BASIC, repository.findHistory("owner-a", "Redis").get(0).oldLevel());
    }

    @Test
    void pendingSuggestionCanBeFilteredByState() {
        JdbcProfileRepository repository = new JdbcProfileRepository(jdbc);
        repository.saveSuggestion(pending("sug-1", "owner-a"));
        ProfileSuggestion approved = pending("sug-2", "owner-a").approved();
        repository.saveSuggestion(approved);

        assertEquals(1, repository.findSuggestions("owner-a", ProfileSuggestionState.PENDING).size());
        assertEquals(1, repository.findSuggestions("owner-a", ProfileSuggestionState.APPROVED).size());
        assertEquals(ProfileSuggestionState.APPROVED,
            repository.findSuggestion("sug-2").orElseThrow().state());
    }

    @Test
    void chatTaskStateRoundTrips() {
        ChatRepository repository = new ChatRepository(jdbc);
        repository.createSession("session-1", "对话", null);

        repository.saveTaskState("session-1", new com.zyagent.agent.ChatTaskState("STUDY_PLAN", "learning_tutor_skill", 2));

        com.zyagent.agent.ChatTaskState state = repository.findTaskState("session-1").orElseThrow();
        assertEquals("STUDY_PLAN", state.taskType());
        assertEquals("learning_tutor_skill", state.activeSkill(), "active skill is the routing source of truth");
        assertEquals(2, state.currentDay());
    }

    @Test
    void schemaInitializerAddsMissingColumnsToLegacyTable() throws Exception {
        // 模拟旧库：agent_task 已存在但没有 idempotency_key。
        jdbc.execute("""
            CREATE TABLE legacy_agent_task_probe(id VARCHAR(64) PRIMARY KEY)
            """);
        jdbc.execute("DROP TABLE agent_task");
        jdbc.execute("""
            CREATE TABLE agent_task (
                id VARCHAR(64) PRIMARY KEY,
                owner_id VARCHAR(128) NOT NULL,
                session_id VARCHAR(64),
                agent_mode VARCHAR(64) NOT NULL,
                state VARCHAR(32) NOT NULL,
                request_text TEXT NOT NULL,
                result_text TEXT,
                error_code VARCHAR(64),
                error_message TEXT,
                created_at TIMESTAMP NOT NULL,
                started_at TIMESTAMP NULL,
                finished_at TIMESTAMP NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """);

        new SchemaInitializer().zyagentSchemaRunner(dataSource).run(null);

        assertTrue(columnExists("agent_task", "idempotency_key"), "migration added the missing column");
        assertTrue(columnExists("profile_suggestion", "updated_at"), "profile migration applied too");

        // 可重复执行：再跑一次不应抛异常，也不应重复加列。
        new SchemaInitializer().zyagentSchemaRunner(dataSource).run(null);
        assertTrue(columnExists("agent_task", "idempotency_key"));
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE LOWER(table_name) = LOWER(?) AND LOWER(column_name) = LOWER(?)
            """, Integer.class, table, column);
        return count != null && count > 0;
    }

    private static AgentTask task(String id, String idempotencyKey) {
        LocalDateTime now = LocalDateTime.now();
        return new AgentTask(id, "local-user", "session-1", AgentMode.JOB_ANALYST, AgentTaskState.PENDING,
            "分析岗位", null, null, null, now, null, null, now, idempotencyKey);
    }

    private static AgentTaskStep step(String taskId, int stepNo, String status) {
        return new AgentTaskStep(taskId + "-step-" + stepNo, taskId, stepNo, "步骤", "search_personal_knowledge",
            status, "input", "output", null, 1, 5L, LocalDateTime.now());
    }

    private static InterviewSession session(String id, String ownerId) {
        LocalDateTime now = LocalDateTime.now();
        return new InterviewSession(id, ownerId, "job-1", null, "项目深挖", "中等",
            InterviewSessionState.IN_PROGRESS, 0, 8, 1, null, now, now);
    }

    private static InterviewTurn turn(String sessionId, int turnNo, String requestId) {
        return new InterviewTurn(sessionId + "-turn-" + turnNo, sessionId, turnNo, InterviewTurnState.QUESTION_READY,
            "请介绍项目", null, null, null, requestId, LocalDateTime.now(), LocalDateTime.now());
    }

    private static ProfileSuggestion pending(String id, String ownerId) {
        return new ProfileSuggestion(id, ownerId, "Redis", SkillLevel.WORKING, SkillLevel.BASIC,
            "面试第 1 轮", "回答体现缓存经验", "INTERVIEW", "session-1#turn-1", "rule-based-interviewer",
            ProfileSuggestionState.PENDING, "", LocalDateTime.now(), LocalDateTime.now());
    }
}
