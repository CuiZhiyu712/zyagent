package com.zyagent.modules.job;

import java.util.List;

public class BossJobParserTest {
    public static void run() {
        parsesBossListCard();
        mergesBossDetailText();
        detectsLoginOrVerifyPage();
    }

    private static void parsesBossListCard() {
        BossJobParser parser = new BossJobParser();
        String html = """
            <div class="job-card-wrapper">
              <a class="job-name" href="/job_detail/abc123.html">Java 后端开发实习生</a>
              <span class="job-area">北京·海淀区</span>
              <span class="salary">200-300元/天</span>
              <h3 class="company-name">示例科技</h3>
              <ul class="tag-list"><li>Java</li><li>Spring Boot</li><li>Redis</li></ul>
            </div>
            """;

        List<BossRawJob> jobs = parser.parseList(html, "https://www.zhipin.com/web/geek/job?query=Java");

        TestAssertions.equals(1, jobs.size(), "boss list job count");
        TestAssertions.equals("Java 后端开发实习生", jobs.get(0).title(), "boss title");
        TestAssertions.equals("示例科技", jobs.get(0).company(), "boss company");
        TestAssertions.equals("北京", jobs.get(0).city(), "boss city");
        TestAssertions.isTrue(jobs.get(0).detailUrl().contains("/job_detail/abc123.html"), "boss detail url");
        TestAssertions.isTrue(jobs.get(0).listText().contains("Spring Boot"), "boss list tags");
    }

    private static void mergesBossDetailText() {
        BossJobParser parser = new BossJobParser();
        BossRawJob raw = new BossRawJob(
            "Java 后端开发实习生",
            "示例科技",
            "北京",
            "200-300元/天",
            "https://www.zhipin.com/job_detail/abc123.html",
            "岗位：Java 后端开发实习生 公司：示例科技 城市：北京 技能：Java Redis"
        );
        String detailHtml = """
            <div class="job-sec-text">
              岗位职责：参与 AI Agent 后端服务开发。
              任职要求：熟悉 Java、Spring Boot、MySQL、Redis。
            </div>
            """;

        JobCrawlItem item = parser.toCrawlItem(raw, detailHtml);

        TestAssertions.isTrue(item.text().contains("岗位：Java 后端开发实习生"), "merged title");
        TestAssertions.isTrue(item.text().contains("任职要求：熟悉 Java"), "merged detail");
        TestAssertions.isTrue(item.detailFetched(), "detail fetched");
    }

    private static void detectsLoginOrVerifyPage() {
        BossJobParser parser = new BossJobParser();

        TestAssertions.isTrue(parser.needsLoginOrVerify("<div>请登录后继续访问</div>"), "login page");
        TestAssertions.isTrue(parser.needsLoginOrVerify("<div>验证码 安全验证</div>"), "verify page");
    }
}
