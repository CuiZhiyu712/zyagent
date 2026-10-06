package com.zyagent.modules.job;

import com.zyagent.infrastructure.config.ZyagentProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobCollectScheduler {
    private final JobCollectorService collectorService;
    private final ZyagentProperties properties;

    public JobCollectScheduler(JobCollectorService collectorService, ZyagentProperties properties) {
        this.collectorService = collectorService;
        this.properties = properties;
    }

    @Scheduled(cron = "${zyagent.job-collect.cron:0 0 8 * * ?}")
    public void collectDaily() {
        if (!properties.jobCollect().enabled()) {
            return;
        }
        collectorService.collectAll(properties.jobCollect().maxPagesPerSource());
    }
}
