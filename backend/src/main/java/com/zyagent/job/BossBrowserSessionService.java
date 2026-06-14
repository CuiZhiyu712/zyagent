package com.zyagent.job;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.zyagent.config.ZyagentProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class BossBrowserSessionService {
    private static final String BOSS_HOME = "https://www.zhipin.com/";

    private final ZyagentProperties properties;
    private final BossJobParser parser;
    private Playwright playwright;
    private BrowserContext context;
    private Page page;

    public BossBrowserSessionService(ZyagentProperties properties, BossJobParser parser) {
        this.properties = properties;
        this.parser = parser;
    }

    public synchronized BossSessionStatus open() {
        Page current = ensurePage();
        current.navigate(BOSS_HOME);
        return status();
    }

    public synchronized BossSessionStatus status() {
        if (page == null) {
            return new BossSessionStatus(false, "", false, "BOSS 浏览器尚未打开");
        }
        String url = safeUrl();
        boolean needsLogin = parser.needsLoginOrVerify(safeContent());
        return new BossSessionStatus(true, url, !needsLogin, needsLogin ? "需要在浏览器中完成登录或验证" : "浏览器已打开");
    }

    public synchronized List<JobCrawlItem> collectItems(BossCollectRequest request) {
        BossPlaywrightPage browserPage = new BossPlaywrightPage(ensurePage(), parser);
        return browserPage.collect(request);
    }

    private Page ensurePage() {
        if (page != null) {
            return page;
        }
        ZyagentProperties.BossCollect boss = properties.bossCollect();
        playwright = Playwright.create();
        context = playwright.chromium().launchPersistentContext(
            Path.of(boss.profileDir()),
            new BrowserType.LaunchPersistentContextOptions().setHeadless(boss.headless())
        );
        page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        return page;
    }

    private String safeUrl() {
        try {
            return page.url();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String safeContent() {
        try {
            return page.content();
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
