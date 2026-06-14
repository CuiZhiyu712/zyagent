package com.zyagent.job;

import java.util.List;

public class MeituanJobDetailCrawlerTest {
    public static void run() {
        supportsMeituanPositionDetailUrls();
        convertsMeituanDetailJsonToCrawlItem();
    }

    private static void supportsMeituanPositionDetailUrls() {
        MeituanJobDetailCrawler crawler = new MeituanJobDetailCrawler(url -> "");

        TestAssertions.isTrue(
            crawler.supports("https://zhaopin.meituan.com/web/position/detail?jobUnionId=4416322995&highlightType=campus"),
            "supports meituan detail url"
        );
    }

    private static void convertsMeituanDetailJsonToCrawlItem() {
        String sourceUrl = "https://zhaopin.meituan.com/web/position/detail?jobUnionId=4416322995&highlightType=campus";
        String json = """
            {
              "data": {
                "jobUnionId": "4416322995",
                "name": "大模型算法工程师（实习）",
                "jobFamily": "技术类",
                "jobFamilyGroup": "算法",
                "cityList": [{"name": "北京市"}, {"name": "上海市"}],
                "department": [{"name": "核心本地商业-基础研发平台"}],
                "departmentIntro": "基础研发平台是美团的核心技术平台。",
                "jobDuty": "负责智能体构建、大模型推理、多模态训练等核心技术。",
                "jobRequirement": "熟悉机器学习、深度学习、Python、Java，有大模型或 Agent 经验优先。",
                "jobType": "2"
              },
              "status": 0,
              "message": "success"
            }
            """;

        JobCrawlItem item = MeituanJobDetailCrawler.parseDetailJson(json, sourceUrl);
        JobPosting posting = new JobDescriptionParser().parse(item.text(), item.url());

        TestAssertions.equals(sourceUrl, item.url(), "meituan source url");
        TestAssertions.isTrue(item.detailFetched(), "meituan detail fetched");
        TestAssertions.equals("美团", posting.company(), "meituan company");
        TestAssertions.equals("大模型算法工程师（实习）", posting.title(), "meituan title");
        TestAssertions.equals("北京市, 上海市", posting.city(), "meituan city");
        TestAssertions.equals("实习", posting.jobType(), "meituan job type");
        TestAssertions.containsAll(posting.skills(), List.of("Java", "agent", "Agent"), "meituan skills");
    }
}
