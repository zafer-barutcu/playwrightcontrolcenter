package com.zaferbarutcu.app.api;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FaviconController {

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> redirectFavicon() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/playwright-logo.svg"))
                .build();
    }
}
