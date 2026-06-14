package com.zyagent.job;

import com.zyagent.common.ApiResponse;
import com.zyagent.config.ZyagentProperties;
import com.zyagent.match.JobMatchReport;
import com.zyagent.match.ResumeJobMatcher;
import com.zyagent.match.ResumeProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService jobService;
    private final JobCollectorService collectorService;
    private final ZyagentProperties properties;
    private final ResumeJobMatcher matcher = new ResumeJobMatcher();

    public JobController(JobService jobService, JobCollectorService collectorService, ZyagentProperties properties) {
        this.jobService = jobService;
        this.collectorService = collectorService;
        this.properties = properties;
    }

    @PostMapping("/import-text")
    public ApiResponse<JobPosting> importText(@RequestBody ImportTextRequest request) {
        return ApiResponse.ok(jobService.importText(request.rawText(), request.sourceUrl()));
    }

    @PostMapping("/import-url")
    public ApiResponse<JobPosting> importUrl(@RequestBody ImportUrlRequest request) {
        return ApiResponse.ok(jobService.importUrl(request.url()));
    }

    @GetMapping
    public ApiResponse<List<JobPosting>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(jobService.list(keyword));
    }

    @DeleteMapping("/invalid-collected")
    public ApiResponse<Integer> deleteInvalidCollected() {
        return ApiResponse.ok(jobService.deleteInvalidCollectedJobs());
    }

    @PostMapping("/collect")
    public ApiResponse<JobCollectResult> collectAll() {
        JobCollectResult result = collectorService.collectAll(properties.jobCollect().maxPagesPerSource());
        return ApiResponse.ok(result);
    }

    @GetMapping("/collect/logs")
    public ApiResponse<List<JobCollectLog>> collectLogs() {
        return ApiResponse.ok(collectorService.listLogs());
    }

    @PostMapping("/{jobId}/match-resume")
    public ApiResponse<JobMatchReport> matchResume(@PathVariable String jobId, @RequestBody MatchResumeRequest request) {
        JobPosting posting = jobService.findById(jobId).orElseThrow(() -> new IllegalArgumentException("岗位不存在：" + jobId));
        ResumeProfile resume = new ResumeProfile("request-resume", request.resumeText(), request.skills(), request.projects());
        return ApiResponse.ok(matcher.match(resume, posting));
    }

    public record ImportTextRequest(String rawText, String sourceUrl) {
    }

    public record ImportUrlRequest(String url) {
    }

    public record MatchResumeRequest(String resumeText, List<String> skills, List<String> projects) {
    }
}
