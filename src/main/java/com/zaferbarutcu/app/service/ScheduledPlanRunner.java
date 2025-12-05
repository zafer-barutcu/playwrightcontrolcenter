package com.zaferbarutcu.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaferbarutcu.app.domain.TestRun;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledPlanRunner {

    private static final Path SUITES_FILE = Path.of("data/suites.json");
    private static final Path STATE_FILE = Path.of("target/schedule-state.json");
    private final ObjectMapper mapper = new ObjectMapper();
    private final TestRunService testRunService;

    public ScheduledPlanRunner(TestRunService testRunService) {
        this.testRunService = testRunService;
    }

    private final ConcurrentHashMap<String, Long> lastTriggered = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 60000)
    public void tick() {
        try {
            loadState();
            List<Map<String, Object>> suites = loadSuites();
            long now = System.currentTimeMillis();
            for (Map<String, Object> s : suites) {
                String name = String.valueOf(s.getOrDefault("name", "")).trim();
                String schedule = String.valueOf(s.getOrDefault("schedule", "")).trim();
                if (name.isEmpty() || schedule.isEmpty()) continue;
                CronSpec spec = CronSpec.parse(schedule, name);
                if (spec == null) continue;
                long last = lastTriggered.getOrDefault(name, 0L);
                if (spec.matches(Instant.ofEpochMilli(now)) && (now - last) > 55_000) {
                    runSuitePlan(name, s);
                    lastTriggered.put(name, now);
                }
            }
            saveState();
        } catch (Exception ignored) {
        }
    }

    private List<Map<String, Object>> loadSuites() {
        try {
            if (!Files.exists(SUITES_FILE)) return List.of();
            return mapper.readValue(SUITES_FILE.toFile(), new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private void runSuitePlan(String name, Map<String, Object> suite) {
        try {
            String command = str(suite.get("command"), "mvn test");
            String env = str(suite.get("env"), "local");
            String baseUrl = str(suite.get("baseUrl"), "https://playwright.dev");
            String browser = str(suite.get("browser"), "chromium");
            boolean headless = Boolean.parseBoolean(str(suite.get("headless"), "true"));
            int parallelism = parseInt(suite.get("parallelism"), 4);
            String videoMode = str(suite.get("videoMode"), "fail");
            String workingDir = str(suite.get("workingDir"), "");
            String suiteFilter = buildSuiteFilter(suite.get("scenarios"));

            Files.deleteIfExists(Path.of("target/run-capture.json"));
            List<String> tokens = ensureSystemProps(command, env, baseUrl, browser, headless, parallelism, videoMode, suiteFilter);
            ProcessBuilder pb = new ProcessBuilder(tokens);
            pb.directory(safeWorkdir(workingDir));
            pb.redirectErrorStream(true);

            TestRun runEntity = testRunService.startRun(String.join(" ", tokens), env, baseUrl, browser, headless, parallelism, videoMode);
            StringBuilder logBuf = new StringBuilder();
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logBuf.append(line).append("\n");
                }
            }
            int exit = p.waitFor();
            runEntity.setFinishedAt(Instant.now());
            runEntity.setLogText(logBuf.toString());
            // try sync from cucumber json if available
            testRunService.syncFromCucumberJson(runEntity, Path.of("target/run-capture.json"));
            testRunService.finishRun(runEntity);
            testRunService.appendLog(runEntity, "Scheduled run exit code: " + exit);
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
                                           String suiteFilter) {
        List<String> tokens = new ArrayList<>(splitCommand(command));
        ensureProp(tokens, "env", env);
        ensureProp(tokens, "baseUrl", baseUrl);
        ensureProp(tokens, "browserName", browser);
        ensureProp(tokens, "headless", String.valueOf(headless));
        ensureProp(tokens, "cucumber.execution.parallel.enabled", "true");
        ensureProp(tokens, "cucumber.execution.parallel.config.strategy", "fixed");
        ensureProp(tokens, "cucumber.execution.parallel.config.fixed.parallelism", String.valueOf(parallelism));
        ensureProp(tokens, "videoMode", videoMode);
        if (suiteFilter != null && !suiteFilter.isBlank()) {
            ensureProp(tokens, "cucumber.filter.name", suiteFilter);
        }
        return tokens;
    }

    private void ensureProp(List<String> tokens, String key, String value) {
        String prefix = "-D" + key + "=";
        tokens.removeIf(t -> t.startsWith(prefix));
        tokens.add(prefix + (value == null ? "" : value));
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

    private String buildSuiteFilter(Object scenariosObj) {
        if (scenariosObj instanceof List<?> list && !list.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Object o : list) {
                if (o != null) names.add(String.valueOf(o));
            }
            return String.join("|", names);
        }
        return "";
    }

    private File safeWorkdir(String input) {
        try {
            if (input == null || input.isBlank()) {
                return new File(System.getProperty("user.dir"));
            }
            Path p = Path.of(input).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                return p.toFile();
            }
        } catch (Exception ignored) {}
        return new File(System.getProperty("user.dir"));
    }

    private int parseInt(Object val, int def) {
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception e) {
            return def;
        }
    }

    private String str(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? def : s;
    }

    private void loadState() {
        try {
            if (!Files.exists(STATE_FILE)) return;
            Map<String, Long> map = mapper.readValue(STATE_FILE.toFile(), new TypeReference<>() {});
            lastTriggered.clear();
            lastTriggered.putAll(map);
        } catch (Exception ignored) {}
    }

    private void saveState() {
        try {
            Files.createDirectories(STATE_FILE.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(STATE_FILE.toFile(), new HashMap<>(lastTriggered));
        } catch (Exception ignored) {}
    }

    private static class CronSpec {
        private final String raw;
        private final int minute;
        private final int hour;
        private final String dow;

        private CronSpec(String raw, int minute, int hour, String dow) {
            this.raw = raw;
            this.minute = minute;
            this.hour = hour;
            this.dow = dow;
        }

        static CronSpec parse(String expr, String seed) {
            try {
                String[] parts = expr.trim().split("\\s+");
                if (parts.length != 5) return null;
                int minute = resolveH(parts[0], seed, 0, 59);
                int hour = resolveH(parts[1], seed, 0, 23);
                String dow = parts[4];
                return new CronSpec(expr, minute, hour, dow);
            } catch (Exception e) {
                return null;
            }
        }

        boolean matches(Instant now) {
            java.time.ZonedDateTime zdt = now.atZone(java.time.ZoneId.systemDefault());
            boolean minuteMatch = zdt.getMinute() == minute;
            boolean hourMatch = zdt.getHour() == hour;
            return minuteMatch && hourMatch;
        }

        private static int resolveH(String token, String seed, int min, int max) {
            if (token == null || token.isBlank()) return min;
            if (token.startsWith("H")) {
                int span = max - min + 1;
                int hash = Math.abs(seed.hashCode());
                int base = (hash % span) + min;
                if (token.contains("/")) {
                    String[] split = token.split("/");
                    int step = Integer.parseInt(split[1]);
                    return min + ((base - min) % step);
                }
                return base;
            }
            if ("*".equals(token)) return min;
            return Integer.parseInt(token);
        }
    }
}
