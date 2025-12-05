package com.zaferbarutcu.app.api;

import com.zaferbarutcu.app.domain.TestRun;
import com.zaferbarutcu.app.service.TestRunService;
import com.zaferbarutcu.app.service.VideoLocator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class TestRunController {

    private static final Pattern SCENARIO_PATTERN = Pattern.compile("Scenario(?: Outline)?:\\s*(.+)");
    private static final Pattern FAIL_PATTERN = Pattern.compile("^\\s*[✘Xx]|failed", Pattern.CASE_INSENSITIVE);
    private static final Pattern STEP_PATTERN = Pattern.compile("^[^A-Za-z0-9]*(Given|When|Then|And|But)\\s+", Pattern.CASE_INSENSITIVE);
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final TestRunService testRunService;
    private final VideoLocator videoLocator;
    private final ObjectMapper mapper = new ObjectMapper();

    public TestRunController(TestRunService testRunService, VideoLocator videoLocator) {
        this.testRunService = testRunService;
        this.videoLocator = videoLocator;
    }

    @GetMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runCommand(
            @RequestParam(defaultValue = "mvn test") String command,
            @RequestParam(defaultValue = "120000") long timeoutMs,
            @RequestParam(defaultValue = "local") String env,
            @RequestParam(defaultValue = "https://playwright.dev") String baseUrl,
            @RequestParam(defaultValue = "chromium") String browser,
            @RequestParam(defaultValue = "true") boolean headless,
            @RequestParam(defaultValue = "4") int parallelism,
            @RequestParam(defaultValue = "fail") String videoMode,
            @RequestParam(required = false) String workdir,
            @RequestParam(required = false) String suiteFilter,
            @RequestParam(required = false) String suiteName,
            @RequestParam(required = false) String suiteTag
    ) {
        try {
            Files.deleteIfExists(Path.of("target/run-capture.json"));
        } catch (Exception ignored) {
        }
        List<String> tokens = ensureSystemProps(command, env, baseUrl, browser, headless, parallelism, videoMode, suiteFilter, suiteName, suiteTag);
        String ensuredCommand = String.join(" ", tokens);
        SseEmitter emitter = new SseEmitter(timeoutMs);

        emitter.onTimeout(emitter::complete);
        emitter.onCompletion(() -> { });

        executor.submit(() -> {
            try {
                List<String> runTokens = new ArrayList<>(tokens);
                emit(emitter, "log", "Starting: " + ensuredCommand);
                TestRun runEntity = testRunService.startRun(ensuredCommand, env, baseUrl, browser, headless, parallelism, videoMode);
                emit(emitter, "run-id", String.valueOf(runEntity.getId()));
                runTokens.removeIf(t -> t.startsWith("-Drun.id="));
                runTokens.removeIf(t -> t.startsWith("-DvideoMode="));
                runTokens.add("-Drun.id=" + runEntity.getId());
                runTokens.add("-DvideoMode=" + videoMode);
                runTokens = normalizeExecutable(runTokens);
                ProcessBuilder pb = new ProcessBuilder(runTokens);
                File wd = safeWorkdir(workdir);
                pb.directory(wd);
                pb.redirectErrorStream(true);
                Process process;
                try {
                    process = pb.start();
                } catch (IOException ioException) {
                    emit(emitter, "error", "Komut baslatilamadi. Maven PATH'te degilse mvnw/mvnw.cmd ekleyin ya da tam yol yazin. Ayrinti: " + ioException.getMessage());
                    emitter.completeWithError(ioException);
                    return;
                }

                AtomicReference<String> currentScenario = new AtomicReference<>(null);
                AtomicReference<String> currentKey = new AtomicReference<>(null);
                AtomicReference<Boolean> currentFailed = new AtomicReference<>(false);
                AtomicReference<Boolean> currentRecorded = new AtomicReference<>(false);
                AtomicReference<List<String>> currentSteps = new AtomicReference<>(new ArrayList<>());
                List<String> pendingQueue = new ArrayList<>();
                AtomicInteger index = new AtomicInteger(0);
                ConcurrentHashMap<String, Integer> nameCounts = new ConcurrentHashMap<>();
                boolean summaryMode = false;
                boolean collectingFailed = false;
                Set<String> failedNames = new HashSet<>();
                AtomicBoolean started = new AtomicBoolean(false);
                AtomicBoolean synced = new AtomicBoolean(false);

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        emit(emitter, "log", line);
                        testRunService.appendLog(runEntity, line);
                        if (line.trim().startsWith("Failed scenarios:")) {
                            summaryMode = true;
                            collectingFailed = true;
                            continue;
                        }
                        if (collectingFailed) {
                            String trimmed = line.trim();
                            if (trimmed.isEmpty()) {
                                collectingFailed = false;
                                continue;
                            }
                            if (!trimmed.contains("#")) {
                                continue;
                            }
                            String cleaned = trimmed.replaceFirst("^\\d+\\)\\s*", "");
                            String name = cleaned.split("#")[0].trim();
                            if (!name.isEmpty()) {
                                failedNames.add(name);
                            }
                            continue;
                        }
                        if (line.trim().matches("^\\d+\\s+scenarios.*")) {
                            summaryMode = true;
                        }
                        if (summaryMode) {
                            continue;
                        }

                        detectStep(emitter, line, currentScenario, currentKey, currentSteps, pendingQueue, nameCounts, started);
                        detectScenario(emitter, line, currentScenario, currentKey, currentFailed, currentRecorded, currentSteps, runEntity, index, nameCounts, pendingQueue, started);
                        detectFailure(emitter, line, currentScenario, currentKey, currentFailed, currentRecorded, currentSteps, runEntity);
                    }
                }

                int exitCode = process.waitFor();
                finalizeLast(emitter, currentScenario, currentKey, currentFailed, currentRecorded, currentSteps, runEntity);
                applyCollectedFailures(emitter, runEntity, failedNames);
                testRunService.finishRun(runEntity);
                emit(emitter, "complete", "Process finished with exit code: " + exitCode);
                emitter.complete();
            } catch (Exception e) {
                emit(emitter, "error", "Failed: " + e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void detectScenario(SseEmitter emitter, String line,
                                AtomicReference<String> currentScenario,
                                AtomicReference<String> currentKey,
                                AtomicReference<Boolean> currentFailed,
                                AtomicReference<Boolean> currentRecorded,
                                AtomicReference<List<String>> currentSteps,
                                TestRun runEntity,
                                AtomicInteger index,
                                ConcurrentHashMap<String, Integer> nameCounts,
                                List<String> pendingQueue,
                                AtomicBoolean started) {
        Matcher matcher = SCENARIO_PATTERN.matcher(line.trim());
        if (matcher.find()) {
            String raw = matcher.group(1);
            String scenarioName = raw.split("#")[0].trim();

            if (!started.get()) {
                pendingQueue.add(scenarioName);
                return;
            }

            String previous = currentScenario.get();
            String previousKey = currentKey.get();
            if (previous != null && !currentRecorded.get()) {
                emit(emitter, "scenario-pass", previousKey);
                testRunService.recordScenario(runEntity, previousKey, "PASS", String.join("\n", currentSteps.get()), null);
            }
            int count = nameCounts.merge(scenarioName, 1, Integer::sum);
            String key = count > 1 ? scenarioName + " #" + count : scenarioName;

            currentScenario.set(scenarioName);
            currentKey.set(key);
            currentFailed.set(false);
            currentRecorded.set(false);
            currentSteps.set(new ArrayList<>());
            emit(emitter, "scenario", key);
        }
    }

    private void detectFailure(SseEmitter emitter, String line,
                               AtomicReference<String> currentScenario,
                               AtomicReference<String> currentKey,
                               AtomicReference<Boolean> currentFailed,
                               AtomicReference<Boolean> currentRecorded,
                               AtomicReference<List<String>> currentSteps,
                               TestRun runEntity) {
        String trimmed = line.trim();
        if (!currentFailed.get() && FAIL_PATTERN.matcher(trimmed).find()) {
            String scenario = currentScenario.get();
            String key = currentKey.get();
            if (scenario != null) {
                currentFailed.set(true);
                String videoUrl = videoLocator.find(key);
                if (videoUrl == null) {
                    videoUrl = videoLocator.find(scenario);
                }
                if (videoUrl == null) {
                    videoUrl = videoLocator.copyToSlug(key);
                }
                String payload = key + "::" + (videoUrl == null ? "" : videoUrl);
                emit(emitter, "scenario-fail", payload);
                testRunService.recordScenario(runEntity, key, "FAIL", String.join("\n", currentSteps.get()), videoUrl);
                currentRecorded.set(true);
            }
        }
    }

    private void finalizeLast(SseEmitter emitter,
                              AtomicReference<String> currentScenario,
                              AtomicReference<String> currentKey,
                              AtomicReference<Boolean> currentFailed,
                              AtomicReference<Boolean> currentRecorded,
                              AtomicReference<List<String>> currentSteps,
                              TestRun runEntity) {
        String scenario = currentScenario.get();
        String key = currentKey.get();
        if (scenario != null) {
            if (currentFailed.get()) {
                String videoUrl = videoLocator.find(key);
                if (videoUrl == null) {
                    videoUrl = videoLocator.find(scenario);
                }
                if (videoUrl == null) {
                    videoUrl = videoLocator.copyToSlug(key);
                }
                if (!currentRecorded.get()) {
                    emit(emitter, "scenario-fail", key + "::" + (videoUrl == null ? "" : videoUrl));
                    testRunService.recordScenario(runEntity, key, "FAIL", String.join("\n", currentSteps.get()), videoUrl);
                }
            } else {
                if (!currentRecorded.get()) {
                    emit(emitter, "scenario-pass", key);
                    testRunService.recordScenario(runEntity, key, "PASS", String.join("\n", currentSteps.get()), null);
                }
            }
        }
    }

    private void applyCollectedFailures(SseEmitter emitter, TestRun runEntity, Set<String> failedNames) {
        if (failedNames.isEmpty()) {
            return;
        }
        for (String name : failedNames) {
            boolean changed = testRunService.markScenarioFailed(runEntity, name);
            if (changed) {
                emit(emitter, "scenario-fail", name + "::");
            }
        }
    }

    private void syncFromJson(SseEmitter emitter, TestRun runEntity, AtomicBoolean synced) {
        try {
            Path jsonPath = Path.of("target/run-capture.json");
            var syncedList = testRunService.syncFromCucumberJson(runEntity, jsonPath);
            if (syncedList == null) {
                return;
            }
            synced.set(true);
            List<Map<String, Object>> slim = new ArrayList<>();
            for (var sr : syncedList) {
                Map<String, Object> m = new HashMap<>();
                m.put("name", sr.getName());
                m.put("status", sr.getStatus());
                m.put("stepsText", sr.getStepsText());
                m.put("videoUrl", sr.getVideoUrl());
                slim.add(m);
            }
            emit(emitter, "sync", mapper.writeValueAsString(slim));
        } catch (Exception ignored) {
        }
    }

    private void detectStep(SseEmitter emitter, String line,
                            AtomicReference<String> currentScenario,
                            AtomicReference<String> currentKey,
                            AtomicReference<List<String>> currentSteps,
                            List<String> pendingQueue,
                            ConcurrentHashMap<String, Integer> nameCounts,
                            AtomicBoolean started) {
        String trimmed = line.trim();
        if (STEP_PATTERN.matcher(trimmed).find()) {
            if (!started.getAndSet(true)) {
                promotePending(emitter, currentScenario, currentKey, currentSteps, pendingQueue, nameCounts);
            } else if (currentScenario.get() == null && !pendingQueue.isEmpty()) {
                promotePending(emitter, currentScenario, currentKey, currentSteps, pendingQueue, nameCounts);
            }
            String stepText = trimmed
                    .replaceFirst("^[^A-Za-z0-9]*(?=Given|When|Then|And|But)", "")
                    .replaceAll("\\s+#.*", "")
                    .trim();
            currentSteps.get().add(stepText);
            String name = currentKey.get();
            if (name != null) {
                emit(emitter, "scenario-step", name + "::" + stepText);
            }
        }
    }

    private void promotePending(SseEmitter emitter,
                                AtomicReference<String> currentScenario,
                                AtomicReference<String> currentKey,
                                AtomicReference<List<String>> currentSteps,
                                List<String> pendingQueue,
                                ConcurrentHashMap<String, Integer> nameCounts) {
        if (pendingQueue.isEmpty()) {
            return;
        }
        String next = pendingQueue.remove(0);
        int count = nameCounts.merge(next, 1, Integer::sum);
        String key = count > 1 ? next + " #" + count : next;
        currentScenario.set(next);
        currentKey.set(key);
        currentSteps.set(new ArrayList<>());
        emit(emitter, "scenario", key);
    }

    private void emit(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(data));
        } catch (Exception ignored) {
        }
    }

    private List<String> ensureSystemProps(String command,
                                           String env,
                                           String baseUrl,
                                           String browser,
                                           boolean headless,
                                           int parallelism,
                                           String videoMode,
                                           String suiteFilter,
                                           String suiteName,
                                           String suiteTag) {
        List<String> tokens = new ArrayList<>(splitCommandWithWrapper(command));
        ensureProp(tokens, "env", env);
        ensureProp(tokens, "baseUrl", baseUrl);
        ensureProp(tokens, "browserName", browser);
        ensureProp(tokens, "headless", String.valueOf(headless));
        ensureProp(tokens, "cucumber.execution.parallel.enabled", "true");
        ensureProp(tokens, "cucumber.execution.parallel.config.strategy", "fixed");
        ensureProp(tokens, "cucumber.execution.parallel.config.fixed.parallelism", String.valueOf(parallelism));
        ensureProp(tokens, "videoMode", videoMode);
        String trimmedFilter = suiteFilter == null ? null : suiteFilter.trim();
        String trimmedTag = suiteTag == null ? null : suiteTag.trim();
        String trimmedSuiteName = suiteName == null ? null : suiteName.trim();

        if (trimmedFilter != null && !trimmedFilter.isBlank()) {
            ensureProp(tokens, "cucumber.filter.name", trimmedFilter);
        } else {
            removeProp(tokens, "cucumber.filter.name");
        }
        removeProp(tokens, "cucumber.filter.tags");
        return tokens;
    }

    private void ensureProp(List<String> tokens, String key, String value) {
        String prefix = "-D" + key + "=";
        tokens.removeIf(t -> t.startsWith(prefix));
        String safeValue = value == null ? "" : value.trim();
        tokens.add(prefix + safeValue);
    }

    private void removeProp(List<String> tokens, String key) {
        String prefix = "-D" + key + "=";
        tokens.removeIf(t -> t.startsWith(prefix));
    }

    private List<String> splitCommand(String cmd) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"|'([^']*)'|([^\\s]+)").matcher(cmd);
        while (m.find()) {
            String token = m.group(1);
            if (token == null) token = m.group(2);
            if (token == null) token = m.group(3);
            out.add(token);
        }
        return out;
    }

    private List<String> splitCommandWithWrapper(String cmd) {
        List<String> out = splitCommand(cmd);
        if (out.isEmpty()) {
            return out;
        }
        String first = out.get(0);
        String lower = first.toLowerCase();
        boolean isMvn = lower.equals("mvn") || lower.equals("mvn.cmd") || lower.equals("mvn.exe");
        if (isMvn) {
            Path cwd = Path.of(System.getProperty("user.dir"));
            Path mvnwCmd = cwd.resolve("mvnw.cmd");
            Path mvnw = cwd.resolve("mvnw");
            if (Files.isRegularFile(mvnwCmd)) {
                out.set(0, mvnwCmd.toAbsolutePath().toString());
            } else if (Files.isRegularFile(mvnw)) {
                out.set(0, mvnw.toAbsolutePath().toString());
            }
        }
        return out;
    }

    private String joinTokens(List<String> tokens) {
        return String.join(" ", tokens);
    }

    private File safeWorkdir(String input) {
        try {
            if (input == null || input.isBlank()) {
                return new File(System.getProperty("user.dir"));
            }
            Path p = Path.of(input).toAbsolutePath().normalize();
            Path repos = Path.of("target/repos").toAbsolutePath().normalize();
            Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            boolean allowed = p.startsWith(repos) || p.startsWith(cwd);
            if (allowed && Files.isDirectory(p)) {
                return p.toFile();
            }
        } catch (Exception ignored) {}
        return new File(System.getProperty("user.dir"));
    }

    private List<String> normalizeExecutable(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return tokens;
        String first = tokens.get(0);
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            if (first.endsWith("mvnw")) {
                Path cmd = Path.of("mvnw.cmd");
                tokens.set(0, cmd.toAbsolutePath().toString());
            }
            return tokens;
        }
        if (first.endsWith("mvnw.cmd")) {
            Path sh = Path.of("mvnw");
            if (Files.exists(sh) && Files.isExecutable(sh)) {
                tokens.set(0, sh.toAbsolutePath().toString());
            } else {
                tokens.set(0, "mvn");
            }
        } else if (first.endsWith("mvnw")) {
            Path sh = Path.of(first);
            if (Files.exists(sh) && Files.isExecutable(sh)) {
                tokens.set(0, sh.toAbsolutePath().toString());
            } else {
                tokens.set(0, "mvn");
            }
        }
        return tokens;
    }
}
