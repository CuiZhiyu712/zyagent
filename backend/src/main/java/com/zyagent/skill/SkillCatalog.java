package com.zyagent.skill;

import com.zyagent.agent.AgentMode;
import com.zyagent.job.JobDescriptionParser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SkillCatalog {
    private final Map<String, SkillDefinition> byId = new LinkedHashMap<>();
    private final Map<AgentMode, SkillDefinition> byMode = new LinkedHashMap<>();

    public SkillCatalog(JobDescriptionParser ignoredParser) {
        register(new SkillDefinition(
            "learning_tutor_skill",
            "学习导师 Skill",
            "解释知识点、整理学习资料、生成复习计划。",
            AgentMode.LEARNING_TUTOR,
            List.of("search_personal_knowledge", "generate_study_plan"),
            List.of("检索个人知识库", "组织知识解释", "生成复习建议")
        ));
        register(new SkillDefinition(
            "resume_coach_skill",
            "简历顾问 Skill",
            "优化简历、提炼项目亮点、生成 STAR 表达。",
            AgentMode.RESUME_COACH,
            List.of("search_personal_knowledge", "analyze_project_experience", "generate_resume_suggestion"),
            List.of("读取简历和项目资料", "提炼项目亮点", "生成简历修改建议")
        ));
        register(new SkillDefinition(
            "job_analysis_skill",
            "岗位分析 Skill",
            "解析 JD、提取关键词、分析岗位匹配方向。",
            AgentMode.JOB_ANALYST,
            List.of("parse_job_description", "match_resume_job"),
            List.of("解析岗位 JD", "提取技术关键词", "输出岗位匹配建议")
        ));
        register(new SkillDefinition(
            "interview_skill",
            "技术面试 Skill",
            "生成模拟面试问题、连续追问并给出评分建议。",
            AgentMode.INTERVIEWER,
            List.of("generate_interview_questions", "search_personal_knowledge"),
            List.of("确定面试方向", "生成首轮问题", "结合回答继续追问")
        ));
        register(new SkillDefinition(
            "review_skill",
            "复盘教练 Skill",
            "归纳面试薄弱点，生成后续学习补强计划。",
            AgentMode.REVIEW_COACH,
            List.of("generate_study_plan", "search_personal_knowledge"),
            List.of("读取复盘内容", "归纳薄弱点", "生成补强计划")
        ));
        register(new SkillDefinition(
            "knowledge_search_skill",
            "知识库检索 Skill",
            "直接检索个人知识库并基于引用回答。",
            AgentMode.LEARNING_TUTOR,
            List.of("search_personal_knowledge"),
            List.of("检索相关资料", "筛选引用来源", "基于资料回答")
        ));
        register(new SkillDefinition(
            "chat_skill",
            "对话澄清 Skill",
            "处理闲聊、确认与对回答的纠错反馈；不注册任何业务工具，避免把元反馈当业务参数。",
            AgentMode.CHAT,
            List.of(),
            List.of("复述理解到的差异", "结合上下文澄清或修正")
        ));
        register(new SkillDefinition(
            "clarify_skill",
            "意图澄清 Skill",
            "请求缺少明确对象时向用户确认要做什么；不注册业务工具，不猜测对象。",
            AgentMode.CLARIFY,
            List.of(),
            List.of("提出一个澄清问题", "列出可选任务方向")
        ));
    }

    public SkillDefinition byId(String id) {
        return byId.getOrDefault(id, byId.get("learning_tutor_skill"));
    }

    public SkillDefinition byMode(AgentMode mode) {
        return byMode.getOrDefault(mode, byId("learning_tutor_skill"));
    }

    private void register(SkillDefinition definition) {
        byId.put(definition.id(), definition);
        byMode.putIfAbsent(definition.mode(), definition);
    }
}
