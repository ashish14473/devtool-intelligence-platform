package com.devtools.intelligence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the 'openai.*' properties from application.yml.
 * Centralising this here means every service that needs model names
 * or the API key reads from one place instead of @Value-scattering.
 */
@Configuration
@ConfigurationProperties(prefix = "openai")
@Data
public class OpenAiProperties {

    private String apiKey;
    private String baseUrl;
    private String chatModel;
    private String embeddingModel;
    private int embeddingDimensions;
}
