package com.zyagent.agent;

import java.util.ArrayList;
import java.util.List;

/** 一次智能对话从路由到最终回答的统一调用链快照。 */
public record AgentPipelineTrace(List<Stage> stages) {
    public static AgentPipelineTrace of(String route, List<String> tools, List<String> agents) {
        List<Stage> stages = new ArrayList<>();
        stages.add(new Stage("route", "路由", route, "根据问题选择 Skill"));
        stages.add(new Stage("planner", "Planner", "SUCCESS", "拆解任务计划"));
        stages.add(new Stage("tools", "Tools", tools == null || tools.isEmpty() ? "SKIPPED" : "SUCCESS",
            tools == null || tools.isEmpty() ? "本轮没有工具调用" : String.join("、", tools)));
        stages.add(new Stage("retriever", "Retriever", agents != null && agents.contains("RETRIEVER") ? "SUCCESS" : "SKIPPED", "整理 RAG 证据"));
        stages.add(new Stage("evaluator", "Evaluator", agents != null && agents.contains("EVALUATOR") ? "SUCCESS" : "SKIPPED", "评估证据和执行质量"));
        stages.add(new Stage("reviewer", "Reviewer", agents != null && agents.contains("REVIEWER") ? "SUCCESS" : "SKIPPED", "复核最终回答约束"));
        stages.add(new Stage("answer", "Answer", "PENDING", "模型生成最终回答"));
        return new AgentPipelineTrace(List.copyOf(stages));
    }

    public record Stage(String id, String name, String status, String detail) {
    }
}
