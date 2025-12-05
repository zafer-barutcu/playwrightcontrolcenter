package com.zaferbarutcu.app.repo;

import com.zaferbarutcu.app.domain.ScenarioStat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioStatRepository extends JpaRepository<ScenarioStat, Long> {
    List<ScenarioStat> findByRunId(Long runId);
    void deleteByRunId(Long runId);
}
