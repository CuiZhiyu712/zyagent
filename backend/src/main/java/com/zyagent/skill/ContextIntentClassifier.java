package com.zyagent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.ai.AiChatService;
import com.zyagent.agent.ChatMemorySnapshot;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 使用一次轻量 LLM 调用识别当前任务阶段和 Skill；输出异常时交给规则路由兜底。
 */
@Service
public class ContextIntentClassifier {
    private static final Set<String> SUPPORTED_SKILLS = Set.of(
        "learning_tutor_skill", "resume_coach_skill", "job_analysis_skill",
        "interview_skill", "review_skill", "knowledge_search_skill", "chat_skill");

    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    public ContextIntentClassifier(AiChatService aiChatService, ObjectMapper objectMapper) {
        this.aiChatService = aiChatService;
        this.objectMapper = objectMapper;
    }

    public ContextIntentResult classify(String message, ChatMemorySnapshot memory) {
        String system = "你是任务路由器，只负责识别用户意图和当前对话阶段。"
            + "不要回答用户问题，不要调用工具。必须只输出一个 JSON 对象，字段为："
            + "intent、skillId、stage、confidence、contextRefs。skillId 只能是："
            + String.join(", ", SUPPORTED_SKILLS) + "。confidence 是 0 到 1 的数字。";
        String prompt = "最近对话上下文：\n" + limit(memory == null ? "" : memory.summary(), 9000)
            + "\n\n当前用户消息：\n" + limit(message, 1800)
            + "\n\n规则预判（本轮是否为延续请求）：" + (IntentSignals.inheritsPreviousTask(message) ? "是" : "否")
            + "\n\n识别原则："
            + "1) 只有当本轮是延续请求（如“继续/接着/下一步/刚才的回答/下一题/执行计划 DayN”）或明确指代上文任务时，才继承上文的 skill 与 stage；"
            + "2) 如果本轮是一个自足的新问题（自带主题词，例如“什么是 JVM 垃圾回收”），按本轮内容重新选择 skill，不要因为上一轮是学习/面试就沿用旧 skill；"
            + "3) 用户在对系统做元层面反馈（如“上下文不一致”“答非所问”）时选择 chat_skill。";
        try {
            return parse(aiChatService.complete(system, prompt));
        } catch (RuntimeException ex) {
            return ContextIntentResult.empty();
        }
    }

    public boolean supports(String skillId) {
        return SUPPORTED_SKILLS.contains(skillId);
    }

    private ContextIntentResult parse(String raw) {
        try {
        if (raw == null || raw.isBlank()) {
            return ContextIntentResult.empty();
        }
        String json = raw.trim().replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return ContextIntentResult.empty();
        }
        JsonNode node = objectMapper.readTree(json.substring(start, end + 1));
        String skillId = node.path("skillId").asText("");
        double confidence = node.path("confidence").asDouble(0D);
        if (!supports(skillId) || confidence < 0D || confidence > 1D) {
            return ContextIntentResult.empty();
        }
        List<String> refs = node.path("contextRefs").isArray()
            ? objectMapper.convertValue(node.path("contextRefs"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
            : List.of();
        return new ContextIntentResult(
            node.path("intent").asText(""), skillId, node.path("stage").asText(""), confidence, refs);
        } catch (Exception ex) {
            return ContextIntentResult.empty();
        }
    }

    private String limit(String value, int max) {
        String text = value == null ? "" : value;
        if (text.length() <= max) {
            return text;
        }
        int head = max / 2;
        int tail = max - head;
        return text.substring(0, head) + "\n...[中间上下文省略]...\n" + text.substring(text.length() - tail);
    }
}
