package com.zyagent.modules.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class MeituanJobDetailCrawler {
    private static final String DETAIL_API = "https://zhaopin.meituan.com/api/official/job/getJobDetail";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PageFetcher fetcher;

    public MeituanJobDetailCrawler() {
        this(MeituanJobDetailCrawler::fetchDetailApi);
    }

    public MeituanJobDetailCrawler(PageFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public boolean supports(String url) {
        try {
            URI uri = URI.create(url);
            return "zhaopin.meituan.com".equalsIgnoreCase(uri.getHost())
                && "/web/position/detail".equals(uri.getPath())
                && queryParam(uri.getRawQuery(), "jobUnionId") != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public JobCrawlItem crawl(String url) throws IOException {
        if (!supports(url)) {
            throw new IllegalArgumentException("Not a Meituan job detail URL: " + url);
        }
        return parseDetailJson(fetcher.fetch(url), url);
    }

    public static JobCrawlItem parseDetailJson(String json, String sourceUrl) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new IllegalArgumentException("Meituan job detail response has no data");
            }
            String text = buildJobText(data);
            return new JobCrawlItem(text, sourceUrl, "", text, true);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Meituan job detail JSON parse failed: " + ex.getMessage(), ex);
        }
    }

    private static String fetchDetailApi(String sourceUrl) throws IOException {
        URI source = URI.create(sourceUrl);
        String jobUnionId = queryParam(source.getRawQuery(), "jobUnionId");
        String highlightType = queryParam(source.getRawQuery(), "highlightType");
        String body = "{\"jobUnionId\":\"" + jsonEscape(jobUnionId) + "\",\"highlightType\":\"" + jsonEscape(highlightType == null ? "" : highlightType) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(DETAIL_API))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json;charset=UTF-8")
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", "https://zhaopin.meituan.com")
            .header("Referer", sourceUrl)
            .header("User-Agent", "zyagent-job-collector/0.1 (+personal job assistant)")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Meituan job detail request interrupted", ex);
        }
    }

    private static String buildJobText(JsonNode data) {
        List<String> lines = new ArrayList<>();
        lines.add("公司：美团");
        lines.add("岗位：" + text(data, "name"));
        lines.add("城市：" + joinNames(data.path("cityList")));
        lines.add("岗位类型：" + jobType(data));
        lines.add("技术方向：" + direction(data));
        addIfPresent(lines, "部门", joinNames(data.path("department")));
        addIfPresent(lines, "部门介绍", cleanHtml(text(data, "departmentIntro")));
        addIfPresent(lines, "岗位职责", cleanHtml(text(data, "jobDuty")));
        addIfPresent(lines, "任职要求", cleanHtml(text(data, "jobRequirement")));
        addIfPresent(lines, "加分项", cleanHtml(text(data, "bonus")));
        return String.join("\n", lines);
    }

    private static void addIfPresent(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(label + "：" + value);
        }
    }

    private static String jobType(JsonNode data) {
        String name = text(data, "jobTypeName");
        if (!name.isBlank()) {
            return name;
        }
        String title = text(data, "name");
        if (title.contains("实习")) {
            return "实习";
        }
        if (title.contains("校招")) {
            return "校招";
        }
        return switch (text(data, "jobType")) {
            case "2" -> "实习";
            case "1" -> "社招";
            default -> "未标注";
        };
    }

    private static String direction(JsonNode data) {
        String group = text(data, "jobFamilyGroup");
        if (!group.isBlank()) {
            return group;
        }
        String family = text(data, "jobFamily");
        return family.isBlank() ? "未标注" : family;
    }

    private static String joinNames(JsonNode nodes) {
        if (!nodes.isArray()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode node : nodes) {
            String name = text(node, "name");
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return String.join(", ", names);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").strip();
    }

    private static String cleanHtml(String value) {
        return Jsoup.parse(value == null ? "" : value).text().replaceAll("\\s+", " ").strip();
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        for (String part : rawQuery.split("&")) {
            int equals = part.indexOf('=');
            String key = equals < 0 ? part : part.substring(0, equals);
            if (name.equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                String value = equals < 0 ? "" : part.substring(equals + 1);
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
