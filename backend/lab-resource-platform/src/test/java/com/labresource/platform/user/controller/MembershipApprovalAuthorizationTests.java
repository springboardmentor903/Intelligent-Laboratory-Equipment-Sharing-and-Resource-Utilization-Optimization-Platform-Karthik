package com.labresource.platform.user.controller;

import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(MembershipApprovalAuthorizationTests.TestConfig.class)
class MembershipApprovalAuthorizationTests {
    @Autowired private UserController controller;
    @Autowired private UserService service;
    @Autowired private ResourceAccessPolicy resourceAccess;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        reset(service, resourceAccess);
    }

    @Test
    @WithMockUser(username = "researcher@example.test", roles = "RESEARCHER_STUDENT")
    void researcherCannotApproveMembership() {
        assertThrows(AccessDeniedException.class,
                () -> controller.approveMembership(12L,
                        SecurityContextHolder.getContext().getAuthentication()));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "pending@example.test", roles = "RESEARCHER_STUDENT")
    void pendingMemberCannotReadMembershipQueue() {
        when(resourceAccess.canManageMemberships(any())).thenReturn(false);
        assertThrows(AccessDeniedException.class,
                () -> controller.getPendingMemberships(
                        SecurityContextHolder.getContext().getAuthentication()));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = "INSTITUTION_ADMIN")
    void administratorCannotApproveCrossInstitutionOrSelf() {
        when(resourceAccess.canApproveMembership(any(), any())).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> controller.approveMembership(12L,
                        SecurityContextHolder.getContext().getAuthentication()));
        assertThrows(AccessDeniedException.class,
                () -> controller.approveMembership(11L,
                        SecurityContextHolder.getContext().getAuthentication()));
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = "INSTITUTION_ADMIN")
    void approvedAdministratorCanApproveAuthorizedMembershipWithoutLeakingHash() {
        when(resourceAccess.canApproveMembership(eq(12L), any())).thenReturn(true);
        User approved = new User();
        approved.setId(12L);
        approved.setEmail("member@example.test");
        approved.setInstitutionId(7L);
        approved.setPasswordHash("never-return-this");
        approved.setMembershipApproved(true);
        when(service.approveMembership(12L, "admin@example.test")).thenReturn(approved);

        var response = controller.approveMembership(12L,
                SecurityContextHolder.getContext().getAuthentication());
        var body = (java.util.Map<?, ?>) response.getBody();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("APPROVED", body.get("membershipStatus"));
        assertFalse(body.containsKey("passwordHash"));
        verify(service).approveMembership(12L, "admin@example.test");
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = "INSTITUTION_ADMIN")
    void administratorPendingListRequiresMembershipManagementScope() {
        when(resourceAccess.canManageMemberships(any())).thenReturn(true);
        when(service.getPendingMemberships("admin@example.test")).thenReturn(java.util.List.of());

        assertEquals(HttpStatus.OK, controller.getPendingMemberships(
                SecurityContextHolder.getContext().getAuthentication()).getStatusCode());
        verify(service).getPendingMemberships("admin@example.test");
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean ResourceAccessPolicy resourceAccess() { return mock(ResourceAccessPolicy.class); }
        @Bean UserController userController(UserService service, ResourceAccessPolicy policy) {
            return new UserController(service, policy);
        }
    }
}
