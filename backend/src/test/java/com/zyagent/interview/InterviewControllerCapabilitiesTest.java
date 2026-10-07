package com.zyagent.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class InterviewControllerCapabilitiesTest {
    @Test
    void exposesUnavailableLlmCapabilitiesWithoutLoadingAnInterviewSession() throws Exception {
        InterviewService interviewService = mock(InterviewService.class);

        mockMvc(unavailableLlmAgent(), interviewService)
            .perform(get("/api/interviews/capabilities").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("ok"))
            .andExpect(jsonPath("$.data.provider").value("llm"))
            .andExpect(jsonPath("$.data.available").value(false))
            .andExpect(jsonPath("$.data.label").value("DeepSeek AI 面试官"));

        verifyNoInteractions(interviewService);
    }

    @Test
    void exposesRuleProviderCapabilitiesWithoutLoadingAnInterviewSession() throws Exception {
        InterviewService interviewService = mock(InterviewService.class);

        mockMvc(new RuleBasedInterviewAgent(new ObjectMapper()), interviewService)
            .perform(get("/api/interviews/capabilities").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("ok"))
            .andExpect(jsonPath("$.data.provider").value("rule_demo"))
            .andExpect(jsonPath("$.data.available").value(true))
            .andExpect(jsonPath("$.data.label").value("规则演示模式"));

        verifyNoInteractions(interviewService);
    }

    private static MockMvc mockMvc(InterviewAgent agent, InterviewService interviewService) {
        InterviewSession session = InterviewSession.create("owner", "job", null, "项目深挖", "中等");
        when(interviewService.get("capabilities")).thenReturn(session);
        when(interviewService.turns(session.id())).thenReturn(List.of());
        InterviewAgentService interviewAgentService = new InterviewAgentService(agent, new ObjectMapper(), null);
        return standaloneSetup(new InterviewController(interviewService, interviewAgentService)).build();
    }

    private static InterviewAgent unavailableLlmAgent() {
        return new InterviewAgent() {
            @Override
            public String provider() {
                return "llm";
            }

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String label() {
                return "DeepSeek AI 面试官";
            }

            @Override
            public String nextQuestion(InterviewSession session, java.util.List<InterviewTurn> history) {
                return "{}";
            }

            @Override
            public String evaluateAnswer(InterviewSession session, String question, String answer,
                                         boolean allowFollowUp) {
                return "{}";
            }
        };
    }
}
