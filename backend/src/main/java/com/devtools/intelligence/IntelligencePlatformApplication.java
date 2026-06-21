package com.devtools.intelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntelligencePlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntelligencePlatformApplication.class, args);
    }
}
