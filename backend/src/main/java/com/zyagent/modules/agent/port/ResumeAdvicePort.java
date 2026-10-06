package com.zyagent.modules.agent.port;

import java.util.List;

public interface ResumeAdvicePort {
    List<String> analyzeProject(String projectText);
    List<String> suggest(String resumeText);
}
