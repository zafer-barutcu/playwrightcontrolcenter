package com.zaferbarutcu.app.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/run-settings")
public class RunDefaultsController {

    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping
    public ResponseEntity<?> load(@RequestParam(required = false) String workdir) {
        try {
            Path base = safeWorkdir(workdir);
            Path file = base.resolve("src/main/resources/run-settings.json").normalize();
            if (!file.startsWith(base) || !Files.exists(file)) {
                return ResponseEntity.ok(Map.of("success", true, "settings", null));
            }
            Map<?, ?> data = mapper.readValue(file.toFile(), Map.class);
            return ResponseEntity.ok(Map.of("success", true, "settings", data, "file", file.toString()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody Map<String, Object> body) {
        try {
            String workdir = body.getOrDefault("workdir", "").toString();
            Path base = safeWorkdir(workdir);
            Path file = base.resolve("src/main/resources/run-settings.json").normalize();
            if (!file.startsWith(base)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "invalid path"));
            }
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), body);
            return ResponseEntity.ok(Map.of("success", true, "file", file.toString()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private Path safeWorkdir(String input) {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repos = Paths.get("target/repos").toAbsolutePath().normalize();
        if (input == null || input.isBlank()) return cwd;
        Path p = Paths.get(input).toAbsolutePath().normalize();
        if (p.startsWith(repos) && Files.isDirectory(p)) return p;
        if (p.startsWith(cwd) && Files.isDirectory(p)) return p;
        return cwd;
    }
}
