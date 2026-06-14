package com.zyagent.job;

import com.zyagent.config.ZyagentProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class JobCollectorConfig {
    @Bean
    JobCrawler jobCrawler(ZyagentProperties properties, MeituanJobDetailCrawler meituanJobDetailCrawler) {
        return new JobCrawler(new JsoupPageFetcher(properties.jobCollect().timeoutSeconds()), meituanJobDetailCrawler);
    }

    @Bean
    ApplicationRunner seedJobSources(JobCollectorService collectorService, ZyagentProperties properties) {
        return ignored -> {
            collectorService.deleteInvalidCollectedJobs();
            if (!collectorService.listSources().isEmpty()) {
                return;
            }
            for (JobSource source : defaultSources(properties.jobCollect().keywords())) {
                collectorService.saveSource(source);
            }
        };
    }

    private List<JobSource> defaultSources(String keywords) {
        return List.of(
            source("aliyun", "阿里", "阿里招聘公开页", "https://talent.alibaba.com/jobs/search", keywords),
            source("tencent", "腾讯", "腾讯招聘公开页", "https://careers.tencent.com/search.html", keywords),
            source("bytedance", "字节", "字节招聘公开页", "https://jobs.bytedance.com/campus/position", keywords),
            source("meituan", "美团", "美团招聘公开页", "https://zhaopin.meituan.com/web/position", keywords),
            source("jd", "京东", "京东招聘公开页", "https://campus.jd.com/#/jobs", keywords),
            source("baidu", "百度", "百度招聘公开页", "https://talent.baidu.com/jobs/list", keywords),
            source("kuaishou", "快手", "快手招聘公开页", "https://zhaopin.kuaishou.cn/recruit/e/#/official/social", keywords),
            source("xiaomi", "小米", "小米招聘公开页", "https://hr.xiaomi.com/job-list", keywords),
            source("huawei", "华为", "华为招聘公开页", "https://career.huawei.com/reccampportal/portal5/index.html", keywords)
        );
    }

    private JobSource source(String id, String company, String name, String url, String keywords) {
        return new JobSource(id, company, name, url, true, keywords, null, "NEW", "SEARCH_PAGE", null, null, null, true, 12);
    }
}
