package com.labresource.platform.auth.controller;

import com.labresource.platform.auth.dto.OAuthCodeExchangeRequest;
import com.labresource.platform.auth.service.JwtService;
import com.labresource.platform.auth.service.OAuthLoginTicketService;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.service.UserService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OAuthLoginControllerTests {

    @Test
    void exchangesAValidOneUseCodeForDatabaseLoadedRolesAndJwt() {
        OAuthLoginTicketService tickets = mock(OAuthLoginTicketService.class);
        UserService users = mock(UserService.class);
        JwtService jwt = mock(JwtService.class);
        OAuthLoginController controller = new OAuthLoginController(tickets, users, jwt);
        User user = approvedUser();

        when(tickets.consume("one-use-code")).thenReturn(Optional.of(user.getId()));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.getRoleNames(user.getId())).thenReturn(List.of("RESEARCHER_STUDENT"));
        when(jwt.generateToken(user)).thenReturn("test-only-token");

        var response = controller.exchange(new OAuthCodeExchangeRequest("one-use-code"));

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(user.getId(), body.get("id"));
        assertEquals(List.of("RESEARCHER_STUDENT"), body.get("roles"));
        assertEquals("test-only-token", body.get("token"));
        assertFalse(body.containsKey("password"));
        verify(users).getRoleNames(user.getId());
        verify(jwt).generateToken(user);
    }

    @Test
    void pendingMembershipCannotExchangeAValidCodeForCredentials() {
        OAuthLoginTicketService tickets = mock(OAuthLoginTicketService.class);
        UserService users = mock(UserService.class);
        JwtService jwt = mock(JwtService.class);
        OAuthLoginController controller = new OAuthLoginController(tickets, users, jwt);
        User user = approvedUser();
        user.setMembershipApproved(false);

        when(tickets.consume("one-use-code")).thenReturn(Optional.of(user.getId()));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        var response = controller.exchange(new OAuthCodeExchangeRequest("one-use-code"));

        assertEquals(401, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("OAUTH_CODE_INVALID", body.get("code"));
        verify(users, never()).getRoleNames(user.getId());
        verifyNoInteractions(jwt);
    }

    @Test
    void invalidCodeDoesNotLookUpUsersOrIssueCredentials() {
        OAuthLoginTicketService tickets = mock(OAuthLoginTicketService.class);
        UserService users = mock(UserService.class);
        JwtService jwt = mock(JwtService.class);
        OAuthLoginController controller = new OAuthLoginController(tickets, users, jwt);

        when(tickets.consume("expired-code")).thenReturn(Optional.empty());

        var response = controller.exchange(new OAuthCodeExchangeRequest("expired-code"));

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(users, jwt);
    }

    private static User approvedUser() {
        User user = new User();
        user.setId(41L);
        user.setEmail("researcher@example.test");
        user.setInstitutionId(7L);
        user.setMembershipApproved(true);
        return user;
    }
}
