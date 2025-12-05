package com.zaferbarutcu.pages;

import com.google.inject.Inject;
import com.zaferbarutcu.config.TestConfig;
import com.microsoft.playwright.Page;
import io.cucumber.guice.ScenarioScoped;

@ScenarioScoped
public class NavigationPage {

    private final Page page;
    private final TestConfig config;

    @Inject
    public NavigationPage(Page page, TestConfig config) {
        this.page = page;
        this.config = config;
    }

    public void navigate(String pathOrUrl) {
        String target = buildUrl(pathOrUrl);
        page.navigate(target);
    }

    public String title() {
        return page.title();
    }

    private String buildUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isEmpty()) {
            return config.getBaseUrl();
        }
        String trimmed = pathOrUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return config.getBaseUrl() + trimmed;
        }
        return config.getBaseUrl() + "/" + trimmed;
    }
}
