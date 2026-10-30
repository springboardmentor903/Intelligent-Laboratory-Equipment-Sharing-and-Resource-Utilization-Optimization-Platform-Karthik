package com.labresource.platform.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiRateLimitFilterTests {
    @Test
    void throttlesLoginRequestsAndReturnsRetryMetadata() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(
                10, 60, 1, 60, 10, 60, 5, 300);
        MockHttpServletRequest first = request();
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request(), limited, new MockFilterChain());

        assertEquals(429, limited.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(
                limited.getContentAsString().contains("\"code\":\"RATE_LIMITED\""));
        assertEquals("60", limited.getHeader("Retry-After"));
    }

    @Test
    void doesNotTrustForwardedClientAddressToBypassTheLimit() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(
                10, 60, 1, 60, 10, 60, 5, 300);
        filter.doFilter(request(), new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletRequest second = request();
        second.addHeader("X-Forwarded-For", "198.51.100.90");
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(second, limited, new MockFilterChain());
        assertEquals(429, limited.getStatus());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.0.2.8");
        return request;
    }
}
