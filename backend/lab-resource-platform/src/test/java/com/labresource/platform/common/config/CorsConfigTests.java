package com.labresource.platform.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTests {

    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Test
    void allowsPatchPreflightForExistingBookingWorkflowFromConfiguredFrontend() throws Exception {
        CorsConfiguration configuration = corsConfiguration();
        MockHttpServletRequest request = preflightRequest(FRONTEND_ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new DefaultCorsProcessor().processRequest(configuration, request, response);

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(FRONTEND_ORIGIN);
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("PATCH");
        assertThat(response.getHeader("Access-Control-Allow-Headers")).contains("Authorization");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isNull();
    }

    @Test
    void rejectsPatchPreflightFromOriginsOutsideTheExistingAllowlist() throws Exception {
        CorsConfiguration configuration = corsConfiguration();
        MockHttpServletRequest request = preflightRequest("https://untrusted.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new DefaultCorsProcessor().processRequest(configuration, request, response);

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    private CorsConfiguration corsConfiguration() {
        ExposedCorsRegistry registry = new ExposedCorsRegistry();
        new CorsConfig().addCorsMappings(registry);
        return registry.configurations().get("/api/**");
    }

    private MockHttpServletRequest preflightRequest(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/bookings/42/cancel");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "PATCH");
        request.addHeader("Access-Control-Request-Headers", "Authorization, Content-Type");
        return request;
    }

    private static class ExposedCorsRegistry extends CorsRegistry {
        private Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
