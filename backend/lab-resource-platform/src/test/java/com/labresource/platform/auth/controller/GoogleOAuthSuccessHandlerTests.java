package com.labresource.platform.auth.controller;

import com.labresource.platform.auth.service.OAuthLoginTicketService;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleOAuthSuccessHandlerTests {
    @Test
    void issuesOneTimeTicketOnlyForVerifiedApprovedExistingAccount() throws Exception {
        UserService users = mock(UserService.class);
        OAuthLoginTicketService tickets = new OAuthLoginTicketService(60);
        GoogleOAuthSuccessHandler handler = new GoogleOAuthSuccessHandler(
                users, tickets, "http://localhost:5173/");
        OidcUser principal = mock(OidcUser.class);
        when(principal.getEmail()).thenReturn("person@example.test");
        when(principal.getClaims()).thenReturn(Map.of("email_verified", true));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        User account = new User();
        account.setId(8L);
        account.setEmail("person@example.test");
        account.setMembershipApproved(true);
        when(users.findByEmail("person@example.test")).thenReturn(Optional.of(account));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(mock(HttpServletRequest.class), response, authentication);

        assertTrue(response.getRedirectedUrl().startsWith("http://localhost:5173/oauth/callback#code="));
        String code = response.getRedirectedUrl().substring(response.getRedirectedUrl().indexOf("#code=") + 6);
        assertEquals(8L, tickets.consume(code).orElseThrow());
    }

    @Test
    void rejectsUnverifiedGoogleEmailBeforeLookingUpPlatformAccount() throws Exception {
        UserService users = mock(UserService.class);
        GoogleOAuthSuccessHandler handler = new GoogleOAuthSuccessHandler(
                users, new OAuthLoginTicketService(60), "http://localhost:5173");
        OidcUser principal = mock(OidcUser.class);
        when(principal.getClaims()).thenReturn(Map.of("email_verified", false));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(mock(HttpServletRequest.class), response, authentication);

        assertEquals("http://localhost:5173/login#oauth_error=google_login_failed",
                response.getRedirectedUrl());
        verifyNoInteractions(users);
    }

    @Test
    void rejectsUnapprovedExistingMembership() throws Exception {
        UserService users = mock(UserService.class);
        GoogleOAuthSuccessHandler handler = new GoogleOAuthSuccessHandler(
                users, new OAuthLoginTicketService(60), "http://localhost:5173");
        OidcUser principal = mock(OidcUser.class);
        when(principal.getEmail()).thenReturn("pending@example.test");
        when(principal.getClaims()).thenReturn(Map.of("email_verified", true));
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        User account = new User();
        account.setId(9L);
        account.setMembershipApproved(false);
        when(users.findByEmail("pending@example.test")).thenReturn(Optional.of(account));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(mock(HttpServletRequest.class), response, authentication);

        assertTrue(response.getRedirectedUrl().endsWith("/login#oauth_error=google_login_failed"));
    }
}
