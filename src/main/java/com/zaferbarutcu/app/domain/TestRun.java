package com.zaferbarutcu.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@JsonIgnoreProperties({"scenarios"})
public class TestRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 4000)
    private String command;
    private String env;
    private String baseUrl;
    private String browser;
    private boolean headless;
    private int parallelism;
    private String videoMode;
    @Lob
    @Column(length = 200000)
    private String logText;

    private Instant startedAt;
    private Instant finishedAt;

    private int passedCount;
    private int failedCount;

    @OneToMany(mappedBy = "testRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScenarioResult> scenarios = new ArrayList<>();

    public void addScenario(ScenarioResult s) {
        s.setTestRun(this);
        scenarios.add(s);
    }

    public Long getId() { return id; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }
    public boolean isHeadless() { return headless; }
    public void setHeadless(boolean headless) { this.headless = headless; }
    public int getParallelism() { return parallelism; }
    public void setParallelism(int parallelism) { this.parallelism = parallelism; }
    public String getVideoMode() { return videoMode; }
    public void setVideoMode(String videoMode) { this.videoMode = videoMode; }
    public String getLogText() { return logText; }
    public void setLogText(String logText) { this.logText = logText; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public int getPassedCount() { return passedCount; }
    public void setPassedCount(int passedCount) { this.passedCount = passedCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public List<ScenarioResult> getScenarios() { return scenarios; }
}
