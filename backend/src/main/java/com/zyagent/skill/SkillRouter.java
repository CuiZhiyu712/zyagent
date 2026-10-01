package com.zyagent.skill;

import com.zyagent.agent.AgentMode;
import com.zyagent.agent.AgentRouteCategory;
import com.zyagent.agent.AgentRouteDecision;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SkillRouter {
    private final SkillCatalog catalog;

    public SkillRouter(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    public SkillDefinition route(AgentMode explicitMode, String message) {
        return catalog.byId(routeDecision(explicitMode, message).skillId());
    }

    public AgentRouteDecision routeDecision(AgentMode explicitMode, String message) {
        if (explicitMode != null) {
            SkillDefinition skill = catalog.byMode(explicitMode);
            return new AgentRouteDecision(
                categoryForMode(explicitMode),
                skill.id(),
                skill.name(),
                List.of(explicitMode.name()),
                skill.toolNames(),
                "用户显式选择 " + explicitMode.name() + "，直接使用 " + skill.name()
            );
        }

        String text = message == null ? "" : message.toLowerCase();
        RouteMatch job = match(text, AgentRouteCategory.JOB, "job_analysis_skill", "jd", "岗位", "招聘", "投递", "公司", "职位");
        if (!job.keywords().isEmpty()) {
            return decision(job, "命中岗位/JD相关关键词，进入岗位分析流程");
        }
        RouteMatch resume = match(text, AgentRouteCategory.RESUME, "resume_coach_skill", "简历", "项目亮点", "star", "经历", "优化");
        if (!resume.keywords().isEmpty()) {
            return decision(resume, "命中简历/项目表达关键词，进入简历顾问流程");
        }
        RouteMatch interview = match(text, AgentRouteCategory.INTERVIEW, "interview_skill", "面试", "追问", "模拟", "系统设计", "八股");
        if (!interview.keywords().isEmpty()) {
            return decision(interview, "命中面试/追问关键词，进入面试官流程");
        }
        RouteMatch review = match(text, AgentRouteCategory.REVIEW, "review_skill", "复盘", "薄弱点", "错题", "改进");
        if (!review.keywords().isEmpty()) {
            return decision(review, "命中复盘/薄弱点关键词，进入复盘教练流程");
        }
        RouteMatch knowledge = match(text, AgentRouteCategory.KNOWLEDGE_QA, "knowledge_search_skill", "知识库", "资料", "笔记", "论文", "总结");
        if (!knowledge.keywords().isEmpty()) {
            return decision(knowledge, "命中知识检索关键词，进入知识问答流程");
        }
        RouteMatch study = match(text, AgentRouteCategory.STUDY_PLAN, "learning_tutor_skill", "学习计划", "补强", "路线", "复习", "学习");
        if (!study.keywords().isEmpty()) {
            return decision(study, "命中学习计划关键词，进入学习导师流程");
        }

        SkillDefinition fallback = catalog.byId("learning_tutor_skill");
        return new AgentRouteDecision(
            AgentRouteCategory.GENERAL,
            fallback.id(),
            fallback.name(),
            List.of(),
            fallback.toolNames(),
            "未命中明确任务类型，使用学习导师作为通用兜底"
        );
    }

    private RouteMatch match(String text, AgentRouteCategory category, String skillId, String... keywords) {
        List<String> matched = new ArrayList<>();
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                matched.add(keyword);
            }
        }
        return new RouteMatch(category, skillId, matched);
    }

    private AgentRouteDecision decision(RouteMatch match, String reason) {
        SkillDefinition skill = catalog.byId(match.skillId());
        return new AgentRouteDecision(match.category(), skill.id(), skill.name(), match.keywords(), skill.toolNames(), reason + "：" + String.join(", ", match.keywords()));
    }

    private AgentRouteCategory categoryForMode(AgentMode mode) {
        return switch (mode) {
            case RESUME_COACH -> AgentRouteCategory.RESUME;
            case JOB_ANALYST -> AgentRouteCategory.JOB;
            case INTERVIEWER -> AgentRouteCategory.INTERVIEW;
            case REVIEW_COACH -> AgentRouteCategory.REVIEW;
            case LEARNING_TUTOR -> AgentRouteCategory.STUDY_PLAN;
        };
    }

    private record RouteMatch(AgentRouteCategory category, String skillId, List<String> keywords) {
    }
}
