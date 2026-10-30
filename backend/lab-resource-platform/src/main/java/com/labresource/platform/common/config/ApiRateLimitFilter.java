package com.labresource.platform.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small single-process limiter for the modular-monolith deployment.
 * Keys use the servlet peer address; forwarded headers are intentionally ignored.
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_TRACKED_BUCKETS = 20_000;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();
    private final int apiLimit;
    private final Duration apiWindow;
    private final int loginLimit;
    private final Duration loginWindow;
    private final int registrationLimit;
    private final Duration registrationWindow;
    private final int passwordResetLimit;
    private final Duration passwordResetWindow;

    public ApiRateLimitFilter(
            @Value("${app.rate-limit.api.max-requests:300}") int apiLimit,
            @Value("${app.rate-limit.api.window-seconds:60}") long apiWindowSeconds,
            @Value("${app.rate-limit.login.max-requests:10}") int loginLimit,
            @Value("${app.rate-limit.login.window-seconds:60}") long loginWindowSeconds,
            @Value("${app.rate-limit.registration.max-requests:10}") int registrationLimit,
            @Value("${app.rate-limit.registration.window-seconds:60}") long registrationWindowSeconds,
            @Value("${app.rate-limit.password-reset.max-requests:5}") int passwordResetLimit,
            @Value("${app.rate-limit.password-reset.window-seconds:300}") long passwordResetWindowSeconds) {
        this.apiLimit = positive(apiLimit, "api max requests");
        this.apiWindow = seconds(apiWindowSeconds, "api window");
        this.loginLimit = positive(loginLimit, "login max requests");
        this.loginWindow = seconds(loginWindowSeconds, "login window");
        this.registrationLimit = positive(registrationLimit, "registration max requests");
        this.registrationWindow = seconds(registrationWindowSeconds, "registration window");
        this.passwordResetLimit = positive(passwordResetLimit, "password reset max requests");
        this.passwordResetWindow = seconds(passwordResetWindowSeconds, "password reset window");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (requestCount.incrementAndGet() % 256 == 0) {
            windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        }

        String address = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        Limit limit = ruleFor(request.getRequestURI());
        long retrySeconds = consume("api:" + address, apiLimit, apiWindow, now);
        if (retrySeconds == 0 && limit != null) {
            retrySeconds = consume(limit.key() + ":" + address,
                    limit.maxRequests(), limit.window(), now);
        }
        if (retrySeconds > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retrySeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please retry later.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private long consume(String key, int maxRequests, Duration duration, long now) {
        if (windows.size() >= MAX_TRACKED_BUCKETS && !windows.containsKey(key)) {
            windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            if (windows.size() >= MAX_TRACKED_BUCKETS) return 1;
        }
        long windowMillis = duration.toMillis();
        Window updated = windows.compute(key, (ignored, current) ->
                current == null || current.expiresAt() <= now
                        ? new Window(now + windowMillis, 1)
                        : new Window(current.expiresAt(), current.requests() + 1));
        return updated.requests() > maxRequests
                ? Math.max(1, (updated.expiresAt() - now + 999) / 1000)
                : 0;
    }

    private Limit ruleFor(String path) {
        if (path.equals("/api/auth/login")) {
            return new Limit("login", loginLimit, loginWindow);
        }
        if (path.equals("/api/users/register")) {
            return new Limit("registration", registrationLimit, registrationWindow);
        }
        if (path.startsWith("/api/auth/password-reset/")) {
            return new Limit("password-reset", passwordResetLimit, passwordResetWindow);
        }
        return null;
    }

    private static int positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static Duration seconds(long value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
        return Duration.ofSeconds(value);
    }

    private record Window(long expiresAt, int requests) { }
    private record Limit(String key, int maxRequests, Duration window) { }
}
