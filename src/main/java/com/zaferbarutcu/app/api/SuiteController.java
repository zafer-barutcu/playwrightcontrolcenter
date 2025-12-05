package com.zaferbarutcu.app.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suites")
public class SuiteController {

    private static final Path SUITES_FILE = Path.of("data/suites.json");
    private static final Pattern SCENARIO_PATTERN = Pattern.compile("^\\s*Scenario(?: Outline)?:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping
    public List<Map<String, Object>> listSuites() {
        try {
            if (!Files.exists(SUITES_FILE)) return List.of();
            return mapper.readValue(SUITES_FILE.toFile(), new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    @PostMapping
    public ResponseEntity<?> saveSuite(@RequestBody Map<String, Object> body) {
        try {
            String name = String.valueOf(body.getOrDefault("name", "")).trim();
            String tag = String.valueOf(body.getOrDefault("tag", "")).trim();
            List<String> scenarios = (List<String>) body.getOrDefault("scenarios", List.of());
            if (name.isEmpty() || scenarios.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "name and scenarios required"));
            }
            List<Map<String, Object>> suites = new ArrayList<>(listSuites());
            suites.removeIf(m -> name.equalsIgnoreCase(String.valueOf(m.get("name"))));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("scenarios", scenarios);
            if (!tag.isBlank()) {
                entry.put("tag", tag);
            } else {
                entry.put("tag", autoTag(name));
            }
            suites.add(entry);
            Files.createDirectories(SUITES_FILE.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(SUITES_FILE.toFile(), suites);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/schedule")
    public ResponseEntity<?> updateSchedule(@RequestBody Map<String, Object> body) {
        try {
            String name = String.valueOf(body.getOrDefault("name", "")).trim();
            String schedule = String.valueOf(body.getOrDefault("schedule", "")).trim();
            boolean notify = Boolean.parseBoolean(String.valueOf(body.getOrDefault("notify", "false")));
            String command = String.valueOf(body.getOrDefault("command", "mvn test"));
            String env = String.valueOf(body.getOrDefault("env", "local"));
            String baseUrl = String.valueOf(body.getOrDefault("baseUrl", "https://playwright.dev"));
            String browser = String.valueOf(body.getOrDefault("browser", "chromium"));
            String headless = String.valueOf(body.getOrDefault("headless", "true"));
            String parallelism = String.valueOf(body.getOrDefault("parallelism", "4"));
            String videoMode = String.valueOf(body.getOrDefault("videoMode", "fail"));
            String workingDir = String.valueOf(body.getOrDefault("workingDir", ""));
            if (name.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "name required"));
            }
            List<Map<String, Object>> suites = new ArrayList<>(listSuites());
            boolean found = false;
            for (Map<String, Object> s : suites) {
                if (name.equalsIgnoreCase(String.valueOf(s.get("name")))) {
                    found = true;
                    if (!schedule.isBlank()) {
                        s.put("schedule", schedule);
                    } else {
                        s.remove("schedule");
                    }
                    s.put("notify", notify);
                    s.put("command", command);
                    s.put("env", env);
                    s.put("baseUrl", baseUrl);
                    s.put("browser", browser);
                    s.put("headless", headless);
                    s.put("parallelism", parallelism);
                    s.put("videoMode", videoMode);
                    s.put("workingDir", workingDir);
                    break;
                }
            }
            if (!found) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "suite not found"));
            }
            Files.createDirectories(SUITES_FILE.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(SUITES_FILE.toFile(), suites);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/schedule")
    public ResponseEntity<?> deleteSchedule(@RequestParam("name") String rawName) {
        try {
            String name = rawName == null ? "" : rawName.trim();
            if (name.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "name required"));
            }
            List<Map<String, Object>> suites = new ArrayList<>(listSuites());
            boolean found = false;
            for (Map<String, Object> s : suites) {
                if (name.equalsIgnoreCase(String.valueOf(s.get("name")))) {
                    s.remove("schedule");
                    s.remove("notify");
                    found = true;
                    break;
                }
            }
            if (!found) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "suite not found"));
            }
            Files.createDirectories(SUITES_FILE.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(SUITES_FILE.toFile(), suites);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deleteSuite(@RequestParam(value = "name", required = false) String nameParam,
                                         @RequestBody(required = false) Map<String, Object> body) {
        try {
            String rawName = nameParam != null ? nameParam.trim() : "";
            if (rawName.isEmpty() && body != null) {
                rawName = String.valueOf(body.getOrDefault("name", "")).trim();
            }
            final String name = rawName;
            if (name.isEmpty()) return ResponseEntity.badRequest().body(Map.of("success", false));
            List<Map<String, Object>> suites = new ArrayList<>(listSuites());
            suites.removeIf(m -> name.equalsIgnoreCase(String.valueOf(m.get("name"))));
            if (!suites.isEmpty()) {
                Files.createDirectories(SUITES_FILE.getParent());
                mapper.writerWithDefaultPrettyPrinter().writeValue(SUITES_FILE.toFile(), suites);
            } else {
                Files.deleteIfExists(SUITES_FILE);
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/scenarios")
    public List<Map<String, String>> listScenarios() {
        List<Map<String, String>> names = new ArrayList<>();
        try {
            Path featuresDir = Path.of("src/test/resources/features");
            if (!Files.exists(featuresDir)) return names;
            Files.walk(featuresDir, 3)
                    .filter(p -> p.toString().endsWith(".feature"))
                    .forEach(p -> names.addAll(parseFeature(p)));
        } catch (Exception ignored) {}
        return names;
    }

    private List<Map<String, String>> parseFeature(Path file) {
        List<Map<String, String>> list = new ArrayList<>();
        String featureName = file.getFileName().toString().replace(".feature", "");
        try {
            for (String line : Files.readAllLines(file)) {
                Matcher m = SCENARIO_PATTERN.matcher(line);
                if (m.find()) {
                    String name = m.group(1).trim();
                    if (!name.isEmpty()) {
                        Map<String, String> entry = new LinkedHashMap<>();
                        entry.put("name", name);
                        entry.put("feature", featureName);
                        list.add(entry);
                    }
                }
            }
        } catch (IOException ignored) {}
        return list;
    }

    private String autoTag(String name) {
        if (name == null || name.isBlank()) return "";
        String slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_");
        if (!slug.startsWith("@")) {
            slug = "@" + slug;
        }
        return slug;
    }
}
