package com.zyagent.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.config.ZyagentProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 面试官 Agent 的解析层：调用 {@link InterviewAgent}，解析 JSON，做校验、超时与降级。
 *
 * <p>解析失败或调用超时时返回 {@code usable=false} 的兜底结果，**不把无效输出伪装成有效评价**。
 */
@Service
public class InterviewAgentService {
    private static final Logger log = LoggerFactory.getLogger(InterviewAgentService.class);

    private final InterviewAgent agent;
    private final ObjectMapper objectMapper;
    private final long modelTimeoutMs;
    private final ExecutorService executor;

    public InterviewAgentService(InterviewAgent agent, ObjectMapper objectMapper, ZyagentProperties properties) {
        this.agent = agent;
        this.objectMapper = objectMapper;
        ZyagentProperties.Interview interview = properties == null ? null : properties.interview();
        this.modelTimeoutMs = interview == null ? 8000L : Math.max(1L, interview.modelTimeoutMs());
        this.executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "zyagent-interview-agent");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    public QuestionPlan nextQuestion(InterviewSession session, List<InterviewTurn> history) {
        String raw = callWithTimeout(() -> agent.nextQuestion(session, history));
        if (raw == null) {
            return new QuestionPlan(fallbackQuestion(session), false, "面试官模型调用超时或不可用，使用本地兜底问题");
        }
        try {
            String question = objectMapper.readTree(raw).path("question").asText(null);
            if (question == null || question.isBlank()) {
                throw new IllegalArgumentException("缺少 question 字段");
            }
            return new QuestionPlan(question, true, "");
        } catch (Exception ex) {
            return new QuestionPlan(fallbackQuestion(session), false,
                "面试官输出无法解析（" + ex.getClass().getSimpleName() + "），使用本地兜底问题");
        }
    }

    public AnswerAssessment assess(InterviewSession session, String question, String answer, boolean allowFollowUp) {
        String raw = callWithTimeout(() -> agent.evaluateAnswer(session, question, answer, allowFollowUp));
        if (raw == null) {
            return unusableAssessment("评价模型调用超时或不可用，本轮评价不可用");
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.has("technicalCorrectness")) {
                throw new IllegalArgumentException("缺少评分字段");
            }
            InterviewEvaluation evaluation = new InterviewEvaluation(
                node.path("technicalCorrectness").asInt(),
                node.path("completeness").asInt(),
                node.path("projectEvidence").asInt(),
                node.path("expressionStructure").asInt(),
                toStringList(node.path("explanations")),
                toStringList(node.path("evidence")),
                true,
                "");
            boolean followUp = allowFollowUp && node.path("followUp").asBoolean(false);
            String followUpQuestion = followUp ? node.path("followUpQuestion").asText(null) : null;
            if (followUpQuestion == null || followUpQuestion.isBlank()) {
                followUp = false;
                followUpQuestion = null;
            }
            return new AnswerAssessment(evaluation, followUp, followUpQuestion, true, "");
        } catch (Exception ex) {
            return unusableAssessment("评价输出无法解析（" + ex.getClass().getSimpleName() + "），本轮评价不可用");
        }
    }

    private AnswerAssessment unusableAssessment(String note) {
        return new AnswerAssessment(InterviewEvaluation.fallback(note), false, null, false, note);
    }

    private String fallbackQuestion(InterviewSession session) {
        String type = session.interviewType() == null ? "" : session.interviewType();
        return switch (type) {
            case "项目深挖" -> "请介绍一个你主导的后端项目，说明你的职责与关键技术取舍。";
            case "系统设计" -> "请设计一个高并发场景下的后端方案，并说明权衡。";
            case "Java 基础" -> "请解释一个你熟悉的 Java 核心机制及其适用场景。";
            default -> "请介绍一段与目标岗位最相关的经历。";
        };
    }

    private List<String> toStringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        }
        return values;
    }

    private String callWithTimeout(Supplier<String> call) {
        Future<String> future;
        try {
            future = executor.submit(call::get);
        } catch (RuntimeException ex) {
            return null;
        }
        try {
            return future.get(modelTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.warn("面试官模型调用超时（{}ms）", modelTimeoutMs);
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            log.warn("面试官模型调用失败：{}", cause.getClass().getSimpleName());
            return null;
        }
    }
}
