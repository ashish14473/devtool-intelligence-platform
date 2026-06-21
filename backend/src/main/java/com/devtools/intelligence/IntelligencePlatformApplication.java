package com.devtools.intelligence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. On startup, IngestionOrchestrator (triggered by
 * ApplicationReadyEvent) automatically runs the full pipeline:
 * CSV -> LLM enrichment -> embedding -> in-memory vector store.
 *
 * Watch the console logs after running this - you'll see each
 * ticket being enriched and embedded in real time.
 */
@SpringBootApplication
public class IntelligencePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelligencePlatformApplication.class, args);
    }
}
