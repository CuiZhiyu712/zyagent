package com.zyagent.modules.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性的规则版面试官，用于离线/无模型环境，输出与 LLM 相同的 JSON 结构。
 *
 * <p>评分启发式：回答越完整、越含技术关键词与项目量化证据、结构越清晰，分数越高；
 * 缺少项目证据时优先追问。它不引入外部依赖，便于测试与演示。
 */
@Component
public class RuleBasedInterviewAgent implements InterviewAgent {
    private static final List<String> TECH_TERMS = List.of(
        "redis", "mysql", "jvm", "线程", "索引", "缓存", "并发", "事务", "spring", "mq", "性能", "锁");
    private static final List<String> PROJECT_TERMS = List.of(
        "项目", "上线", "qps", "延迟", "优化", "故障", "万", "%", "降级", "压测");
    private static final List<String> STRUCTURE_TERMS = List.of(
        "首先", "其次", "最后", "第一", "第二", "背景", "方案", "结果", "取舍");

    private final ObjectMapper objectMapper;

    public RuleBasedInterviewAgent(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String nextQuestion(InterviewSession session, List<InterviewTurn> history) {
        String question = questionPool(session).stream()
            .skip(Math.min(history == null ? 0 : history.size(), Math.max(0, questionPool(session).size() - 1)))
            .findFirst()
            .orElse("请介绍一个你最有代表性的后端项目。");
        return toJson(Map.of("question", question, "focus", session.interviewType()));
    }

    @Override
    public String evaluateAnswer(InterviewSession session, String question, String answer, boolean allowFollowUp) {
        String text = answer == null ? "" : answer.strip().toLowerCase();
        int completeness = graded(text.length(), 20, 60, 120);
        int technicalCorrectness = containsAny(text, TECH_TERMS) ? 4 : 2;
        int projectEvidence = containsAny(text, PROJECT_TERMS) ? 4 : 1;
        int expressionStructure = containsAny(text, STRUCTURE_TERMS) ? 4 : 2;

        boolean weak = projectEvidence <= 1 || completeness <= 2;
        boolean followUp = allowFollowUp && weak;

        List<String> explanations = List.of(
            "技术正确性：" + (technicalCorrectness >= 4 ? "提到了关键技术点" : "缺少具体技术细节"),
            "完整性：" + (completeness >= 3 ? "覆盖了主要方面" : "回答偏简略"),
            "项目证据：" + (projectEvidence >= 4 ? "包含可验证的项目结果" : "缺少真实项目场景或量化结果"),
            "表达结构：" + (expressionStructure >= 4 ? "结构清晰" : "建议按背景-方案-结果组织"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("technicalCorrectness", technicalCorrectness);
        payload.put("completeness", completeness);
        payload.put("projectEvidence", projectEvidence);
        payload.put("expressionStructure", expressionStructure);
        payload.put("explanations", explanations);
        payload.put("evidence", List.of());
        payload.put("followUp", followUp);
        payload.put("followUpQuestion", followUp
            ? "你缺少具体项目证据，能补充一个真实场景、你的取舍和量化结果吗？"
            : null);
        return toJson(payload);
    }

    private List<String> questionPool(InterviewSession session) {
        String type = session.interviewType() == null ? "" : session.interviewType();
        return switch (type) {
            case "项目深挖" -> List.of(
                "请选一个你主导的后端项目，说明业务背景、你的职责和技术选型。",
                "这个项目里最棘手的线上问题是什么？你如何定位和解决？",
                "如果流量增长十倍，你会先优化哪一层？为什么？");
            case "系统设计" -> List.of(
                "设计一个短链接服务，说明存储选型与并发考虑。",
                "如何设计一个高可用的限流系统？",
                "设计一个消息幂等消费的方案。");
            case "Java 基础" -> List.of(
                "JVM 内存结构与常见垃圾回收器有什么区别？",
                "HashMap 的扩容机制和线程安全问题是什么？",
                "谈谈你对 Java 并发包的理解。");
            default -> List.of(
                "请做一个简短的自我介绍，突出与岗位最相关的经历。",
                "介绍一个你最有代表性的后端项目。",
                "你在项目中做过哪些性能优化？");
        };
    }

    private static int graded(int length, int low, int mid, int high) {
        if (length >= high) {
            return 4;
        }
        if (length >= mid) {
            return 3;
        }
        return length >= low ? 2 : 1;
    }

    private static boolean containsAny(String text, List<String> terms) {
        return terms.stream().anyMatch(text::contains);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
