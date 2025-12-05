package com.zaferbarutcu.config;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.cucumber.guice.ScenarioScoped;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

public class PlaywrightModule extends AbstractModule {

    @Provides
    @Singleton
    TestConfig testConfig() {
        Properties properties = new Properties();
        try (InputStream is = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("test-config.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException ignored) { }
        String environment = System.getProperty(
                "env",
                properties.getProperty("env", "local"));
        String baseUrl = System.getProperty(
                "baseUrl",
                properties.getProperty(environment + ".baseUrl", properties.getProperty("baseUrl", "https://playwright.dev")));
        boolean headless = Boolean.parseBoolean(System.getProperty(
                "headless",
                properties.getProperty("headless", "true")));
        String browserName = System.getProperty(
                "browserName",
                properties.getProperty("browserName", "chromium"));
        String videoMode = System.getProperty(
                "videoMode",
                properties.getProperty("videoMode", "fail"));
        return new TestConfig(baseUrl, headless, browserName, environment, videoMode);
    }

    @Provides
    @ScenarioScoped
    BrowserType.LaunchOptions launchOptions(TestConfig config) {
        return new BrowserType.LaunchOptions().setHeadless(config.isHeadless());
    }

    @Provides
    @ScenarioScoped
    Playwright playwright() {
        return Playwright.create();
    }

    @Provides
    @ScenarioScoped
    BrowserType browserType(Playwright playwright, TestConfig config) {
        String name = config.getBrowserName().toLowerCase();
        switch (name) {
            case "firefox":
                return playwright.firefox();
            case "webkit":
                return playwright.webkit();
            default:
                return playwright.chromium();
        }
    }

    @Provides
    @ScenarioScoped
    Browser browser(BrowserType browserType, BrowserType.LaunchOptions launchOptions) {
        return browserType.launch(launchOptions);
    }

    @Provides
    @ScenarioScoped
    BrowserContext browserContext(Browser browser, TestConfig config) {
        String mode = config.getVideoMode().toLowerCase();
        if ("off".equals(mode)) {
            return browser.newContext();
        }
        String runId = System.getProperty("run.id");
        Path videoDir = Paths.get("target/videos");
        if (runId != null && !runId.isBlank()) {
            videoDir = videoDir.resolve("run-" + runId);
        }
        try {
            Files.createDirectories(videoDir);
        } catch (Exception ignored) {}
        return browser.newContext(
                new Browser.NewContextOptions()
                        .setRecordVideoDir(videoDir)
                        .setRecordVideoSize(1280, 720)
        );
    }

    @Provides
    @ScenarioScoped
    Page page(BrowserContext browserContext) {
        return browserContext.newPage();
    }
}
