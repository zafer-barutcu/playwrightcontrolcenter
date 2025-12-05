package com.zaferbarutcu.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TestRunnerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestRunnerApplication.class, args);
    }
}
