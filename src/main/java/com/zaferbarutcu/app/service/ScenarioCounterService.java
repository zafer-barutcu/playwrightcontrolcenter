package com.zaferbarutcu.app.service;

import com.zaferbarutcu.app.domain.ScenarioStat;
import com.zaferbarutcu.app.repo.ScenarioStatRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScenarioCounterService {

    private final ScenarioStatRepository repo;

    public ScenarioCounterService(ScenarioStatRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void record(Long runId, String name, String status, String stepsText, String videoUrl) {
        if (runId == null || name == null) return;
        repo.findByRunId(runId).stream()
                .filter(s -> name.equals(s.getName()))
                .forEach(repo::delete);

        ScenarioStat stat = new ScenarioStat();
        stat.setRunId(runId);
        stat.setName(name);
        stat.setStatus(status);
        stat.setStepsText(stepsText);
        stat.setVideoUrl(videoUrl);
        stat.setTimestamp(Instant.now());
        repo.save(stat);
    }

    @Transactional(readOnly = true)
    public List<ScenarioStat> listByRun(Long runId) {
        return repo.findByRunId(runId);
    }

    @Transactional
    public void deleteByRun(Long runId) { repo.deleteByRunId(runId); }

    @Transactional
    public void deleteAll() { repo.deleteAll(); }
}
