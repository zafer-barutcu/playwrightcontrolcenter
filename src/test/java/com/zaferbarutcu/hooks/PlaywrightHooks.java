package com.zaferbarutcu.hooks;

import com.google.inject.Inject;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Page;
import com.zaferbarutcu.config.TestConfig;
import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@ScenarioScoped
public class PlaywrightHooks {

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext browserContext;
    private final Page page;
    private final TestConfig config;

    @Inject
    public PlaywrightHooks(Playwright playwright, Browser browser, BrowserContext browserContext, Page page, TestConfig config) {
        this.playwright = playwright;
        this.browser = browser;
        this.browserContext = browserContext;
        this.page = page;
        this.config = config;
    }

    @Before
    public void beforeScenario(io.cucumber.java.Scenario scenario) {
        try {
            Files.createDirectories(Paths.get("target/videos"));
        } catch (Exception ignored) {
        }
    }

    @After(order = Integer.MAX_VALUE)
    public void tearDown(io.cucumber.java.Scenario scenario) {
        boolean failed = scenario != null && scenario.isFailed();
        Path captured = null;
        try {
            if (page != null && page.video() != null) {
                captured = page.video().path();
            }
            if (page != null && page.video() != null) {
                page.close();
            }
            if (browserContext != null) {
                browserContext.close();
            }
        } finally {
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
            saveVideo(scenario, captured, failed);
        }
    }

    private void saveVideo(io.cucumber.java.Scenario scenario, Path source, boolean failed) {
        if (scenario == null || source == null || !Files.exists(source)) {
            return;
        }
        String mode = config != null ? config.getVideoMode().toLowerCase() : "fail";
        if (!"all".equals(mode) && !"off".equals(mode) && !"fail".equals(mode)) {
            mode = "fail";
        }
        if ("off".equals(mode)) {
            safeDelete(source);
            return;
        }
        if ("fail".equals(mode) && !failed) {
            safeDelete(source);
            return;
        }
        try {
            Path targetDir = source != null && source.getParent() != null
                    ? source.getParent()
                    : Paths.get("target/videos");
            Files.createDirectories(targetDir);
            String slug = slugify(scenario.getName());
            Path target = targetDir.resolve(slug + ".webm");
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            if (!source.equals(target)) {
                safeDelete(source);
            }
        } catch (Exception ignored) {
        }
    }

    private void safeDelete(Path path) {
        try {
            if (path != null && Files.exists(path)) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
        }
    }

    private String slugify(String text) {
        return text == null ? "scenario" : text.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
