package com.senior.spm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Verifies acceptance criterion from Issue #48:
 * "Services strictly enforce a 5-second timeout."
 */
class RestTemplateConfigTest {

    private static final int EXPECTED_TIMEOUT_MS = 5_000;

    private final RestTemplateConfig config = new RestTemplateConfig();

    @Test
    void restTemplate_usesSimpleClientHttpRequestFactory() {
        var restTemplate = config.restTemplate();
        assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
    }

    @Test
    void restTemplate_hasFiveSecondConnectTimeout() throws Exception {
        var factory = (SimpleClientHttpRequestFactory) config.restTemplate().getRequestFactory();
        Field field = SimpleClientHttpRequestFactory.class.getDeclaredField("connectTimeout");
        field.setAccessible(true);
        assertThat(field.get(factory)).isEqualTo(EXPECTED_TIMEOUT_MS);
    }

    @Test
    void restTemplate_hasFiveSecondReadTimeout() throws Exception {
        var factory = (SimpleClientHttpRequestFactory) config.restTemplate().getRequestFactory();
        Field field = SimpleClientHttpRequestFactory.class.getDeclaredField("readTimeout");
        field.setAccessible(true);
        assertThat(field.get(factory)).isEqualTo(EXPECTED_TIMEOUT_MS);
    }
}
