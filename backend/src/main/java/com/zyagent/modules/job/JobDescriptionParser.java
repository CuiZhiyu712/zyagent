package com.zyagent.modules.job;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JobDescriptionParser {
    private static final List<String> KNOWN_SKILLS = List.of(
        "Java", "Spring Boot", "Spring Cloud", "MySQL", "Redis", "JVM", "RabbitMQ", "Kafka",
        "Docker", "Kubernetes", "Linux", "计算机网络", "操作系统", "数据结构", "算法", "后端",
        "微服务", "高并发", "分布式", "agent", "Agent", "大模型", "LLM", "RAG"
    );

    public JobPosting parse(String rawText, String sourceUrl) {
        String text = rawText == null ? "" : rawText.strip();
        return new JobPosting(
            UUID.randomUUID().toString(),
            field(text, "公司", guessCompany(text)),
            field(text, "岗位", guessTitle(text)),
            field(text, "城市", guessCity(text)),
            field(text, "岗位类型", guessJobType(text)),
            field(text, "技术方向", guessDirection(text)),
            section(text, "岗位职责", "任职要求"),
            section(text, "任职要求", "加分项"),
            section(text, "加分项", null),
            skills(text),
            sourceUrl,
            null,
            LocalDateTime.now(),
            "COLLECTED",
            text
        );
    }

    private String field(String text, String label, String fallback) {
        Matcher matcher = Pattern.compile(label + "[:：]\\s*([^\\n\\r]+)").matcher(text);
        return matcher.find() ? cleanLine(matcher.group(1)) : fallback;
    }

    private String guessCompany(String text) {
        for (String company : List.of("阿里", "腾讯", "字节", "美团", "京东", "百度", "快手", "小米", "华为")) {
            if (text.contains(company)) {
                return company;
            }
        }
        return "未标注";
    }

    private String guessTitle(String text) {
        Matcher explicit = Pattern.compile("(Java[^，。；;\\n\\r]{0,24}(后端|服务端|后台)[^，。；;\\n\\r]{0,24}(工程师|开发|实习生|校招生)?)").matcher(text);
        if (explicit.find()) {
            return cleanLine(explicit.group(1));
        }
        if (text.toLowerCase().contains("agent") || text.contains("大模型")) {
            return "大模型 Agent 开发工程师";
        }
        return "未标注";
    }

    private String guessCity(String text) {
        for (String city : List.of("北京", "上海", "深圳", "杭州", "广州", "成都", "南京", "武汉", "西安", "苏州")) {
            if (text.contains(city)) {
                return city;
            }
        }
        return "未标注";
    }

    private String guessJobType(String text) {
        if (text.contains("实习")) {
            return "实习";
        }
        if (text.contains("校招")) {
            return "校招";
        }
        return text.contains("社招") ? "社招" : "未标注";
    }

    private String guessDirection(String text) {
        if (text.toLowerCase().contains("agent") || text.contains("大模型")) {
            return "AI / Agent";
        }
        return text.contains("后端") || text.contains("后台") || text.contains("服务端") ? "后端" : "未标注";
    }

    private List<String> section(String text, String start, String end) {
        int from = text.indexOf(start + "：");
        if (from < 0) {
            from = text.indexOf(start + ":");
        }
        if (from < 0) {
            return List.of();
        }
        from = text.indexOf('\n', from);
        if (from < 0) {
            return List.of();
        }
        int to = end == null ? text.length() : Math.max(text.indexOf(end + "：", from), text.indexOf(end + ":", from));
        if (to < 0) {
            to = text.length();
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.substring(from, to).split("\\R")) {
            String cleaned = cleanLine(line);
            if (!cleaned.isBlank()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }

    private List<String> skills(String text) {
        Set<String> skills = new LinkedHashSet<>();
        String lower = text.toLowerCase();
        for (String skill : KNOWN_SKILLS) {
            if (lower.contains(skill.toLowerCase())) {
                skills.add(skill);
            }
        }
        return List.copyOf(skills);
    }

    private String cleanLine(String line) {
        return line.replaceFirst("^\\s*[0-9一二三四五六七八九十]+[.、)]\\s*", "")
            .replace("；", "")
            .replace(";", "")
            .strip();
    }
}
