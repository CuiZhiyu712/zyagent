package com.zyagent.job;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class BossJobParser {
    public List<BossRawJob> parseList(String html, String baseUrl) {
        Document document = Jsoup.parse(html == null ? "" : html, baseUrl == null ? "https://www.zhipin.com" : baseUrl);
        List<BossRawJob> jobs = new ArrayList<>();
        for (Element card : document.select(".job-card-wrapper, .job-primary, li.job-card, [class*=job-card]")) {
            String title = firstText(card, ".job-name, .job-title, [class*=job-name], [class*=job-title]");
            String company = firstText(card, ".company-name, [class*=company-name], .boss-name");
            String area = firstText(card, ".job-area, [class*=job-area], .job-location");
            String salary = firstText(card, ".salary, [class*=salary]");
            String detailUrl = firstUrl(card, "a[href]");
            String listText = normalize(card.text());
            if (!title.isBlank() && !company.isBlank() && !detailUrl.isBlank()) {
                jobs.add(new BossRawJob(title, company, normalizeCity(area), salary, detailUrl, listText));
            }
        }
        return jobs;
    }

    public JobCrawlItem toCrawlItem(BossRawJob raw, String detailHtml) {
        String detailText = detailText(detailHtml);
        String text = normalize("""
            公司：%s
            岗位：%s
            城市：%s
            岗位类型：实习
            技术方向：后端
            薪资：%s
            列表信息：%s
            %s
            """.formatted(raw.company(), raw.title(), raw.city(), raw.salary(), raw.listText(), detailText));
        return new JobCrawlItem(text, raw.detailUrl(), raw.listText(), detailText, !detailText.isBlank());
    }

    public boolean needsLoginOrVerify(String html) {
        String text = normalize(Jsoup.parse(html == null ? "" : html).text()).toLowerCase();
        return text.contains("登录")
            || text.contains("验证码")
            || text.contains("安全验证")
            || text.contains("请完成验证")
            || text.contains("verify")
            || text.contains("captcha");
    }

    private String detailText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document document = Jsoup.parse(html, "https://www.zhipin.com");
        Element detail = document.selectFirst(".job-sec-text, .job-detail-section, .job-detail, [class*=job-sec], [class*=detail]");
        return normalize(detail == null ? document.body() == null ? document.text() : document.body().text() : detail.text());
    }

    private String firstText(Element element, String selector) {
        Element found = element.selectFirst(selector);
        return normalize(found == null ? "" : found.text());
    }

    private String firstUrl(Element element, String selector) {
        Element found = element.selectFirst(selector);
        if (found == null) {
            return "";
        }
        String href = found.absUrl("href");
        return href == null || href.isBlank() ? found.attr("href") : href;
    }

    private String normalizeCity(String area) {
        if (area == null || area.isBlank()) {
            return "北京";
        }
        String city = area.split("[·-]")[0].strip();
        return city.isBlank() ? "北京" : city;
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }
}
