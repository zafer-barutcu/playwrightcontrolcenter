package com.zaferbarutcu.app.api;

import com.zaferbarutcu.app.service.ScenarioCounterService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenario")
public class ScenarioStatController {

    private final ScenarioCounterService counterService;

    public ScenarioStatController(ScenarioCounterService counterService) {
        this.counterService = counterService;
    }

    @PostMapping
    public void record(@RequestParam Long runId,
                       @RequestParam String name,
                       @RequestParam String status,
                       @RequestParam(required = false) String stepsText,
                       @RequestParam(required = false) String videoUrl) {
        counterService.record(runId, name, status, stepsText, videoUrl);
    }
}
