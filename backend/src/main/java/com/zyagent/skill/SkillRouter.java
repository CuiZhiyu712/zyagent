package com.zyagent.skill;

import com.zyagent.agent.AgentMode;
import org.springframework.stereotype.Component;

@Component
public class SkillRouter {
    private final SkillCatalog catalog;

    public SkillRouter(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    public SkillDefinition route(AgentMode explicitMode, String message) {
        if (explicitMode != null) {
            return catalog.byMode(explicitMode);
        }

        String text = message == null ? "" : message.toLowerCase();
        if (containsAny(text, "jd", "岗位", "招聘", "投递", "公司", "职位")) {
            return catalog.byId("job_analysis_skill");
        }
        if (containsAny(text, "简历", "项目亮点", "star", "经历", "优化")) {
            return catalog.byId("resume_coach_skill");
        }
        if (containsAny(text, "面试", "追问", "模拟", "系统设计", "八股")) {
            return catalog.byId("interview_skill");
        }
        if (containsAny(text, "复盘", "薄弱点", "错题", "改进")) {
            return catalog.byId("review_skill");
        }
        if (containsAny(text, "知识库", "资料", "笔记", "论文", "总结")) {
            return catalog.byId("knowledge_search_skill");
        }
        return catalog.byId("learning_tutor_skill");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
