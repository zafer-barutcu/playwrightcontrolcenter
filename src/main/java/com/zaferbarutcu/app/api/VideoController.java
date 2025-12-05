package com.zaferbarutcu.app.api;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    @Value("${video.base-dir:target/videos}")
    private String baseDir;

    @GetMapping
    public ResponseEntity<FileSystemResource> stream(@RequestParam("file") String file) {
        try {
            String clean = file == null ? "" : file.replaceFirst("^/+", "");
            FileSystemResource resource = resolveResource(Path.of("target/videos"), clean);
            if (resource == null) {
                resource = resolveResource(Path.of(baseDir), clean);
            }
            if (resource == null) {
                resource = resolveResource(Path.of("target/dashboard-videos"), clean);
            }
            if (resource == null) {
                return ResponseEntity.notFound().build();
            }
            MediaType type = MediaTypeFactory.getMediaType(resource.getFilename()).orElse(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok()
                    .contentType(type)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private FileSystemResource resolveResource(Path base, String file) {
        try {
            Path root = base.toAbsolutePath().normalize();
            Path requested = root.resolve(file).normalize();
            if (!requested.startsWith(root) || !Files.exists(requested)) {
                return null;
            }
            return new FileSystemResource(requested);
        } catch (Exception e) {
            return null;
        }
    }
}
