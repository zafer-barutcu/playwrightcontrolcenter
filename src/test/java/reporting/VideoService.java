package reporting;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class VideoService {

    private final Path baseDir;

    public VideoService(String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    public String findVideoForScenario(String scenarioName) {
        try {
            if (!Files.exists(baseDir)) {
                return null;
            }
            String slug = slugify(scenarioName);
            AtomicReference<Path> best = new AtomicReference<>();

            try (var stream = Files.walk(baseDir, 2)) {
                stream
                        .filter(Files::isRegularFile)
                        .filter(p -> matches(p.getFileName().toString(), slug))
                        .forEach(p -> {
                            Path current = best.get();
                            try {
                                if (current == null || Files.getLastModifiedTime(p).toMillis() > Files.getLastModifiedTime(current).toMillis()) {
                                    best.set(p);
                                }
                            } catch (Exception ignored) { }
                        });
            }

            Path chosen = best.get();
            if (chosen == null) {
                try (var stream = Files.walk(baseDir, 2)) {
                    stream
                            .filter(Files::isRegularFile)
                            .filter(p -> matchesAny(p.getFileName().toString()))
                            .forEach(p -> {
                                Path current = best.get();
                                try {
                                    if (current == null || Files.getLastModifiedTime(p).toMillis() > Files.getLastModifiedTime(current).toMillis()) {
                                        best.set(p);
                                    }
                                } catch (Exception ignored) { }
                            });
                }
                chosen = best.get();
                if (chosen == null) return null;
            }
            String relative = baseDir.relativize(chosen).toString().replace("\\", "/");
            return "/api/video?file=" + URLEncoder.encode(relative, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean matches(String fileName, String slug) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.contains(slug.toLowerCase(Locale.ROOT))
                && (lower.endsWith(".gif") || lower.endsWith(".webm") || lower.endsWith(".mp4") || lower.endsWith(".mkv"));
    }

    private boolean matchesAny(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".gif") || lower.endsWith(".webm") || lower.endsWith(".mp4") || lower.endsWith(".mkv");
    }

    private String slugify(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }
}
