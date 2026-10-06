package com.zyagent.modules.interview;

import com.zyagent.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {
    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    /** 创建会话并生成首题。 */
    @PostMapping
    public ApiResponse<InterviewDetails> create(@RequestBody CreateInterviewRequest request) {
        InterviewSession session = interviewService.start(
            request.jobId(), request.jdSnapshot(), request.interviewType(), request.difficulty());
        return ApiResponse.ok(details(session));
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<InterviewDetails> get(@PathVariable String sessionId) {
        return ApiResponse.ok(details(interviewService.get(sessionId)));
    }

    /** 提交当前轮回答，返回本轮评价/追问与更新后的会话状态。 */
    @PostMapping("/{sessionId}/turns")
    public ApiResponse<InterviewTurnResult> submitTurn(@PathVariable String sessionId,
                                                       @RequestBody SubmitTurnRequest request) {
        InterviewTurn turn = interviewService.submitTurn(sessionId, request.answer(), request.requestId());
        return ApiResponse.ok(new InterviewTurnResult(turn, interviewService.get(sessionId)));
    }

    @PostMapping("/{sessionId}/complete")
    public ApiResponse<InterviewSession> complete(@PathVariable String sessionId) {
        return ApiResponse.ok(interviewService.complete(sessionId));
    }

    @PostMapping("/{sessionId}/abort")
    public ApiResponse<InterviewSession> abort(@PathVariable String sessionId) {
        return ApiResponse.ok(interviewService.abort(sessionId));
    }

    @GetMapping
    public ApiResponse<InterviewService.SessionPage> sessions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(interviewService.sessions(page, size));
    }

    /** 兼容旧接口：创建会话并返回首题。 */
    @PostMapping("/simulate")
    public ApiResponse<InterviewSessionResponse> simulate(@RequestBody SimulateInterviewRequest request) {
        InterviewSession session = interviewService.start(null, null, request.interviewType(), request.difficulty());
        String firstQuestion = interviewService.turns(session.id()).stream()
            .findFirst()
            .map(InterviewTurn::question)
            .orElse("");
        return ApiResponse.ok(new InterviewSessionResponse(session.id(), firstQuestion));
    }

    /** 兼容旧接口：等价于提交当前轮回答。 */
    @PostMapping("/{sessionId}/answer")
    public ApiResponse<InterviewTurn> answer(@PathVariable String sessionId,
                                             @RequestBody InterviewAnswerRequest request) {
        return ApiResponse.ok(interviewService.submitTurn(sessionId, request.answer(), null));
    }

    private InterviewDetails details(InterviewSession session) {
        return new InterviewDetails(session, interviewService.turns(session.id()));
    }

    public record CreateInterviewRequest(String jobId, String jdSnapshot, String interviewType, String difficulty) {
    }

    public record SubmitTurnRequest(String answer, String requestId) {
    }

    public record InterviewDetails(InterviewSession session, List<InterviewTurn> turns) {
    }

    public record InterviewTurnResult(InterviewTurn turn, InterviewSession session) {
    }

    public record SimulateInterviewRequest(String jobId, String interviewType, String difficulty) {
    }

    public record InterviewSessionResponse(String sessionId, String firstQuestion) {
    }

    public record InterviewAnswerRequest(String answer) {
    }
}
