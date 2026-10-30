package com.labresource.platform.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OAuthLoginTicketServiceTests {
    @Test
    void ticketIsOpaqueAndCanOnlyBeConsumedOnce() {
        OAuthLoginTicketService tickets = new OAuthLoginTicketService(60);
        String code = tickets.issue(42L);
        assertEquals(43, code.length());
        assertEquals(42L, tickets.consume(code).orElseThrow());
        assertTrue(tickets.consume(code).isEmpty());
    }

    @Test
    void rejectsNullUsersAndNonpositiveLifetime() {
        assertThrows(IllegalArgumentException.class, () -> new OAuthLoginTicketService(0));
        OAuthLoginTicketService tickets = new OAuthLoginTicketService(60);
        assertThrows(IllegalArgumentException.class, () -> tickets.issue(null));
        assertTrue(tickets.consume(null).isEmpty());
    }
}
