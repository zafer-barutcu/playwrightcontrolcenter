package com.zaferbarutcu.app.api;

import com.zaferbarutcu.app.service.ScenarioCounterService;
import com.zaferbarutcu.app.repo.ScenarioResultRepository;
import com.zaferbarutcu.app.repo.TestRunRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ScenarioCounterService counterService;
    private final ScenarioResultRepository scenarioResultRepository;
    private final TestRunRepository testRunRepository;
    private final JdbcTemplate jdbcTemplate;

    public AdminController(ScenarioCounterService counterService,
                           ScenarioResultRepository scenarioResultRepository,
                           TestRunRepository testRunRepository,
                           JdbcTemplate jdbcTemplate) {
        this.counterService = counterService;
        this.scenarioResultRepository = scenarioResultRepository;
        this.testRunRepository = testRunRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/reset")
    public void resetAll() {
        counterService.deleteAll();
        scenarioResultRepository.deleteAll();
        testRunRepository.deleteAll();
        try {
            jdbcTemplate.execute("ALTER TABLE TEST_RUN ALTER COLUMN ID RESTART WITH 1");
            jdbcTemplate.execute("ALTER TABLE SCENARIO_RESULT ALTER COLUMN ID RESTART WITH 1");
            jdbcTemplate.execute("ALTER TABLE SCENARIO_STAT ALTER COLUMN ID RESTART WITH 1");
        } catch (Exception ignored) {
        }
    }
}
