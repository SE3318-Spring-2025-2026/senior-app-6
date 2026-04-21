package com.senior.spm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configures a RestTemplate bean with a strict 5-second timeout
 * for all outbound HTTP calls to external APIs (JIRA, GitHub).
 *
 * Acceptance criterion: "Services strictly enforce a 5-second timeout."
 * Issue #48 — [Backend] External Tool Validation Services
 */
@Configuration
public class RestTemplateConfig {

    private static final int TIMEOUT_MS = 5_000;

    @Bean
    public RestTemplate restTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
