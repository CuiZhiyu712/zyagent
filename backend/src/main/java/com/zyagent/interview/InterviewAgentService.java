package com.zyagent.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 面试官 Agent 的解析层：调用 {@link InterviewAgent}，解析 JSON，做校验、超时与降级。
 *
 * <p>解析失败或调用超时时返回 {@code usable=false} 的兜底结果，**不把无效输出伪装成有效评价**。
 */
@Service
public class InterviewAgentService {
    private static final Logger log = LoggerFactory.getLogger(InterviewAgentService.class);
    private static final Pattern OUTER_JSON_FENCE = Pattern.compile(
        "\\A```json[ \\t]*\\r?\\n(.*?)\\r?\\n```\\z", Pattern.DOTALL);

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
            String json = unwrapSingleJsonFence(raw);
            JsonNode node = objectMapper.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(json);
            if (node == null || !node.isObject()) {
                throw new EvaluationValidationException("JSON 内容必须是对象");
            }
            int technicalCorrectness = requiredScore(node, "technicalCorrectness", "技术正确性");
            int completeness = requiredScore(node, "completeness", "完整性");
            int projectEvidence = requiredScore(node, "projectEvidence", "项目证据");
            int expressionStructure = requiredScore(node, "expressionStructure", "表达结构");
            List<String> explanations = requiredTextArray(node, "explanations", "解释");
            List<String> evidence = requiredTextArray(node, "evidence", "证据");
            validateEvidence(evidence, answer);

            boolean requestedFollowUp = node.path("followUp").asBoolean(false);
            String requestedFollowUpQuestion = node.path("followUpQuestion").asText(null);
            if (requestedFollowUp && (requestedFollowUpQuestion == null || requestedFollowUpQuestion.isBlank())) {
                throw new EvaluationValidationException("追问必须包含非空追问问题");
            }

            InterviewEvaluation evaluation = new InterviewEvaluation(
                technicalCorrectness,
                completeness,
                projectEvidence,
                expressionStructure,
                explanations,
                evidence,
                true,
                "");
            boolean followUp = allowFollowUp && requestedFollowUp;
            String followUpQuestion = followUp ? requestedFollowUpQuestion : null;
            return new AnswerAssessment(evaluation, followUp, followUpQuestion, true, "");
        } catch (Exception ex) {
            String reason = ex instanceof EvaluationValidationException validationException
                ? validationException.getMessage()
                : "JSON 格式错误";
            return unusableAssessment("评价输出无法解析（" + reason + "），本轮评价不可用");
        }
    }

    private static String unwrapSingleJsonFence(String raw) {
        String candidate = raw.strip();
        Matcher matcher = OUTER_JSON_FENCE.matcher(candidate);
        return matcher.matches() ? matcher.group(1) : candidate;
    }

    private static int requiredScore(JsonNode node, String field, String label) {
        JsonNode value = node.get(field);
        if (value == null) {
            throw new EvaluationValidationException("缺少评分字段：" + label);
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new EvaluationValidationException("评分必须为整数：" + label);
        }
        int score = value.intValue();
        if (score < 0 || score > 5) {
            throw new EvaluationValidationException("评分必须在 0 到 5 之间：" + label);
        }
        return score;
    }

    private static List<String> requiredTextArray(JsonNode node, String field, String label) {
        JsonNode values = node.get(field);
        if (values == null || !values.isArray()) {
            throw new EvaluationValidationException(label + "必须是字符串数组");
        }
        List<String> result = new ArrayList<>(values.size());
        for (JsonNode value : values) {
            if (!value.isTextual()) {
                throw new EvaluationValidationException(label + "数组项必须是文本");
            }
            result.add(value.textValue());
        }
        return result;
    }

    private static void validateEvidence(List<String> evidence, String answer) {
        for (String item : evidence) {
            if (!item.isBlank() && (answer == null || !answer.contains(item))) {
                throw new EvaluationValidationException("证据必须逐字引用回答原文");
            }
        }
    }

    private static final class EvaluationValidationException extends IllegalArgumentException {
        private EvaluationValidationException(String message) {
            super(message);
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
