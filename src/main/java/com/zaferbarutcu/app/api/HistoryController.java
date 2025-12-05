package com.zaferbarutcu.app.api;

import com.zaferbarutcu.app.domain.TestRun;
import com.zaferbarutcu.app.domain.ScenarioResult;
import com.zaferbarutcu.app.domain.ScenarioStat;
import com.zaferbarutcu.app.repo.ScenarioResultRepository;
import com.zaferbarutcu.app.service.TestRunService;
import com.zaferbarutcu.app.service.VideoLocator;
import com.zaferbarutcu.app.service.ScenarioCounterService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.ArrayList;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final TestRunService testRunService;
    private final ScenarioResultRepository scenarioResultRepository;
    private final VideoLocator videoLocator;
    private final ScenarioCounterService scenarioCounterService;
    private final TestRunService runService;
    private final ObjectMapper mapper = new ObjectMapper();

    public HistoryController(TestRunService testRunService, ScenarioResultRepository scenarioResultRepository, VideoLocator videoLocator, ScenarioCounterService scenarioCounterService, TestRunService runService) {
        this.testRunService = testRunService;
        this.scenarioResultRepository = scenarioResultRepository;
        this.videoLocator = videoLocator;
        this.scenarioCounterService = scenarioCounterService;
        this.runService = runService;
    }

    @GetMapping
    public List<TestRun> list() {
        List<TestRun> runs = testRunService.listRuns();
        runs.forEach(this::recomputeCounts);
        return runs.stream()
                .sorted((a, b) -> Long.compare(
                        b.getId() != null ? b.getId() : 0,
                        a.getId() != null ? a.getId() : 0))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/scenarios")
    public List<ScenarioResult> scenarios(@PathVariable Long id) {
        var backfill = loadRunCapture();
        String mode = "";
        try { var r = runService.find(id); mode = r != null && r.getVideoMode()!=null ? r.getVideoMode() : ""; } catch (Exception ignored) {}
        final String videoMode = mode.toLowerCase();

        Map<String, ScenarioResult> merged = mergeResults(id);

        for (ScenarioResult s : merged.values()) {
            if (isBlank(s.getStepsText())) {
                String steps = backfill.getSteps(s.getName());
                if (steps != null) s.setStepsText(steps);
            }
            boolean allowVideo = !"fail".equals(videoMode) || "FAIL".equalsIgnoreCase(s.getStatus());
            if (allowVideo) {
                if (isBlank(s.getVideoUrl())) {
                    String vid = backfill.getVideo(s.getName());
                    if (vid == null || vid.isBlank()) {
                        vid = videoLocator.find(s.getName());
                    }
                    if (vid != null) s.setVideoUrl(vid);
                }
            } else {
                s.setVideoUrl(null);
            }
            if (isBlank(s.getStatus())) {
                String status = backfill.getStatus(s.getName());
                if (status != null) s.setStatus(status);
            }
        }
        return new ArrayList<>(merged.values());
    }

    @GetMapping("/{id}")
    public TestRun getRun(@PathVariable Long id) {
        TestRun run = testRunService.find(id);
        recomputeCounts(run);
        return run;
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void deleteRun(@PathVariable Long id) {
        scenarioCounterService.deleteByRun(id);
        testRunService.delete(id);
    }

    private Map<String, ScenarioResult> mergeResults(Long runId) {
        Map<String, ScenarioResult> map = new LinkedHashMap<>();
        List<ScenarioResult> repoList = scenarioResultRepository.findByTestRunId(runId);
        for (ScenarioResult s : repoList) {
            ScenarioResult copy = new ScenarioResult();
            copy.setName(s.getName());
            copy.setStatus(s.getStatus());
            copy.setStepsText(s.getStepsText());
            copy.setVideoUrl(s.getVideoUrl());
            copy.setTimestamp(s.getTimestamp());
            map.put(s.getName(), copy);
        }

        List<ScenarioStat> stats = scenarioCounterService.listByRun(runId);
        if (stats != null) {
            for (ScenarioStat stat : stats) {
                ScenarioResult sr = map.getOrDefault(stat.getName(), new ScenarioResult());
                sr.setName(stat.getName());
                if (!isBlank(stat.getStatus())) sr.setStatus(stat.getStatus());
                if (!isBlank(stat.getStepsText())) sr.setStepsText(stat.getStepsText());
                if (!isBlank(stat.getVideoUrl())) sr.setVideoUrl(stat.getVideoUrl());
                sr.setTimestamp(stat.getTimestamp());
                map.put(stat.getName(), sr);
            }
        }

        // include any names from run-capture that never reached DB (e.g., passed scenarios)
        BackfillData backfill = loadRunCapture();
        for (String name : backfill.names()) {
            if (isBlank(name) || map.containsKey(name)) continue;
            ScenarioResult sr = new ScenarioResult();
            sr.setName(name);
            sr.setStatus(backfill.getStatus(name));
            sr.setStepsText(backfill.getSteps(name));
            sr.setVideoUrl(backfill.getVideo(name));
            map.put(name, sr);
        }
        return map;
    }

    private void recomputeCounts(TestRun run) {
        if (run == null) return;
        Map<String, ScenarioResult> map = mergeResults(run.getId());
        int pass = (int) map.values().stream().filter(s -> "PASS".equalsIgnoreCase(s.getStatus())).count();
        int fail = (int) map.values().stream().filter(s -> "FAIL".equalsIgnoreCase(s.getStatus())).count();
        run.setPassedCount(pass);
        run.setFailedCount(fail);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private BackfillData loadRunCapture() {
        try {
            Path json = Path.of("target/run-capture.json");
            if (!Files.exists(json)) return BackfillData.empty();
            List<java.util.Map<String, Object>> list = mapper.readValue(json.toFile(), new TypeReference<>() {});
            return new BackfillData(list);
        } catch (Exception e) {
            return BackfillData.empty();
        }
    }

    private static class BackfillData {
        private final java.util.Map<String, java.util.Map<String, String>> map;
        BackfillData(List<java.util.Map<String, Object>> list) {
            map = new java.util.HashMap<>();
            for (var m : list) {
                String name = String.valueOf(m.getOrDefault("name", "")).trim();
                if (name.isEmpty()) continue;
                java.util.Map<String, String> v = new java.util.HashMap<>();
                v.put("stepsText", String.valueOf(m.getOrDefault("stepsText", "")).trim());
                v.put("videoUrl", String.valueOf(m.getOrDefault("videoUrl", "")).trim());
                v.put("status", String.valueOf(m.getOrDefault("status", "")).trim());
                map.put(name, v);
            }
        }
        static BackfillData empty() { return new BackfillData(java.util.List.of()); }
        String getSteps(String name) { var v = map.get(name); return v == null ? null : emptyToNull(v.get("stepsText")); }
        String getVideo(String name) { var v = map.get(name); return v == null ? null : emptyToNull(v.get("videoUrl")); }
        String getStatus(String name) { var v = map.get(name); return v == null ? null : emptyToNull(v.get("status")); }
        java.util.Set<String> names() { return map.keySet(); }
        private String emptyToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }
    }

    private List<ScenarioResult> dedupByLatest(List<ScenarioResult> list) {
        Map<String, ScenarioResult> map = new LinkedHashMap<>();
        Comparator<ScenarioResult> byTs = Comparator.comparing(s -> s.getTimestamp() != null ? s.getTimestamp() : java.time.Instant.EPOCH);
        list.stream()
                .filter(s -> s.getName() != null)
                .forEach(s -> {
                    ScenarioResult existing = map.get(s.getName());
                    if (existing == null || byTs.compare(s, existing) > 0) {
                        map.put(s.getName(), s);
                    }
                });
        return map.values().stream().collect(Collectors.toList());
    }
}
