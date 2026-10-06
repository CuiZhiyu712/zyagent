package com.zyagent.modules.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPipelineTraceTest {
    @Test
    void keepsTheCompleteConversationPipelineOrder() {
        AgentPipelineTrace trace = AgentPipelineTrace.of("JOB_ANALYST", List.of("parse_job_description", "search_personal_knowledge"),
            List.of("PLANNER", "RETRIEVER", "EVALUATOR", "REVIEWER"));

        assertEquals(List.of("route", "planner", "tools", "retriever", "evaluator", "reviewer", "answer"),
            trace.stages().stream().map(AgentPipelineTrace.Stage::id).toList());
    }
}
