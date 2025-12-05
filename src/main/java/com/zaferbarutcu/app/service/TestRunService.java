package com.zaferbarutcu.app.service;

import com.zaferbarutcu.app.domain.ScenarioResult;
import com.zaferbarutcu.app.domain.TestRun;
import com.zaferbarutcu.app.repo.TestRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestRunService {

    private final TestRunRepository testRunRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public TestRunService(TestRunRepository testRunRepository) {
        this.testRunRepository = testRunRepository;
    }

    @Transactional
    public TestRun startRun(String command, String env, String baseUrl, String browser, boolean headless, int parallelism, String videoMode) {
        TestRun run = new TestRun();
        run.setCommand(command);
        run.setEnv(env);
        run.setBaseUrl(baseUrl);
        run.setBrowser(browser);
        run.setHeadless(headless);
        run.setParallelism(parallelism);
        run.setVideoMode(videoMode);
        run.setStartedAt(Instant.now());
        run.setLogText("");
        run.setPassedCount(0);
        run.setFailedCount(0);
        return testRunRepository.save(run);
    }

    @Transactional
    public void recordScenario(TestRun run, String name, String status, String stepsText, String videoUrl) {
        run.getScenarios().removeIf(s -> name.equals(s.getName()));
        ScenarioResult sr = new ScenarioResult();
        sr.setName(name);
        sr.setStatus(status);
        sr.setTimestamp(Instant.now());
        sr.setStepsText(stepsText);
        sr.setVideoUrl(videoUrl);
        run.addScenario(sr);
        int pass = (int) run.getScenarios().stream().filter(s -> "PASS".equalsIgnoreCase(s.getStatus())).count();
        int fail = (int) run.getScenarios().stream().filter(s -> "FAIL".equalsIgnoreCase(s.getStatus())).count();
        run.setPassedCount(pass);
        run.setFailedCount(fail);
        testRunRepository.save(run);
    }

    @Transactional
    public void finishRun(TestRun run) {
        run.setFinishedAt(Instant.now());
        testRunRepository.save(run);
    }

    @Transactional(readOnly = true)
    public List<TestRun> listRuns() {
        return testRunRepository.findAll();
    }

    @Transactional(readOnly = true)
    public TestRun find(Long id) {
        return testRunRepository.findById(id).orElse(null);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) return;
        testRunRepository.deleteById(id);
    }

    @Transactional
    public void appendLog(TestRun run, String line) {
        String existing = run.getLogText() == null ? "" : run.getLogText();
        String updated = existing + line + "\n";
        if (updated.length() > 200000) {
            updated = updated.substring(updated.length() - 180000);
        }
        run.setLogText(updated);
        testRunRepository.save(run);
    }

    @Transactional
    public boolean markScenarioFailed(TestRun run, String name) {
        run.getScenarios().removeIf(s -> name.equals(s.getName()) || s.getName().startsWith(name + " #"));
        ScenarioResult sr = new ScenarioResult();
        sr.setName(name);
        sr.setStatus("FAIL");
        sr.setTimestamp(Instant.now());
        sr.setStepsText("");
        sr.setVideoUrl(null);
        run.addScenario(sr);
        int pass = (int) run.getScenarios().stream().filter(s -> "PASS".equalsIgnoreCase(s.getStatus())).count();
        int fail = (int) run.getScenarios().stream().filter(s -> "FAIL".equalsIgnoreCase(s.getStatus())).count();
        run.setPassedCount(pass);
        run.setFailedCount(fail);
        testRunRepository.save(run);
        return true;
    }

    @Transactional
    public List<ScenarioResult> syncFromCucumberJson(TestRun run, Path jsonPath) {
        try {
            if (jsonPath == null || !Files.exists(jsonPath)) {
                return null;
            }
            Map<String, String> videoMap = new HashMap<>();
            for (ScenarioResult sr : run.getScenarios()) {
                videoMap.put(sr.getName(), sr.getVideoUrl());
            }

            List<Map<String, Object>> features = mapper.readValue(jsonPath.toFile(), new TypeReference<>() {});
            Map<String, Integer> counters = new HashMap<>();
            List<ScenarioResult> rebuilt = new ArrayList<>();
            int pass = 0;
            int fail = 0;

            for (Map<String, Object> feature : features) {
                List<Map<String, Object>> elements = (List<Map<String, Object>>) feature.get("elements");
                if (elements == null) continue;
                for (Map<String, Object> el : elements) {
                    if (!"scenario".equals(el.get("type"))) continue;
                    String name = String.valueOf(el.get("name"));
                    int count = counters.merge(name, 1, Integer::sum);
                    String key = count > 1 ? name + " #" + count : name;
                    List<Map<String, Object>> steps = (List<Map<String, Object>>) el.get("steps");
                    StringBuilder sb = new StringBuilder();
                    boolean failed = false;
                    if (steps != null) {
                        for (Map<String, Object> step : steps) {
                            String keyword = String.valueOf(step.getOrDefault("keyword", "")).trim();
                            String stepName = String.valueOf(step.getOrDefault("name", "")).trim();
                            String line = (keyword + " " + stepName).trim();
                            if (!line.isEmpty()) {
                                if (sb.length() > 0) sb.append("\n");
                                sb.append(line);
                            }
                            Map<String, Object> result = (Map<String, Object>) step.get("result");
                            if (result != null && "failed".equalsIgnoreCase(String.valueOf(result.get("status")))) {
                                failed = true;
                            }
                        }
                    }
                    String status = failed ? "FAIL" : "PASS";
                    ScenarioResult sr = new ScenarioResult();
                    sr.setName(key);
                    sr.setStatus(status);
                    sr.setStepsText(sb.toString());
                    sr.setVideoUrl(videoMap.getOrDefault(key, videoMap.get(name)));
                    sr.setTestRun(run);
                    rebuilt.add(sr);
                    if (failed) fail++; else pass++;
                }
            }

            run.getScenarios().clear();
            run.getScenarios().addAll(rebuilt);
            run.setPassedCount(pass);
            run.setFailedCount(fail);
            testRunRepository.save(run);
            return rebuilt;
        } catch (Exception e) {
            return null;
        }
    }
}
