package com.zaferbarutcu.app.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaferbarutcu.app.service.VideoLocator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LiveController {

    private final ObjectMapper mapper = new ObjectMapper();
    private final VideoLocator videoLocator;

    public LiveController(VideoLocator videoLocator) {
        this.videoLocator = videoLocator;
    }

    @GetMapping("/run-capture")
    public List<Map<String, Object>> capture(@RequestParam(defaultValue = "fail") String videoMode) {
        try {
            Path json = Path.of("target/run-capture.json");
            if (!Files.exists(json)) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> list = mapper.readValue(json.toFile(), new TypeReference<>() {});
            String mode = videoMode == null ? "fail" : videoMode.toLowerCase();
            boolean allowAll = "all".equals(mode);
            boolean allowFail = "fail".equals(mode);
            for (Map<String, Object> m : list) {
                String status = String.valueOf(m.getOrDefault("status", "")).toUpperCase();
                boolean allowed = allowAll || (allowFail && "FAIL".equals(status));
                if (!allowed) {
                    m.put("videoUrl", null);
                    continue;
                }
                Object url = m.get("videoUrl");
                if (url == null || String.valueOf(url).isBlank()) {
                    String found = videoLocator.find(String.valueOf(m.get("name")));
                    if (found != null) {
                        m.put("videoUrl", found);
                    }
                }
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
