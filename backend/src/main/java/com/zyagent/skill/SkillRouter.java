package com.zyagent.skill;

import com.zyagent.agent.AgentMode;
import com.zyagent.agent.AgentRouteCategory;
import com.zyagent.agent.AgentRouteDecision;
import com.zyagent.agent.ChatMemorySnapshot;
import com.zyagent.agent.RouteAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 路由器。
 *
 * <p>决策顺序（越靠前越优先）：
 * <ol>
 *   <li>用户显式选择的模式；</li>
 *   <li>元反馈（纠错/吐槽）→ 无工具的 chat_skill；</li>
 *   <li>执行既有学习计划的某一天 → review_skill（沿用原计划结构）；</li>
 *   <li><b>Active Skill 优先</b>：会话里当前任务仍能处理本轮请求 → {@code CONTINUE}，不再每轮从零重选；</li>
 *   <li>LLM 结合上下文识别意图；</li>
 *   <li>面试上下文启发式；</li>
 *   <li>声明式触发词表；</li>
 *   <li>指代不明且无可继承任务 → {@code CLARIFY}；</li>
 *   <li>兜底 learning_tutor_skill。</li>
 * </ol>
 */
@Component
public class SkillRouter {
    /** 声明式 Skill 触发词表：顺序即优先级，第一个命中的 Skill 胜出。 */
    private static final List<Trigger> TRIGGERS = List.of(
        new Trigger("job_analysis_skill", AgentRouteCategory.JOB,
            List.of("jd", "岗位", "招聘", "投递", "公司", "职位"), "命中岗位/JD相关关键词，进入岗位分析流程"),
        new Trigger("resume_coach_skill", AgentRouteCategory.RESUME,
            List.of("简历", "项目亮点", "star", "经历", "优化"), "命中简历/项目表达关键词，进入简历顾问流程"),
        new Trigger("learning_tutor_skill", AgentRouteCategory.STUDY_PLAN,
            List.of("学习计划", "制定计划", "学习路线", "复习计划", "全覆盖", "覆盖所有问题"), "命中学习计划/覆盖范围关键词，进入学习导师流程"),
        new Trigger("interview_skill", AgentRouteCategory.INTERVIEW,
            List.of("面试", "追问", "模拟", "系统设计", "八股"), "命中面试/追问关键词，进入面试官流程"),
        new Trigger("review_skill", AgentRouteCategory.REVIEW,
            List.of("复盘", "薄弱点", "错题", "改进"), "命中复盘/薄弱点关键词，进入复盘教练流程"),
        new Trigger("knowledge_search_skill", AgentRouteCategory.KNOWLEDGE_QA,
            List.of("知识库", "资料", "笔记", "论文", "总结"), "命中知识检索关键词，进入知识问答流程"),
        new Trigger("learning_tutor_skill", AgentRouteCategory.STUDY_PLAN,
            List.of("补强", "路线", "复习", "学习"), "命中学习计划关键词，进入学习导师流程")
    );

    /** 一次性 Skill：不作为可继承的 Active Skill。 */
    private static final List<String> ONE_SHOT_SKILLS = List.of("chat_skill", "clarify_skill");

    /** 明确要求创建/覆盖学习计划的触发词：规则优先于 LLM，避免被判成复盘类任务。 */
    private static final List<String> PLAN_CREATION_KEYWORDS = List.of(
        "学习计划", "制定计划", "学习路线", "复习计划", "覆盖所有问题", "覆盖所有", "全覆盖");

    private final SkillCatalog catalog;
    private final ContextIntentClassifier intentClassifier;

    public SkillRouter(SkillCatalog catalog) {
        this(catalog, null);
    }

    @Autowired
    public SkillRouter(SkillCatalog catalog, ContextIntentClassifier intentClassifier) {
        this.catalog = catalog;
        this.intentClassifier = intentClassifier;
    }

    public SkillDefinition route(AgentMode explicitMode, String message) {
        return catalog.byId(routeDecision(explicitMode, message).skillId());
    }

    public SkillDefinition route(AgentMode explicitMode, String message, ChatMemorySnapshot memory) {
        return catalog.byId(routeDecision(explicitMode, message, memory).skillId());
    }

    public SkillDefinition route(AgentRouteDecision decision) {
        return catalog.byId(decision == null ? "" : decision.skillId());
    }

    /** 不带会话状态的纯规则路由（首轮、或无法取得 Active Skill 时使用）。 */
    public AgentRouteDecision routeDecision(AgentMode explicitMode, String message) {
        if (explicitMode != null) {
            SkillDefinition skill = catalog.byMode(explicitMode);
            return new AgentRouteDecision(
                categoryForMode(explicitMode), skill.id(), skill.name(), List.of(explicitMode.name()),
                skill.toolNames(), "用户显式选择 " + explicitMode.name() + "，直接使用 " + skill.name(),
                RouteAction.SWITCH, null);
        }
        if (IntentSignals.isMetaFeedback(message)) {
            return buildDecision("chat_skill", AgentRouteCategory.CHAT, List.of("元反馈/纠错"),
                "用户在对上一轮回答做元层面反馈，进入对话澄清流程", RouteAction.SWITCH, null);
        }
        AgentRouteDecision keyword = keywordRoute(message);
        if (keyword != null) {
            return keyword;
        }
        return fallbackDecision(null);
    }

    /**
     * 带会话状态的完整路由：先判断 Active Skill 能否继续，再决定 CONTINUE / SWITCH / CLARIFY。
     */
    public AgentRouteDecision routeDecision(AgentMode explicitMode, String message, ChatMemorySnapshot memory) {
        if (explicitMode != null) {
            return routeDecision(explicitMode, message);
        }
        String activeSkill = memory == null ? null : memory.activeSkillId();
        if (IntentSignals.isMetaFeedback(message)) {
            return buildDecision("chat_skill", AgentRouteCategory.CHAT, List.of("元反馈/纠错"),
                "用户在对上一轮回答做元层面反馈，进入对话澄清流程", RouteAction.SWITCH, activeSkill);
        }

        String text = message == null ? "" : message.toLowerCase();
        String context = memory == null || memory.summary() == null ? "" : memory.summary().toLowerCase();

        // ① 执行既有学习计划的某一天 = 学习计划任务的「继续」，不是切换到复盘。
        //    计划归学习导师所有，DayN 只是任务进度（currentDay），不是新的 Skill。
        boolean learningPlanContext = containsAny(context,
            "全覆盖学习计划", "学习计划清单", "学习计划", "计划清单", "周计划", "天计划", "按周", "按天", "全覆盖");
        if (learningPlanContext && IntentSignals.mentionsStudyDay(message)) {
            boolean continuingPlan = "learning_tutor_skill".equals(activeSkill);
            return buildDecision("learning_tutor_skill", AgentRouteCategory.STUDY_PLAN,
                List.of("学习计划上下文", "DayN任务进度"),
                "沿用既有学习计划的第 N 天（任务继续，不切换 Skill）",
                continuingPlan ? RouteAction.CONTINUE : RouteAction.SWITCH, activeSkill);
        }

        // ② 明确要求"制定/覆盖学习计划"：规则优先于 LLM，避免被判成复盘类任务后污染 Active Skill。
        List<String> planCreation = matchedKeywords(text, PLAN_CREATION_KEYWORDS);
        if (!planCreation.isEmpty()) {
            return buildDecision("learning_tutor_skill", AgentRouteCategory.STUDY_PLAN, planCreation,
                "命中学习计划创建/覆盖请求，规则优先进入学习导师流程", RouteAction.SWITCH, activeSkill);
        }

        // Active Skill 优先：当前任务还能处理本轮请求就继续，避免每轮重新选 Skill 造成漂移。
        if (activeSkill != null && !activeSkill.isBlank() && canContinueActiveSkill(activeSkill, message)) {
            SkillDefinition skill = catalog.byId(activeSkill);
            return new AgentRouteDecision(categoryOf(activeSkill), skill.id(), skill.name(),
                List.of("沿用当前任务"), skill.toolNames(),
                "本轮仍属于当前任务「" + skill.name() + "」，沿用该 Skill", RouteAction.CONTINUE, activeSkill);
        }

        if (intentClassifier != null) {
            ContextIntentResult intent = intentClassifier.classify(message, memory);
            if (intent.usable() && intentClassifier.supports(intent.skillId())) {
                return decisionForSkill(intent.skillId(), intent, "LLM 结合上下文识别意图", activeSkill);
            }
        }

        boolean interviewContext = containsAny(context, "面试", "面试官", "模拟面试", "追问");
        if (interviewContext && containsAny(text, "结束", "day1", "问题和答案", "学习思路", "训练包", "总结", "复盘", "学习计划")) {
            return buildDecision("review_skill", AgentRouteCategory.REVIEW, List.of("面试上下文", "结束/复盘训练"),
                "结合最近面试上下文，识别为面试结束后的复盘与学习计划请求", RouteAction.SWITCH, activeSkill);
        }
        boolean explicitJobSwitch = containsAny(text,
            "切换到岗位", "开始分析岗位", "请分析岗位", "帮我匹配岗位", "分析这个jd", "分析该jd");
        if (interviewContext && !explicitJobSwitch && IntentSignals.inheritsPreviousTask(message)) {
            return buildDecision("interview_skill", AgentRouteCategory.INTERVIEW, List.of("面试上下文"),
                "结合最近面试上下文，判定为延续请求，保持面试官流程", RouteAction.SWITCH, activeSkill);
        }

        AgentRouteDecision keyword = keywordRoute(message);
        if (keyword != null) {
            return keyword.withAction(RouteAction.SWITCH, activeSkill, keyword.reason());
        }

        // 指代不明（"帮我看看这个怎么样"）且没有可继承的任务 → 澄清，不硬选 Skill。
        if (IntentSignals.looksReferential(message)) {
            return buildDecision("clarify_skill", AgentRouteCategory.CLARIFY, List.of("指代不明"),
                "请求缺少明确对象，先向用户澄清要做什么", RouteAction.CLARIFY, activeSkill);
        }
        return fallbackDecision(activeSkill);
    }

    /**
     * 当前 Active Skill 能否处理本轮请求。
     *
     * <p>延续措辞直接继续；命中**其他** Skill 的触发词则让位给新任务；
     * 定义型新问题（"什么是…"）也按新话题处理；其余没有任何信号的请求留在当前任务，避免无谓漂移。
     */
    private boolean canContinueActiveSkill(String activeSkillId, String message) {
        if (ONE_SHOT_SKILLS.contains(activeSkillId)) {
            return false;
        }
        if (!catalog.byId(activeSkillId).id().equals(activeSkillId)) {
            return false;
        }
        if (IntentSignals.inheritsPreviousTask(message)) {
            return true;
        }
        AgentRouteDecision competing = keywordRoute(message);
        if (competing != null && !competing.skillId().equals(activeSkillId)) {
            return false;
        }
        return !IntentSignals.looksDefinitionalQuestion(message);
    }

    private AgentRouteDecision keywordRoute(String message) {
        String text = message == null ? "" : message.toLowerCase();
        for (Trigger trigger : TRIGGERS) {
            List<String> matched = matchedKeywords(text, trigger.keywords());
            if (!matched.isEmpty()) {
                return buildDecision(trigger.skillId(), trigger.category(), matched, trigger.reason(), RouteAction.SWITCH, null);
            }
        }
        return null;
    }

    private AgentRouteDecision fallbackDecision(String activeSkill) {
        SkillDefinition fallback = catalog.byId("learning_tutor_skill");
        return new AgentRouteDecision(AgentRouteCategory.GENERAL, fallback.id(), fallback.name(), List.of(),
            fallback.toolNames(), "未命中明确任务类型，使用学习导师作为通用兜底", RouteAction.SWITCH, activeSkill);
    }

    private AgentRouteDecision decisionForSkill(String skillId, ContextIntentResult intent, String reason, String activeSkill) {
        List<String> refs = intent.contextRefs() == null || intent.contextRefs().isEmpty()
            ? List.of(intent.intent(), intent.stage())
            : intent.contextRefs();
        return buildDecision(skillId, categoryOf(skillId), refs,
            reason + "（置信度 " + String.format("%.2f", intent.confidence()) + "）"
                + (intent.stage().isBlank() ? "" : "，阶段：" + intent.stage()),
            RouteAction.SWITCH, activeSkill);
    }

    private AgentRouteDecision buildDecision(String skillId, AgentRouteCategory category, List<String> keywords,
                                             String reason, RouteAction action, String previousSkill) {
        SkillDefinition skill = catalog.byId(skillId);
        return new AgentRouteDecision(category, skill.id(), skill.name(), keywords, skill.toolNames(),
            reason + (keywords.isEmpty() ? "" : "：" + String.join(", ", keywords)), action, previousSkill);
    }

    private List<String> matchedKeywords(String text, List<String> keywords) {
        List<String> matched = new ArrayList<>();
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                matched.add(keyword);
            }
        }
        return matched;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** Skill 对应的展示分类；knowledge_search_skill 与学习导师共用模式，需要单独归类。 */
    private AgentRouteCategory categoryOf(String skillId) {
        if ("knowledge_search_skill".equals(skillId)) {
            return AgentRouteCategory.KNOWLEDGE_QA;
        }
        return categoryForMode(catalog.byId(skillId).mode());
    }

    private AgentRouteCategory categoryForMode(AgentMode mode) {
        return switch (mode) {
            case RESUME_COACH -> AgentRouteCategory.RESUME;
            case JOB_ANALYST -> AgentRouteCategory.JOB;
            case INTERVIEWER -> AgentRouteCategory.INTERVIEW;
            case REVIEW_COACH -> AgentRouteCategory.REVIEW;
            case LEARNING_TUTOR -> AgentRouteCategory.STUDY_PLAN;
            case CHAT -> AgentRouteCategory.CHAT;
            case CLARIFY -> AgentRouteCategory.CLARIFY;
        };
    }

    private record Trigger(String skillId, AgentRouteCategory category, List<String> keywords, String reason) {
    }
}
