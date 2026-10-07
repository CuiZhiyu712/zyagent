package com.zyagent.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyagent.config.PropertiesConfig;
import com.zyagent.config.ZyagentProperties;
import com.zyagent.interview.InterviewAgent;
import com.zyagent.interview.InterviewAgentService;
import com.zyagent.interview.RuleBasedInterviewAgent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewAgentProviderContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(ProviderConfiguration.class)
        .withPropertyValues("DEEPSEEK_API_KEY=provider-test-key");

    @Test
    void missingProviderPropertySelectsLlmAgent() {
        new ApplicationContextRunner()
            .withUserConfiguration(ProviderConfiguration.class)
            .withPropertyValues("DEEPSEEK_API_KEY=provider-test-key")
            .run(context -> {
                assertFalse(context.getStartupFailure() != null,
                    () -> "missing provider context failed: " + context.getStartupFailure());
                assertEquals(1, context.getBeansOfType(InterviewAgent.class).size());
                assertEquals("LlmInterviewAgent", context.getBean(InterviewAgent.class).getClass().getSimpleName());
            });
    }

    @Test
    void defaultAndLlmProviderSelectOnlyTheLlmAgentAndBindInterviewProperties() {
        contextRunner.withPropertyValues("ZYAGENT_INTERVIEW_PROVIDER=llm").run(context -> {
            assertFalse(context.getStartupFailure() != null,
                () -> "default provider context failed: " + context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(InterviewAgent.class).size());
            assertEquals("LlmInterviewAgent", context.getBean(InterviewAgent.class).getClass().getSimpleName());

            ZyagentProperties.Interview interview = context.getBean(ZyagentProperties.class).interview();
            assertNotNull(interview);
            assertEquals("llm", interview.provider());
            assertEquals(8, interview.maxTurns());
        });

        contextRunner.withPropertyValues(
            "zyagent.interview.provider=llm",
            "zyagent.interview.max-turns=5",
            "zyagent.interview.max-follow-ups=2",
            "zyagent.interview.model-timeout-ms=1234").run(context -> {
            assertFalse(context.getStartupFailure() != null,
                () -> "explicit llm provider context failed: " + context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(InterviewAgent.class).size());
            assertEquals("LlmInterviewAgent", context.getBean(InterviewAgent.class).getClass().getSimpleName());

            ZyagentProperties.Interview interview = context.getBean(ZyagentProperties.class).interview();
            assertEquals(5, interview.maxTurns());
            assertEquals(2, interview.maxFollowUps());
            assertEquals(1234L, interview.modelTimeoutMs());
        });
    }

    @Test
    void ruleProviderSelectsOnlyTheRuleAgent() {
        contextRunner.withPropertyValues("ZYAGENT_INTERVIEW_PROVIDER=rule").run(context -> {
            assertFalse(context.getStartupFailure() != null,
                () -> "rule provider context failed: " + context.getStartupFailure());
            assertEquals(1, context.getBeansOfType(InterviewAgent.class).size());
            assertEquals("RuleBasedInterviewAgent", context.getBean(InterviewAgent.class).getClass().getSimpleName());
            assertEquals("rule", context.getBean(ZyagentProperties.class).interview().provider());
        });
    }

    @Test
    void unknownProviderFailsWhenTheServiceRequiresAnInterviewAgent() {
        contextRunner.withPropertyValues("ZYAGENT_INTERVIEW_PROVIDER=unknown").run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(rootCause(context.getStartupFailure()).getMessage().contains("InterviewAgent"));
        });
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
        basePackages = "com.zyagent.ai",
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.zyagent\\.ai\\.LlmInterviewAgent"))
    @Import({PropertiesConfig.class, RuleBasedInterviewAgent.class, InterviewAgentService.class})
    static class ProviderConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AiChatClient aiChatClient() {
            return new AiChatClient() {
                @Override
                public String complete(String systemPrompt, String userPrompt) {
                    throw new AssertionError("provider context test must not call a model");
                }

                @Override
                public void stream(String systemPrompt, String userPrompt, TokenHandler handler) {
                    throw new AssertionError("provider context test must not call a model");
                }
            };
        }
    }
}
