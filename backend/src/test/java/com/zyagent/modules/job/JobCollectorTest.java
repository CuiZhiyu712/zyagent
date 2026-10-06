package com.zyagent.modules.job;

import java.util.List;

public class JobCollectorTest {
    public static void run() {
        extractsKeywordMatchedJobWithApplyLinkFromHtml();
        expandsSearchUrlTemplateBeforeCrawling();
        fetchesDetailPageWhenListItemOnlyContainsLink();
        ignoresJavascriptShellPage();
        countsInvalidParsedJobAsSkipped();
        countsDuplicateCollectedJobAsUpdate();
    }

    private static void expandsSearchUrlTemplateBeforeCrawling() {
        List<String> fetchedUrls = new java.util.ArrayList<>();
        JobCrawler crawler = new JobCrawler(url -> {
            fetchedUrls.add(url);
            return """
                <html><body>
                  <article>
                    <a href="/jobs/1001">Java 后端开发实习生</a>
                    公司：示例大厂
                    城市：北京
                    任职要求：熟悉 Java、Spring Boot、Redis、MySQL。
                  </article>
                </body></html>
                """;
        });

        JobSource source = new JobSource(
            "source-search",
            "示例大厂",
            "示例搜索页",
            "https://jobs.example.com",
            true,
            "Java,后端",
            null,
            null,
            "SEARCH_PAGE",
            "https://jobs.example.com/search?keyword={keyword}&page={page}",
            "article",
            "a[href]",
            true,
            3
        );

        List<JobCrawlItem> items = crawler.crawlItems(source, 1);

        TestAssertions.equals("https://jobs.example.com/search?keyword=Java&page=1", fetchedUrls.get(0), "search template url");
        TestAssertions.equals(1, items.size(), "search item count");
    }

    private static void fetchesDetailPageWhenListItemOnlyContainsLink() {
        JobCrawler crawler = new JobCrawler(url -> {
            if (url.endsWith("/jobs/1001")) {
                return """
                    <html><body>
                      公司：示例大厂
                      岗位：Java 后端开发实习生
                      城市：北京
                      岗位类型：实习
                      任职要求：熟悉 Java、Spring Boot、Redis、MySQL，了解大模型 Agent 应用。
                    </body></html>
                    """;
            }
            return """
                <html><body>
                  <article><a href="/jobs/1001">查看岗位详情</a></article>
                </body></html>
                """;
        });

        JobSource source = new JobSource(
            "source-detail",
            "示例大厂",
            "示例详情页",
            "https://jobs.example.com/list",
            true,
            "Java,后端",
            null,
            null,
            "SEARCH_PAGE",
            null,
            "article",
            "a[href]",
            true,
            3
        );

        List<JobCrawlItem> items = crawler.crawlItems(source, 3);

        TestAssertions.equals(1, items.size(), "detail fetched item count");
        TestAssertions.isTrue(items.get(0).text().contains("Java 后端开发实习生"), "detail text contains title");
        TestAssertions.equals("https://jobs.example.com/jobs/1001", items.get(0).url(), "detail source url");
        TestAssertions.isTrue(items.get(0).detailFetched(), "detail fetch marker");
    }

    private static void extractsKeywordMatchedJobWithApplyLinkFromHtml() {
        JobCrawler crawler = new JobCrawler(url -> """
            <html><body>
              <article>
                <a href="/apply/1001"><h2>Java 后端开发实习生</h2></a>
                <p>公司：示例大厂</p>
                <p>城市：北京</p>
                <p>任职要求：熟悉 Java、Spring Boot、Redis、MySQL，了解大模型 agent 应用。</p>
              </article>
              <article><h2>设计师</h2><p>不包含目标关键词</p></article>
            </body></html>
            """);

        List<JobCrawlItem> items = crawler.crawlItems(
            new JobSource("source-1", "示例大厂", "示例招聘", "https://jobs.example.com/list", true, "Java,后端,agent", null, null),
            3
        );

        TestAssertions.equals(1, items.size(), "matched item count");
        TestAssertions.isTrue(items.get(0).text().contains("Java 后端开发实习生"), "matched text contains title");
        TestAssertions.equals("https://jobs.example.com/apply/1001", items.get(0).url(), "apply link");
    }

    private static void ignoresJavascriptShellPage() {
        JobCrawler crawler = new JobCrawler(url -> "<html><body>You need to enable JavaScript to run this app.</body></html>");

        List<JobCrawlItem> items = crawler.crawlItems(
            new JobSource("source-js", "快手", "快手招聘公开页", "https://zhaopin.example.com", true, "Java,后端,agent", null, null),
            3
        );

        TestAssertions.equals(0, items.size(), "js shell item count");
    }

    private static void countsInvalidParsedJobAsSkipped() {
        InMemoryJobCollectorStore store = new InMemoryJobCollectorStore();
        JobDescriptionParser parser = new JobDescriptionParser();
        JobCrawler crawler = new JobCrawler(url -> """
            <html><body><article>
              <h2>JavaScript 前端页面</h2>
              <p>You need to enable JavaScript to run this app.</p>
            </article></body></html>
            """);
        JobCollectorService service = new JobCollectorService(store, parser, crawler);
        JobSource source = new JobSource("source-js", "快手", "快手招聘公开页", "https://zhaopin.example.com", true, "Java", null, null);

        JobCollectResult result = service.collect(source, 3);

        TestAssertions.equals(0, result.added(), "invalid added count");
        TestAssertions.equals(1, result.skipped(), "invalid skipped count");
    }

    private static void countsDuplicateCollectedJobAsUpdate() {
        InMemoryJobCollectorStore store = new InMemoryJobCollectorStore();
        JobDescriptionParser parser = new JobDescriptionParser();
        JobCrawler crawler = new JobCrawler(url -> """
            <html><body><article>
              <a href="/apply/1001">Java 后端开发实习生</a>
              公司：示例大厂
              岗位：Java 后端开发实习生
              城市：北京
              岗位类型：实习
              任职要求：熟悉 Java、Spring Boot、Redis、MySQL，了解大模型 agent 应用。
            </article></body></html>
            """);
        JobCollectorService service = new JobCollectorService(store, parser, crawler);
        JobSource source = new JobSource("source-1", "示例大厂", "示例招聘", "https://jobs.example.com/list", true, "Java,后端,agent", null, null);

        JobCollectResult first = service.collect(source, 3);
        JobCollectResult second = service.collect(source, 3);

        TestAssertions.equals(1, first.added(), "first added count");
        TestAssertions.equals(0, second.added(), "second added count");
        TestAssertions.equals(1, second.updated(), "second updated count");
    }
}
