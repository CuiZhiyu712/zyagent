package com.zyagent.modules.agent.skill;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路由与工具输入校验共用的意图信号判定。
 *
 * <p>两边必须用同一套判定，否则会出现"路由认为不是业务请求、工具却照旧执行"这类上下文不一致。
 */
public final class IntentSignals {
    /** 对系统/上一条回答的元层面反馈：既不是业务请求，也不能当作工具的内容输入。 */
    private static final List<String> META_FEEDBACK_MARKERS = List.of(
        "上下文不一致", "不一致", "答非所问", "跑偏", "搞错", "错了", "不对",
        "重新回答", "重新来", "重新生成", "幻觉", "编造", "你没按", "没按我");

    /** 执行既有计划里的某一天（Day1 / day 1 / 第一天 / 第2天）。大小写不敏感。 */
    private static final Pattern STUDY_DAY = Pattern.compile(
        "(?i)day\\s*\\d+|第\\s*(?:十[一二三四]|[一二三四五六七八九十]|1[0-4]|[1-9])\\s*天");

    /** 带捕获组的天数解析（Day2 / 第二天 → 2）。 */
    private static final Pattern STUDY_DAY_NUMBER = Pattern.compile(
        "(?i)day\\s*(\\d{1,2})|第\\s*(十[一二三四]|[一二三四五六七八九十]|1[0-4]|[1-9])\\s*天");
    private static final List<String> CJK_DAY_ORDER = List.of(
        "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二", "十三", "十四");

    private static final List<String> CONTINUATION_MARKERS = List.of(
        "继续", "接着", "下一步", "然后呢", "执行计划", "再讲", "再说说", "再来一个",
        "刚才", "上一个", "上面那", "下一题", "该题", "这个题");

    /** 指代上文的说法；单独出现（且没有其他任务线索）时属于"指代不明"，应先澄清。 */
    private static final List<String> REFERENTIAL_MARKERS = List.of(
        "这个", "那个", "它", "这些", "那些", "该", "此", "上面", "前面说的");

    /** 定义型/新话题提问：即使正在别的任务里，也应按新问题重新路由。 */
    private static final List<String> DEFINITIONAL_MARKERS = List.of(
        "什么是", "是什么", "的区别", "区别是", "原理", "解释一下", "如何理解", "怎么理解");

    private IntentSignals() {
    }

    public static boolean looksReferential(String message) {
        if (message == null) {
            return false;
        }
        String trimmed = message.strip();
        return !trimmed.isEmpty() && trimmed.length() <= 20 && containsAny(trimmed, REFERENTIAL_MARKERS);
    }

    public static boolean looksDefinitionalQuestion(String message) {
        return containsAny(message, DEFINITIONAL_MARKERS);
    }

    public static boolean isMetaFeedback(String message) {
        return containsAny(message, META_FEEDBACK_MARKERS);
    }

    public static boolean mentionsStudyDay(String message) {
        return message != null && STUDY_DAY.matcher(message).find();
    }

    /** 从消息里取出要执行的计划天数（Day2 / 第二天 → 2）；没有则返回 0。 */
    public static int studyDayNumber(String message) {
        if (message == null) {
            return 0;
        }
        Matcher matcher = STUDY_DAY_NUMBER.matcher(message);
        if (!matcher.find()) {
            return 0;
        }
        String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        if (token == null) {
            return 0;
        }
        if (token.matches("\\d+")) {
            return Integer.parseInt(token);
        }
        return Math.max(0, CJK_DAY_ORDER.indexOf(token) + 1);
    }

    public static boolean isContinuation(String message) {
        return containsAny(message, CONTINUATION_MARKERS);
    }

    /**
     * 是否应继承上一轮的任务/模式。
     *
     * <p>只有延续型请求（"继续/接着/下一步/刚才/下一题"）或执行既有计划某一天时才继承；
     * 一个自足的新问题必须按它自己的内容重新路由，否则会一直粘在上一个模式上。
     */
    public static boolean inheritsPreviousTask(String message) {
        return isContinuation(message) || mentionsStudyDay(message);
    }

    /** 消息里是否存在可用于调用"内容型工具"的实质输入（不是元反馈，也不是执行既有计划的指令）。 */
    public static boolean hasContentForTool(String toolName, String message) {
        if (message == null || message.isBlank() || isMetaFeedback(message)) {
            return false;
        }
        if ("generate_study_plan".equals(toolName)) {
            // 执行既有计划里的某一天时不重新生成计划，避免用模板覆盖上文已确认的计划结构。
            return !mentionsStudyDay(message) && !isContinuation(message);
        }
        return true;
    }

    public static boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsAny(String text, List<String> keywords) {
        return containsAny(text, keywords.toArray(String[]::new));
    }
}
