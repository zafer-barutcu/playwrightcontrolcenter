package com.zaferbarutcu.app.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class ScenarioStat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long runId;
    private String name;
    private String status;
    @Lob
    @Column(length = 20000)
    private String stepsText;
    private String videoUrl;
    private Instant timestamp;

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStepsText() { return stepsText; }
    public void setStepsText(String stepsText) { this.stepsText = stepsText; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
