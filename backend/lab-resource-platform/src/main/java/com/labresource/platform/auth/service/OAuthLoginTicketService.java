package com.labresource.platform.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived, one-use bridge between the browser OAuth redirect and the JWT API. */
@Service
public class OAuthLoginTicketService {
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final Duration lifetime;
    @Autowired
    public OAuthLoginTicketService(
            @Value("${app.oauth.google.ticket-seconds:60}") long lifetimeSeconds) {
        this(Clock.systemUTC(), Duration.ofSeconds(lifetimeSeconds));
    }

    OAuthLoginTicketService(Clock clock, Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("OAuth ticket lifetime must be positive");
        }
        this.clock = clock;
        this.lifetime = lifetime;
    }

    public String issue(Long userId) {
        if (userId == null) throw new IllegalArgumentException("User ID is required");
        Instant now = clock.instant();
        tickets.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        byte[] bytes = new byte[32];
        String code;
        do {
            random.nextBytes(bytes);
            code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (tickets.containsKey(code));
        tickets.put(code, new Ticket(userId, now.plus(lifetime)));
        return code;
    }

    public Optional<Long> consume(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        Ticket ticket = tickets.remove(code);
        if (ticket == null || !ticket.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(ticket.userId());
    }

    private record Ticket(Long userId, Instant expiresAt) { }
}
