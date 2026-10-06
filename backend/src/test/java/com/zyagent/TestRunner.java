package com.zyagent;

import com.zyagent.modules.knowledgebase.TextChunkerTest;
import com.zyagent.modules.knowledgebase.DocumentSearchResponseTest;
import com.zyagent.modules.knowledgebase.DocumentDeletionTest;
import com.zyagent.infrastructure.ai.AiChatServiceTest;
import com.zyagent.modules.chat.StreamChunkerTest;
import com.zyagent.modules.agent.AgentPlanTest;
import com.zyagent.modules.agent.AgentObservabilityTest;
import com.zyagent.modules.agent.ChatMemoryContextTest;
import com.zyagent.modules.agent.MultiAgentCollaborationTest;
import com.zyagent.modules.agent.PromptAdvisorChainTest;
import com.zyagent.modules.job.JobDescriptionParserTest;
import com.zyagent.modules.job.BossJobParserTest;
import com.zyagent.modules.job.JobCollectorTest;
import com.zyagent.modules.job.MeituanJobDetailCrawlerTest;
import com.zyagent.modules.resume.match.ResumeJobMatcherTest;
import com.zyagent.infrastructure.ai.structured.StructuredOutputTest;
import com.zyagent.modules.agent.tool.AgentToolServiceTest;
import com.zyagent.modules.agent.skill.SkillRouterTest;
import com.zyagent.modules.agent.skill.SkillRouterDecisionTest;
import com.zyagent.modules.agent.skill.ContextIntentClassifierTest;
import com.zyagent.modules.agent.tool.ToolRegistryTest;

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
        PromptAdvisorChainTest.run();
        StructuredOutputTest.run();
        AgentToolServiceTest.run();
        SkillRouterTest.run();
        SkillRouterDecisionTest.run();
        ContextIntentClassifierTest.run();
        StreamChunkerTest.run();
        System.out.println("All core tests passed.");
    }
}
