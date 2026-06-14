package com.zyagent.job;

public record JobCrawlItem(
    String text,
    String url,
    String rawListText,
    String rawDetailText,
    boolean detailFetched
) {
    public JobCrawlItem(String text, String url) {
        this(text, url, text, "", false);
    }
}
