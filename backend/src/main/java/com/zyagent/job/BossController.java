package com.zyagent.job;

import com.zyagent.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/boss")
public class BossController {
    private final BossJobCollector collector;

    public BossController(BossJobCollector collector) {
        this.collector = collector;
    }

    @PostMapping("/session/open")
    public ApiResponse<BossSessionStatus> openSession() {
        try {
            return ApiResponse.ok(collector.openSession());
        } catch (RuntimeException ex) {
            return ApiResponse.fail(ex.getMessage());
        }
    }

    @GetMapping("/session/status")
    public ApiResponse<BossSessionStatus> status() {
        try {
            return ApiResponse.ok(collector.status());
        } catch (RuntimeException ex) {
            return ApiResponse.fail(ex.getMessage());
        }
    }

    @PostMapping("/collect")
    public ApiResponse<JobCollectResult> collect(@RequestBody(required = false) BossCollectRequest request) {
        try {
            return ApiResponse.ok(collector.collect(request));
        } catch (RuntimeException ex) {
            return ApiResponse.fail(ex.getMessage());
        }
    }
}
