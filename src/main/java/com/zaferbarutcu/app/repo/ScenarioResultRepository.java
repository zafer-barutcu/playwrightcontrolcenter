package com.zaferbarutcu.app.repo;

import com.zaferbarutcu.app.domain.ScenarioResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioResultRepository extends JpaRepository<ScenarioResult, Long> {
    List<ScenarioResult> findByTestRunId(Long testRunId);
    long countByTestRunIdAndStatusIgnoreCase(Long testRunId, String status);
    void deleteByTestRunId(Long testRunId);
}
