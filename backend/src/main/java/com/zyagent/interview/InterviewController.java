package com.zyagent.interview;

import com.zyagent.agent.AgentMode;
import com.zyagent.agent.AgentOrchestrator;
import com.zyagent.agent.AgentResult;
import com.zyagent.common.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {
    private final AgentOrchestrator orchestrator;

    public InterviewController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/simulate")
    public ApiResponse<InterviewSessionResponse> simulate(@RequestBody SimulateInterviewRequest request) {
        String sessionId = UUID.randomUUID().toString();
        AgentResult result = orchestrator.execute(AgentMode.INTERVIEWER, "基于岗位 " + request.jobId() + " 进行" + request.interviewType() + "模拟面试，难度：" + request.difficulty());
        return ApiResponse.ok(new InterviewSessionResponse(sessionId, result.answer()));
    }

    @PostMapping("/{sessionId}/answer")
    public ApiResponse<AgentResult> answer(@PathVariable String sessionId, @RequestBody InterviewAnswerRequest request) {
        return ApiResponse.ok(orchestrator.execute(AgentMode.INTERVIEWER, "会话 " + sessionId + " 的候选人回答：" + request.answer() + "。请追问并评分。"));
    }

    public record SimulateInterviewRequest(String jobId, String interviewType, String difficulty) {
    }

    public record InterviewAnswerRequest(String answer) {
    }

    public record InterviewSessionResponse(String sessionId, String firstQuestion) {
    }
}
