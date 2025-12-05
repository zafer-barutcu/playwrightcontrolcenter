package com.zaferbarutcu.config;

public class TestConfig {
    private final String baseUrl;
    private final boolean headless;
    private final String browserName;
    private final String environment;
    private final String videoMode;

    public TestConfig(String baseUrl, boolean headless, String browserName, String environment, String videoMode) {
        this.baseUrl = baseUrl;
        this.headless = headless;
        this.browserName = browserName;
        this.environment = environment;
        this.videoMode = videoMode;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean isHeadless() {
        return headless;
    }

    public String getBrowserName() {
        return browserName;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getVideoMode() {
        return videoMode;
    }
}
