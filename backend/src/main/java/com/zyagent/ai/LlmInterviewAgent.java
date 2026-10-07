package com.zyagent.ai;

import com.zyagent.interview.InterviewAgent;
import com.zyagent.interview.InterviewEvaluation;
import com.zyagent.interview.InterviewSession;
import com.zyagent.interview.InterviewTurn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "zyagent.interview", name = "provider", havingValue = "llm", matchIfMissing = true)
public class LlmInterviewAgent implements InterviewAgent {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9]*)}}");
    private static final Pattern UNTRUSTED_BOUNDARY = Pattern.compile(
        "</?untrusted-[a-z0-9-]+\\b[^>]*>", Pattern.CASE_INSENSITIVE);

    private final AiChatClient chatClient;
    private final String apiKey;
    private final String questionSystemPrompt;
    private final String questionUserPrompt;
    private final String evaluationSystemPrompt;
    private final String evaluationUserPrompt;

    public LlmInterviewAgent(
        AiChatClient chatClient,
        @Value("${DEEPSEEK_API_KEY:}") String apiKey,
        @Value("classpath:prompts/interview-question-system.st") Resource questionSystemPrompt,
        @Value("classpath:prompts/interview-question-user.st") Resource questionUserPrompt,
        @Value("classpath:prompts/interview-evaluation-system.st") Resource evaluationSystemPrompt,
        @Value("classpath:prompts/interview-evaluation-user.st") Resource evaluationUserPrompt
    ) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
        this.apiKey = apiKey;
        this.questionSystemPrompt = readPrompt(questionSystemPrompt);
        this.questionUserPrompt = readPrompt(questionUserPrompt);
        this.evaluationSystemPrompt = readPrompt(evaluationSystemPrompt);
        this.evaluationUserPrompt = readPrompt(evaluationUserPrompt);
    }

    @Override
    public String provider() {
        return "llm";
    }

    @Override
    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String label() {
        return "DeepSeek AI 面试官";
    }

    @Override
    public String nextQuestion(InterviewSession session, List<InterviewTurn> history) {
        requireAvailable();
        String userPrompt = render(questionUserPrompt, Map.of(
            "interviewType", untrustedBlock("interview-type", session.interviewType()),
            "difficulty", untrustedBlock("difficulty", session.difficulty()),
            "jobDescription", untrustedBlock("job-description", session.jdSnapshot()),
            "history", formatHistory(history)));
        return chatClient.completeWithoutTools(questionSystemPrompt, userPrompt);
    }

    @Override
    public String evaluateAnswer(InterviewSession session, String question, String answer, boolean allowFollowUp) {
        requireAvailable();
        String userPrompt = render(evaluationUserPrompt, Map.of(
            "interviewType", untrustedBlock("interview-type", session.interviewType()),
            "difficulty", untrustedBlock("difficulty", session.difficulty()),
            "jobDescription", untrustedBlock("job-description", session.jdSnapshot()),
            "question", untrustedBlock("current-question", question),
            "answer", untrustedBlock("candidate-answer", answer),
            "allowFollowUp", Boolean.toString(allowFollowUp)));
        return chatClient.completeWithoutTools(evaluationSystemPrompt, userPrompt);
    }

    private void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("DeepSeek API key is not configured for the LLM interview agent");
        }
    }

    private static String readPrompt(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read interview prompt " + resource.getDescription(), ex);
        }
    }

    private static String formatHistory(List<InterviewTurn> history) {
        if (history == null || history.isEmpty()) {
            return "（暂无历史面试记录。）";
        }
        return history.stream()
            .map(turn -> "第 " + turn.turnNo() + " 轮\n"
                + "问题：\n" + untrustedBlock("prior-question", turn.question()) + "\n"
                + "回答：\n" + untrustedBlock("prior-answer", turn.answer()) + "\n"
                + "评价摘要：\n" + untrustedBlock("prior-evaluation", evaluationSummary(turn.evaluation())))
            .collect(Collectors.joining("\n\n"));
    }

    private static String evaluationSummary(InterviewEvaluation evaluation) {
        if (evaluation == null || !evaluation.usable()) {
            return "无可用评价";
        }

        String[] dimensions = {"技术正确性", "完整性", "项目证据", "表达结构"};
        int[] scores = {evaluation.technicalCorrectness(), evaluation.completeness(),
            evaluation.projectEvidence(), evaluation.expressionStructure()};
        List<String> explanations = evaluation.explanations();
        StringBuilder summary = new StringBuilder();
        for (int index = 0; index < dimensions.length; index++) {
            if (index > 0) {
                summary.append('\n');
            }
            String explanation = index < explanations.size() ? explanations.get(index).strip() : "";
            String fullLabel = dimensions[index] + "：";
            if (explanation.startsWith(fullLabel)) {
                explanation = explanation.substring(fullLabel.length()).strip();
            } else if (explanation.startsWith(dimensions[index] + ":")) {
                explanation = explanation.substring((dimensions[index] + ":").length()).strip();
            }
            if (explanation.isEmpty()) {
                explanation = "无说明";
            }
            summary.append(dimensions[index]).append("：")
                .append(scores[index]).append("/5；").append(explanation);
        }
        return summary.toString();
    }

    private static String untrustedBlock(String label, String content) {
        String closingTag = "</untrusted-" + label + ">";
        String safeContent = value(content);
        if ("job-description".equals(label) && safeContent.isBlank()) {
            safeContent = "未提供";
        }
        Matcher matcher = UNTRUSTED_BOUNDARY.matcher(safeContent);
        StringBuffer sanitized = new StringBuffer();
        while (matcher.find()) {
            String escapedBoundary = matcher.group().replace("<", "&lt;").replace(">", "&gt;");
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(escapedBoundary));
        }
        matcher.appendTail(sanitized);
        safeContent = sanitized.toString();
        return "<untrusted-" + label + ">\n" + safeContent + "\n" + closingTag;
    }

    private static String render(String template, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(rendered,
                Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), "")));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
