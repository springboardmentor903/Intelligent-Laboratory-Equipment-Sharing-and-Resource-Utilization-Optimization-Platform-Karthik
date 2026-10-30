package com.labresource.platform.auth.filter;

import com.labresource.platform.auth.service.JwtService;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterMembershipTests {
    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserRoleRepository userRoles = mock(UserRoleRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtService, users, userRoles, roles);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pendingUserIsAuthenticatedWithoutAuthorities() throws Exception {
        User pending = user(false);
        stubTokenUser(pending);

        filter.doFilter(request(), new MockHttpServletResponse(), emptyChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertTrue(authentication.getAuthorities().isEmpty());
        verifyNoInteractions(userRoles, roles);
    }

    @Test
    void legacyUserWithNullApprovalStillReceivesDatabaseLoadedAuthorities() throws Exception {
        User legacy = user(null);
        stubTokenUser(legacy);
        when(userRoles.findByUserId(51L)).thenReturn(List.of(new UserRole(51L, (short) 1)));
        when(roles.findById((short) 1))
                .thenReturn(Optional.of(new Role("RESEARCHER_STUDENT", "researcher")));

        filter.doFilter(request(), new MockHttpServletResponse(), emptyChain());

        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertEquals("ROLE_RESEARCHER_STUDENT", authorities.iterator().next().getAuthority());
    }

    private void stubTokenUser(User user) {
        Claims claims = mock(Claims.class);
        when(jwtService.parseClaims("signed-test-token")).thenReturn(claims);
        when(claims.get("email", String.class)).thenReturn(user.getEmail());
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private User user(Boolean approved) {
        User user = new User();
        user.setId(51L);
        user.setEmail("member@example.test");
        user.setInstitutionId(8L);
        user.setMembershipApproved(approved);
        return user;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/equipment");
        request.addHeader("Authorization", "Bearer signed-test-token");
        return request;
    }

    private FilterChain emptyChain() {
        return (request, response) -> { };
    }
}
