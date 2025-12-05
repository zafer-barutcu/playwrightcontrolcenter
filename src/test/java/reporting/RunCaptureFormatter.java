package reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.HookTestStep;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import io.cucumber.plugin.event.TestStep;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RunCaptureFormatter implements EventListener {

    private final List<Map<String, Object>> scenarios = new ArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private ScenarioBuffer current;
    private final Path output = Path.of("target/run-capture.json");
    private final Map<String, Integer> nameCounters = new ConcurrentHashMap<>();

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::onCaseStarted);
        publisher.registerHandlerFor(TestStepStarted.class, this::onStepStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::onStepFinished);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onCaseFinished);
        publisher.registerHandlerFor(TestRunFinished.class, e -> writeOutput());
    }

    private void onCaseStarted(TestCaseStarted event) {
        current = new ScenarioBuffer();
        current.name = uniqueName(event.getTestCase().getName());
    }

    private void onStepStarted(TestStepStarted event) {
        if (current == null) return;
        TestStep step = event.getTestStep();
        if (step instanceof HookTestStep) {
            return;
        }
        if (step instanceof PickleStepTestStep ps) {
            String keyword = ps.getStep().getKeyword().trim();
            String text = ps.getStep().getText().trim();
            String line = (keyword + " " + text).trim();
            if (!line.isEmpty()) {
                current.steps.add(line);
            }
        }
    }

    private void onStepFinished(TestStepFinished event) {
        if (current == null) return;
        if (event.getResult().getStatus() == Status.FAILED) {
            current.failed = true;
        }
    }

    private void onCaseFinished(TestCaseFinished event) {
        if (current == null) return;
        current.failed = event.getResult().getStatus() == Status.FAILED || current.failed;
        Map<String, Object> m = new HashMap<>();
        m.put("name", current.name);
        m.put("status", current.failed ? "FAIL" : "PASS");
        m.put("stepsText", current.steps.stream().collect(Collectors.joining("\n")));
        m.put("videoUrl", findVideoUrl(current.name));
        scenarios.add(m);
        current = null;
        writeOutput();
    }

    private void writeOutput() {
        try {
            Files.createDirectories(output.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), scenarios);
        } catch (IOException ignored) { }
    }

    private String uniqueName(String base) {
        int c = nameCounters.merge(base, 1, Integer::sum);
        return c > 1 ? base + " #" + c : base;
    }

    private static class ScenarioBuffer {
        String name;
        boolean failed = false;
        List<String> steps = new ArrayList<>();
    }

    private String findVideoUrl(String scenarioName) {
        try {
            Path base = Path.of("target/videos").toAbsolutePath().normalize();
            if (!Files.exists(base)) return null;
            String slug = slugify(scenarioName);
            Path best = pickLatest(base, slug);
            if (best == null) {
                best = pickLatest(base, null);
            }
            if (best == null) return null;
            String relative = base.relativize(best).toString().replace("\\", "/");
            return "/api/video?file=" + URLEncoder.encode(relative, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private Path pickLatest(Path base, String slug) throws IOException {
        Path[] best = new Path[1];
        try (var stream = Files.walk(base, 2)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> isVideo(p.getFileName().toString()))
                    .filter(p -> slug == null || matchesSlug(p.getFileName().toString(), slug))
                    .forEach(p -> {
                        if (best[0] == null) {
                            best[0] = p;
                        } else {
                            try {
                                if (Files.getLastModifiedTime(p).toMillis() > Files.getLastModifiedTime(best[0]).toMillis()) {
                                    best[0] = p;
                                }
                            } catch (Exception ignored) {}
                        }
                    });
        }
        return best[0];
    }

    private boolean isVideo(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".webm") || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".gif");
    }

    private boolean matchesSlug(String name, String slug) {
        return name.toLowerCase().contains(slug.toLowerCase());
    }

    private String slugify(String text) {
        String slug = text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        if (slug.isBlank()) slug = "scenario";
        return slug;
    }
}
