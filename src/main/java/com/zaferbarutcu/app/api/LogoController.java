package com.zaferbarutcu.app.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogoController {

    @GetMapping(value = "/static/logo.svg", produces = "image/svg+xml")
    public ResponseEntity<Resource> logo() {
        Resource res = new ClassPathResource("static/logo.svg");
        if (!res.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(res);
    }
}
