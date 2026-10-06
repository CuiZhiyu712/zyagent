package com.zyagent.modules.review;

import com.zyagent.modules.agent.AgentMode;
import com.zyagent.modules.agent.AgentOrchestrator;
import com.zyagent.modules.agent.AgentResult;
import com.zyagent.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final AgentOrchestrator orchestrator;

    public ReviewController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ApiResponse<AgentResult> create(@RequestBody ReviewRequest request) {
        return ApiResponse.ok(orchestrator.execute(AgentMode.REVIEW_COACH, request.content()));
    }

    public record ReviewRequest(String content) {
    }
}
