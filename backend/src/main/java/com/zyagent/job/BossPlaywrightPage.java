package com.zyagent.job;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class BossPlaywrightPage {
    private static final String SEARCH_URL = "https://www.zhipin.com/web/geek/job?query={keyword}&city={city}&page={page}";
    private final Page page;
    private final BossJobParser parser;
    private final Random random = new Random();

    BossPlaywrightPage(Page page, BossJobParser parser) {
        this.page = page;
        this.parser = parser;
    }

    List<JobCrawlItem> collect(BossCollectRequest request) {
        List<JobCrawlItem> items = new ArrayList<>();
        int maxJobs = request.maxJobs() == null || request.maxJobs() <= 0 ? 60 : request.maxJobs();
        int maxPages = request.maxPages() == null || request.maxPages() <= 0 ? 3 : request.maxPages();
        boolean fetchDetail = request.fetchDetail() == null || request.fetchDetail();
        for (String keyword : request.keywords()) {
            for (int pageNo = 1; pageNo <= maxPages && items.size() < maxJobs; pageNo++) {
                page.navigate(searchUrl(keyword, request.city(), pageNo), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                waitPolitely();
                String listHtml = page.content();
                if (parser.needsLoginOrVerify(listHtml)) {
                    throw new IllegalStateException("需要在浏览器中完成登录或验证");
                }
                for (BossRawJob raw : parser.parseList(listHtml, page.url())) {
                    if (items.size() >= maxJobs) {
                        break;
                    }
                    String detailHtml = "";
                    if (fetchDetail) {
                        detailHtml = fetchDetail(raw.detailUrl());
                    }
                    items.add(parser.toCrawlItem(raw, detailHtml));
                }
            }
        }
        return items;
    }

    private String fetchDetail(String url) {
        try {
            Page detail = page.context().newPage();
            detail.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            waitPolitely();
            String html = detail.content();
            detail.close();
            return parser.needsLoginOrVerify(html) ? "" : html;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String searchUrl(String keyword, String city, int pageNo) {
        return SEARCH_URL
            .replace("{keyword}", encode(keyword))
            .replace("{city}", encode(city == null || city.isBlank() ? "北京" : city))
            .replace("{page}", String.valueOf(pageNo));
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void waitPolitely() {
        page.waitForTimeout(2_000 + random.nextInt(2_001));
    }
}
