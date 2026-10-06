package com.zyagent.modules.agent.port;

import java.util.List;

public interface InterviewPracticePort {
    List<String> generateQuestions(String jobText);
}
