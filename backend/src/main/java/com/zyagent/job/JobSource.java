package com.zyagent.job;

import java.time.LocalDateTime;

public record JobSource(
    String id,
    String company,
    String name,
    String url,
    boolean enabled,
    String keywords,
    LocalDateTime lastCollectedAt,
    String lastStatus,
    String sourceType,
    String searchUrlTemplate,
    String listItemSelector,
    String detailUrlSelector,
    boolean enabledDetailFetch,
    int maxDetailPages
) {
    public JobSource(
        String id,
        String company,
        String name,
        String url,
        boolean enabled,
        String keywords,
        LocalDateTime lastCollectedAt,
        String lastStatus
    ) {
        this(id, company, name, url, enabled, keywords, lastCollectedAt, lastStatus, "STATIC_HTML", null, null, null, false, 0);
    }

    public String normalizedSourceType() {
        return sourceType == null || sourceType.isBlank() ? "STATIC_HTML" : sourceType;
    }

    public int normalizedMaxDetailPages() {
        return maxDetailPages <= 0 ? 10 : maxDetailPages;
    }
}
