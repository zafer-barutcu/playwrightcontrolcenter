package com.zaferbarutcu.app.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class VideoLocator {
    @Value("${video.base-dir:target/videos}")
    private String baseDir;

    public String find(String scenarioName) {
        try {
            Path primary = Path.of(baseDir).toAbsolutePath().normalize();
            Path fallback = Path.of("target/videos").toAbsolutePath().normalize();
            Path[] bases = Files.exists(primary) ? new Path[]{primary, fallback} : new Path[]{fallback, primary};

            String slug = slugify(scenarioName);
            AtomicReference<Path> best = new AtomicReference<>();

            for (Path base : bases) {
                if (base == null || !Files.exists(base)) continue;
                try (var stream = Files.walk(base, 2)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> matches(p.getFileName().toString(), slug))
                            .forEach(p -> chooseNewest(best, p));
                }
                if (best.get() != null) {
                    String relative = base.relativize(best.get()).toString().replace("\\", "/");
                    return "/api/video?file=" + URLEncoder.encode(relative, StandardCharsets.UTF_8);
                }
            }

            for (Path base : bases) {
                if (base == null || !Files.exists(base)) continue;
                try (var stream = Files.walk(base, 2)) {
                    stream.filter(Files::isRegularFile)
                            .filter(p -> matchesAny(p.getFileName().toString()))
                            .forEach(p -> chooseNewest(best, p));
                }
                if (best.get() != null) {
                    String relative = base.relativize(best.get()).toString().replace("\\", "/");
                    return "/api/video?file=" + URLEncoder.encode(relative, StandardCharsets.UTF_8);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public String copyToSlug(String scenarioName) {
        try {
            Path primary = Path.of(baseDir).toAbsolutePath().normalize();
            Path fallback = Path.of("target/videos").toAbsolutePath().normalize();
            Path base = Files.exists(primary) ? primary : fallback;
            if (base == null || !Files.exists(base)) return null;

            Path latest = latestAny(base);
            if (latest == null || !Files.exists(latest)) return null;

            String ext = getExtension(latest.getFileName().toString());
            String targetName = slugify(scenarioName);
            if (targetName.isBlank()) targetName = latest.getFileName().toString();
            if (!ext.isBlank() && !targetName.endsWith(ext)) targetName = targetName + "." + ext;
            Path target = base.resolve(targetName);
            if (!Files.exists(target)) {
                try {
                    Files.copy(latest, target);
                } catch (Exception copyErr) {
                    target = latest;
                }
            }
            String relative = base.relativize(target).toString().replace("\\", "/");
            return "/api/video?file=" + URLEncoder.encode(relative, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private void chooseNewest(AtomicReference<Path> best, Path candidate) {
        Path current = best.get();
        try {
            if (current == null || Files.getLastModifiedTime(candidate).toMillis() > Files.getLastModifiedTime(current).toMillis()) {
                best.set(candidate);
            }
        } catch (Exception ignored) {}
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

    private Path latestAny(Path base) {
        AtomicReference<Path> best = new AtomicReference<>();
        try (var stream = Files.walk(base, 2)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> matchesAny(p.getFileName().toString()))
                    .forEach(p -> chooseNewest(best, p));
        } catch (Exception ignored) {}
        return best.get();
    }

    private String getExtension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return "";
        return name.substring(idx);
    }

    private String slugify(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }
}
