package com.zyagent.modules.job;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record JobPosting(
    String id,
    String company,
    String title,
    String city,
    String jobType,
    String direction,
    List<String> responsibilities,
    List<String> requirements,
    List<String> bonusItems,
    List<String> skills,
    String sourceUrl,
    String sourceName,
    String externalId,
    String contentHash,
    LocalDate publishedDate,
    LocalDateTime collectedAt,
    LocalDateTime lastSeenAt,
    String applicationStatus,
    String rawText
) {
    public JobPosting(
        String id,
        String company,
        String title,
        String city,
        String jobType,
        String direction,
        List<String> responsibilities,
        List<String> requirements,
        List<String> bonusItems,
        List<String> skills,
        String sourceUrl,
        LocalDate publishedDate,
        LocalDateTime collectedAt,
        String applicationStatus,
        String rawText
    ) {
        this(id, company, title, city, jobType, direction, responsibilities, requirements, bonusItems, skills, sourceUrl, sourceNameFrom(sourceUrl), null, null, publishedDate, collectedAt, collectedAt, applicationStatus, rawText);
    }

    public JobPosting withCollectMetadata(String sourceName, String externalId, String contentHash) {
        return new JobPosting(id, company, title, city, jobType, direction, responsibilities, requirements, bonusItems, skills, sourceUrl, sourceName, externalId, contentHash, publishedDate, collectedAt, LocalDateTime.now(), applicationStatus, rawText);
    }

    private static String sourceNameFrom(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return "manual";
        }
        if (sourceUrl.startsWith("mock://")) {
            return "mock";
        }
        if (sourceUrl.startsWith("manual://")) {
            return "manual";
        }
        return "url";
    }
}
