package com.zyagent.modules.job;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class JobCrawler {
    private static final Pattern JAVA_WORD = Pattern.compile("(?i)(^|[^a-z])java($|[^a-z])");

    private final PageFetcher fetcher;
    private final MeituanJobDetailCrawler meituanJobDetailCrawler;

    public JobCrawler(PageFetcher fetcher) {
        this(fetcher, null);
    }

    public JobCrawler(PageFetcher fetcher, MeituanJobDetailCrawler meituanJobDetailCrawler) {
        this.fetcher = fetcher;
        this.meituanJobDetailCrawler = meituanJobDetailCrawler;
    }

    public List<String> crawlTexts(JobSource source, int maxPages) {
        return crawlItems(source, maxPages).stream()
            .map(JobCrawlItem::text)
            .toList();
    }

    public List<JobCrawlItem> crawlItems(JobSource source, int maxPages) {
        List<JobCrawlItem> all = new ArrayList<>();
        for (String url : crawlUrls(source, maxPages)) {
            all.addAll(crawlPage(source, url, maxPages));
        }
        return all.stream()
            .limit(Math.max(1, maxPages))
            .toList();
    }

    private List<JobCrawlItem> crawlPage(JobSource source, String url, int maxPages) {
        try {
            if (meituanJobDetailCrawler != null && meituanJobDetailCrawler.supports(url)) {
                return List.of(meituanJobDetailCrawler.crawl(url));
            }
            Document document = Jsoup.parse(fetcher.fetch(url), url);
            if (isInvalidShellPage(document) && document.select(listItemSelector(source) + " a[href], " + listItemSelector(source) + "[href]").isEmpty()) {
                return List.of();
            }
            List<JobCrawlItem> candidates = new ArrayList<>();
            int detailFetched = 0;
            for (Element element : document.select(listItemSelector(source))) {
                String listText = normalize(element.text());
                String detailText = "";
                String text = listText;
                String link = linkFrom(element, url, source);
                if (source.enabledDetailFetch() && detailFetched < source.normalizedMaxDetailPages() && !link.equals(url)) {
                    detailText = fetchDetailText(link);
                    if (!detailText.isBlank()) {
                        text = mergeText(listText, detailText);
                        detailFetched++;
                    }
                }
                if (looksLikeNavigation(text) && !matchesKeywords(text, source.keywords())) {
                    continue;
                }
                if (!text.isBlank()) {
                    candidates.add(new JobCrawlItem(text, link, listText, detailText, !detailText.isBlank()));
                }
            }
            if (candidates.isEmpty()) {
                String text = normalize(document.body() == null ? document.text() : document.body().text());
                if (!looksLikeNavigation(text)) {
                    candidates.add(new JobCrawlItem(text, url));
                }
            }
            return candidates.stream()
                .filter(item -> !isInvalidShellText(item.text()))
                .filter(item -> matchesKeywords(item.text(), source.keywords()))
                .toList();
        } catch (IOException ex) {
            throw new IllegalArgumentException("公开招聘页面采集失败：" + ex.getMessage());
        }
    }

    private List<String> crawlUrls(JobSource source, int maxPages) {
        if (!"SEARCH_PAGE".equalsIgnoreCase(source.normalizedSourceType()) || source.searchUrlTemplate() == null || source.searchUrlTemplate().isBlank()) {
            return List.of(source.url());
        }
        String keyword = parseKeywords(source.keywords()).stream().findFirst().orElse("");
        int pages = Math.max(1, maxPages);
        List<String> urls = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            urls.add(source.searchUrlTemplate()
                .replace("{keyword}", URLEncoder.encode(keyword, StandardCharsets.UTF_8))
                .replace("{page}", String.valueOf(page))
                .replace("{city}", ""));
        }
        return urls;
    }

    private String fetchDetailText(String url) throws IOException {
        Document detail = Jsoup.parse(fetcher.fetch(url), url);
        if (isInvalidShellPage(detail)) {
            return "";
        }
        return normalize(detail.body() == null ? detail.text() : detail.body().text());
    }

    private String mergeText(String listText, String detailText) {
        if (listText == null || listText.isBlank()) {
            return detailText;
        }
        if (detailText == null || detailText.isBlank() || detailText.contains(listText)) {
            return detailText == null || detailText.isBlank() ? listText : detailText;
        }
        return listText + "\n" + detailText;
    }

    private String listItemSelector(JobSource source) {
        return source.listItemSelector() == null || source.listItemSelector().isBlank()
            ? "article, li, .job, .position, .recruit, .list-item, .job-item, [class*=job], [class*=position]"
            : source.listItemSelector();
    }

    private String linkFrom(Element element, String fallbackUrl, JobSource source) {
        String selector = source.detailUrlSelector() == null || source.detailUrlSelector().isBlank() ? "a[href]" : source.detailUrlSelector();
        Element link = element.is(selector) ? element : element.selectFirst(selector);
        if (link == null) {
            return fallbackUrl;
        }
        String href = link.absUrl("href");
        return href == null || href.isBlank() ? fallbackUrl : href;
    }

    private boolean matchesKeywords(String text, String keywords) {
        List<String> parsed = parseKeywords(keywords);
        if (parsed.isEmpty()) {
            return true;
        }
        return parsed.stream().anyMatch(keyword -> containsKeyword(text, keyword));
    }

    private boolean containsKeyword(String text, String keyword) {
        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        if ("java".equals(lowerKeyword)) {
            return JAVA_WORD.matcher(text).find();
        }
        return lowerText.contains(lowerKeyword);
    }

    private List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String part : keywords.split("[,，]")) {
            String value = part.strip();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private boolean isInvalidShellPage(Document document) {
        String text = normalize(document.body() == null ? document.text() : document.body().text());
        return isInvalidShellText(text);
    }

    static boolean isInvalidShellText(String text) {
        String normalized = normalizeStatic(text).toLowerCase();
        return normalized.isBlank()
            || normalized.equals("you need to enable javascript to run this app.")
            || normalized.contains("enable javascript to run this app")
            || normalized.length() < 24;
    }

    private boolean looksLikeNavigation(String text) {
        String lower = text.toLowerCase();
        return text.length() < 16
            || lower.contains("首页") && lower.contains("登录") && !lower.contains("岗位")
            || lower.contains("copyright") && !lower.contains("java");
    }

    private String normalize(String text) {
        return normalizeStatic(text);
    }

    private static String normalizeStatic(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }
}
