package com.devtools.intelligence.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Base64;

/**
 * Produces two RestClient beans:
 *
 * 1. openAiRestClient  — pre-configured for OpenAI API calls
 *    (Bearer token auth, api.openai.com base URL)
 *
 * 2. jiraRestClient    — pre-configured for Jira Cloud REST API calls
 *    (Basic auth with email:apiToken, atlassian.net base URL)
 *
 * Both are qualified so they can be injected by name without ambiguity.
 */
@Configuration
public class RestClientConfig {

    @Bean
    @Qualifier("openAiRestClient")
    public RestClient openAiRestClient(OpenAiProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(60_000);

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    @Qualifier("jiraRestClient")
    public RestClient jiraRestClient(JiraProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);

        // Jira Cloud uses HTTP Basic Auth: base64(email:apiToken)
        String credentials = props.getEmail() + ":" + props.getApiToken();
        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes());

        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
