package com.zyagent.modules.job;

import com.zyagent.infrastructure.storage.JobPostingRepository;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class JobService {
    private final JobDescriptionParser parser;
    private final JobPostingRepository repository;
    private final MeituanJobDetailCrawler meituanJobDetailCrawler;
    private final List<JobPosting> jobs = new ArrayList<>();

    public JobService(
        JobDescriptionParser parser,
        ObjectProvider<JobPostingRepository> repository,
        ObjectProvider<MeituanJobDetailCrawler> meituanJobDetailCrawler
    ) {
        this.parser = parser;
        this.repository = repository.getIfAvailable();
        this.meituanJobDetailCrawler = meituanJobDetailCrawler.getIfAvailable();
        jobs.addAll(MockJobSource.seed());
        seedRepository();
    }

    public JobPosting importText(String rawText, String sourceUrl) {
        JobPosting posting = parser.parse(rawText, sourceUrl);
        jobs.add(posting);
        if (repository != null) {
            repository.save(posting);
        }
        return posting;
    }

    public JobPosting importUrl(String url) {
        try {
            if (meituanJobDetailCrawler != null && meituanJobDetailCrawler.supports(url)) {
                JobCrawlItem item = meituanJobDetailCrawler.crawl(url);
                return importText(item.text(), item.url());
            }
            String text = Jsoup.connect(url)
                .timeout((int) Duration.ofSeconds(5).toMillis())
                .userAgent("zyagent/0.1")
                .get()
                .body()
                .text();
            return importText(text, url);
        } catch (IOException ex) {
            throw new IllegalArgumentException("公开岗位页面解析失败，请改用手动导入 JD。原因：" + ex.getMessage());
        }
    }

    public List<JobPosting> list(String keyword) {
        if (repository != null) {
            try {
                return repository.findAll(keyword);
            } catch (RuntimeException ignored) {
                return memoryList(keyword);
            }
        }
        return memoryList(keyword);
    }

    public Optional<JobPosting> findById(String id) {
        if (repository != null) {
            try {
                Optional<JobPosting> posting = repository.findById(id);
                if (posting.isPresent()) {
                    return posting;
                }
            } catch (RuntimeException ignored) {
                return jobs.stream().filter(job -> job.id().equals(id)).findFirst();
            }
        }
        return jobs.stream().filter(job -> job.id().equals(id)).findFirst();
    }

    public int deleteInvalidCollectedJobs() {
        if (repository == null) {
            jobs.removeIf(job -> !List.of("mock", "manual", "url").contains(job.sourceName())
                && ("未标注".equals(job.company()) || "未标注".equals(job.title()) || job.rawText().toLowerCase().contains("enable javascript to run this app")));
            return 0;
        }
        return repository.deleteInvalidCollected();
    }

    private List<JobPosting> memoryList(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.copyOf(jobs);
        }
        String lower = keyword.toLowerCase();
        return jobs.stream()
            .filter(job -> (job.company() + job.title() + job.city() + job.skills()).toLowerCase().contains(lower))
            .toList();
    }

    private void seedRepository() {
        if (repository == null) {
            return;
        }
        try {
            if (repository.count() == 0) {
                jobs.forEach(repository::save);
            }
        } catch (RuntimeException ignored) {
            // Keep in-memory mock jobs available when MySQL is not ready.
        }
    }
}
