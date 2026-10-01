package com.zyagent;

import com.zyagent.document.TextChunkerTest;
import com.zyagent.document.DocumentSearchResponseTest;
import com.zyagent.document.DocumentDeletionTest;
import com.zyagent.ai.AiChatServiceTest;
import com.zyagent.chat.StreamChunkerTest;
import com.zyagent.agent.AgentPlanTest;
import com.zyagent.agent.AgentObservabilityTest;
import com.zyagent.agent.ChatMemoryContextTest;
import com.zyagent.agent.MultiAgentCollaborationTest;
import com.zyagent.job.JobDescriptionParserTest;
import com.zyagent.job.BossJobParserTest;
import com.zyagent.job.JobCollectorTest;
import com.zyagent.job.MeituanJobDetailCrawlerTest;
import com.zyagent.match.ResumeJobMatcherTest;
import com.zyagent.structured.StructuredOutputTest;
import com.zyagent.tool.AgentToolServiceTest;
import com.zyagent.skill.SkillRouterTest;
import com.zyagent.skill.SkillRouterDecisionTest;
import com.zyagent.tool.ToolRegistryTest;

public class TestRunner {
    public static void main(String[] args) {
        JobDescriptionParserTest.run();
        BossJobParserTest.run();
        JobCollectorTest.run();
        MeituanJobDetailCrawlerTest.run();
        ResumeJobMatcherTest.run();
        ToolRegistryTest.run();
        TextChunkerTest.run();
        DocumentSearchResponseTest.run();
        DocumentDeletionTest.run();
        AiChatServiceTest.run();
        AgentPlanTest.run();
        AgentObservabilityTest.run();
        MultiAgentCollaborationTest.run();
        ChatMemoryContextTest.run();
        StructuredOutputTest.run();
        AgentToolServiceTest.run();
        SkillRouterTest.run();
        SkillRouterDecisionTest.run();
        StreamChunkerTest.run();
        System.out.println("All core tests passed.");
    }
}
