package com.zyagent.job;

public record BossRawJob(
    String title,
    String company,
    String city,
    String salary,
    String detailUrl,
    String listText
) {
}
