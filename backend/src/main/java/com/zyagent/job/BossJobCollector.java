package com.zyagent.job;

import com.zyagent.config.ZyagentProperties;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class BossJobCollector {
    private static final JobSource BOSS_SOURCE = new JobSource(
        "boss",
        "BOSS",
        "BOSS直聘",
        "https://www.zhipin.com",
        true,
        "Java,后端,实习,AI,Agent",
        null,
        "NEW",
        "BOSS_BROWSER",
        null,
        null,
        null,
        true,
        60
    );

    private final ZyagentProperties properties;
    private final BossBrowserSessionService browserSession;
    private final JobCollectorService collectorService;

    public BossJobCollector(ZyagentProperties properties, BossBrowserSessionService browserSession, JobCollectorService collectorService) {
        this.properties = properties;
        this.browserSession = browserSession;
        this.collectorService = collectorService;
    }

    public BossSessionStatus openSession() {
        return browserSession.open();
    }

    public BossSessionStatus status() {
        return browserSession.status();
    }

    public JobCollectResult collect(BossCollectRequest request) {
        if (!properties.bossCollect().enabled()) {
            throw new IllegalStateException("BOSS 采集未启用");
        }
        BossCollectRequest normalized = normalize(request);
        List<JobCrawlItem> items = browserSession.collectItems(normalized);
        return collectorService.collectItems(BOSS_SOURCE, items, 0);
    }

    private BossCollectRequest normalize(BossCollectRequest request) {
        ZyagentProperties.BossCollect boss = properties.bossCollect();
        String city = request == null || request.city() == null || request.city().isBlank() ? boss.city() : request.city();
        List<String> keywords = request == null || request.keywords() == null || request.keywords().isEmpty()
            ? parseKeywords(boss.keywords())
            : request.keywords();
        int maxPages = request == null || request.maxPages() == null || request.maxPages() <= 0 ? boss.maxPages() : request.maxPages();
        int maxJobs = request == null || request.maxJobs() == null || request.maxJobs() <= 0 ? boss.maxJobs() : request.maxJobs();
        boolean fetchDetail = request == null || request.fetchDetail() == null || request.fetchDetail();
        return new BossCollectRequest(city, keywords, maxPages, maxJobs, fetchDetail);
    }

    private List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of("Java 后端 实习", "AI Agent 后端 实习");
        }
        return Arrays.stream(keywords.split("[,，]"))
            .map(String::strip)
            .filter(value -> !value.isBlank())
            .toList();
    }
}
