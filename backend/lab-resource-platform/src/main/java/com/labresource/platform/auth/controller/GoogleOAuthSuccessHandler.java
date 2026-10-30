package com.labresource.platform.auth.controller;

import com.labresource.platform.auth.service.OAuthLoginTicketService;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Links only verified Google identities to existing, already-approved platform accounts. */
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {
    private final UserService users;
    private final OAuthLoginTicketService tickets;
    private final String frontendUrl;

    public GoogleOAuthSuccessHandler(UserService users,
                                     OAuthLoginTicketService tickets,
                                     @Value("${app.oauth.google.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.users = users;
        this.tickets = tickets;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OidcUser principal)
                || !isVerified(principal)) {
            redirectError(response);
            return;
        }

        String email = principal.getEmail();
        User user = email == null ? null : users.findByEmail(email).orElse(null);
        if (user == null || Boolean.FALSE.equals(user.getMembershipApproved())) {
            redirectError(response);
            return;
        }

        String code = tickets.issue(user.getId());
        response.sendRedirect(frontendUrl + "/oauth/callback#code="
                + URLEncoder.encode(code, StandardCharsets.UTF_8));
    }

    private boolean isVerified(OidcUser principal) {
        Object verified = principal.getClaims().get("email_verified");
        return Boolean.TRUE.equals(verified) || "true".equalsIgnoreCase(String.valueOf(verified));
    }

    private void redirectError(HttpServletResponse response) throws IOException {
        response.sendRedirect(frontendUrl + "/login#oauth_error=google_login_failed");
    }
}
