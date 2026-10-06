package com.zyagent.modules.agent.port;

import com.zyagent.modules.job.JobPosting;
import com.zyagent.modules.resume.match.JobMatchReport;

public interface JobAnalysisPort {
    JobPosting parse(String rawText);
    JobMatchReport match(String resumeText, String jobText);
}
