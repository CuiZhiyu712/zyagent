package com.zyagent.job;

import java.util.List;

public record BossCollectRequest(
    String city,
    List<String> keywords,
    Integer maxPages,
    Integer maxJobs,
    Boolean fetchDetail
) {
}
