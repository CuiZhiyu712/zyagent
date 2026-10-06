package com.zyagent.modules.job;

import com.zyagent.common.ApiResponse;
import com.zyagent.infrastructure.config.ZyagentProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/job-sources")
public class JobSourceController {
    private final JobCollectorService collectorService;
    private final ZyagentProperties properties;

    public JobSourceController(JobCollectorService collectorService, ZyagentProperties properties) {
        this.collectorService = collectorService;
        this.properties = properties;
    }

    @GetMapping
    public ApiResponse<List<JobSource>> list() {
        return ApiResponse.ok(collectorService.listSources());
    }

    @PostMapping
    public ApiResponse<JobSource> create(@RequestBody SaveJobSourceRequest request) {
        String id = request.id() == null || request.id().isBlank() ? UUID.randomUUID().toString() : request.id();
        return ApiResponse.ok(collectorService.saveSource(toSource(id, request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<JobSource> update(@PathVariable String id, @RequestBody SaveJobSourceRequest request) {
        return ApiResponse.ok(collectorService.saveSource(toSource(id, request)));
    }

    @PostMapping("/{id}/collect")
    public ApiResponse<JobCollectResult> collect(@PathVariable String id) {
        return ApiResponse.ok(collectorService.collect(id, properties.jobCollect().maxPagesPerSource()));
    }

    private JobSource toSource(String id, SaveJobSourceRequest request) {
        return new JobSource(
            id,
            request.company(),
            request.name(),
            request.url(),
            request.enabled(),
            request.keywords() == null || request.keywords().isBlank() ? properties.jobCollect().keywords() : request.keywords(),
            null,
            "UPDATED",
            request.sourceType(),
            request.searchUrlTemplate(),
            request.listItemSelector(),
            request.detailUrlSelector(),
            request.enabledDetailFetch(),
            request.maxDetailPages()
        );
    }

    public record SaveJobSourceRequest(
        String id,
        String company,
        String name,
        String url,
        boolean enabled,
        String keywords,
        String sourceType,
        String searchUrlTemplate,
        String listItemSelector,
        String detailUrlSelector,
        boolean enabledDetailFetch,
        int maxDetailPages
    ) {
    }
}
