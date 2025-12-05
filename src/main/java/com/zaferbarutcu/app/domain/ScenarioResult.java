package com.zaferbarutcu.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class ScenarioResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String status;
    private Instant timestamp;
    @Column(length = 4000)
    private String stepsText;
    private String videoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    private TestRun testRun;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getStepsText() { return stepsText; }
    public void setStepsText(String stepsText) { this.stepsText = stepsText; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public TestRun getTestRun() { return testRun; }
    public void setTestRun(TestRun testRun) { this.testRun = testRun; }
}
