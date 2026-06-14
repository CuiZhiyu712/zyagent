package com.zyagent.job;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.time.Duration;

public class JsoupPageFetcher implements PageFetcher {
    private final int timeoutSeconds;

    public JsoupPageFetcher(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String fetch(String url) throws IOException {
        return Jsoup.connect(url)
            .timeout((int) Duration.ofSeconds(timeoutSeconds).toMillis())
            .userAgent("zyagent-job-collector/0.1 (+personal job assistant)")
            .get()
            .html();
    }
}
