package com.zyagent.ai;

import com.zyagent.interview.InterviewAgent;
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
            "interviewType", value(session.interviewType()),
            "difficulty", value(session.difficulty()),
            "jobDescription", untrustedBlock("job-description", session.jdSnapshot()),
            "history", formatHistory(history)));
        return chatClient.complete(questionSystemPrompt, userPrompt);
    }

    @Override
    public String evaluateAnswer(InterviewSession session, String question, String answer, boolean allowFollowUp) {
        requireAvailable();
        String userPrompt = render(evaluationUserPrompt, Map.of(
            "jobDescription", untrustedBlock("job-description", session.jdSnapshot()),
            "question", untrustedBlock("current-question", question),
            "answer", untrustedBlock("candidate-answer", answer),
            "allowFollowUp", Boolean.toString(allowFollowUp)));
        return chatClient.complete(evaluationSystemPrompt, userPrompt);
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
                + "回答：\n" + untrustedBlock("prior-answer", turn.answer()))
            .collect(Collectors.joining("\n\n"));
    }

    private static String untrustedBlock(String label, String content) {
        String closingTag = "</untrusted-" + label + ">";
        String safeContent = value(content).replace(closingTag, "&lt;/untrusted-" + label + "&gt;");
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
