package com.labresource.platform.user.service;

import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.user.dto.UserRegistrationRequest;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipApprovalServiceTests {
    @Mock private UserRepository users;
    @Mock private InstitutionRepository institutions;
    @Mock private UserRoleRepository userRoles;
    @Mock private RoleRepository roles;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, institutions, userRoles, roles);
    }

    @Test
    void publicRegistrationCreatesPendingResearcherAndNeverUsesRequestedRole() {
        when(users.existsByEmail("new@example.test")).thenReturn(false);
        when(institutions.existsById(20L)).thenReturn(true);
        when(roles.findByName("RESEARCHER_STUDENT"))
                .thenReturn(Optional.of(new Role("RESEARCHER_STUDENT", "researcher")));
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(30L);
            return saved;
        });

        UserRegistrationRequest request = new UserRegistrationRequest();
        request.setInstitutionId(20L);
        request.setEmail("New@Example.Test");
        request.setPassword("test-only-password");
        User created = service.register(request);

        assertEquals("new@example.test", created.getEmail());
        assertEquals(false, created.getMembershipApproved());
        verify(userRoles).save(argThat(link -> link.getUserId().equals(30L)));
        verify(roles).findByName("RESEARCHER_STUDENT");
        verifyNoMoreInteractions(roles);
    }

    @Test
    void approvedInstitutionAdministratorCanApprovePendingUserInSameInstitution() {
        stubAdministrator();
        User pending = user(22L, 20L, false);
        when(users.findById(22L)).thenReturn(Optional.of(pending));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User approved = service.approveMembership(22L, "ADMIN@example.test");

        assertEquals(true, approved.getMembershipApproved());
        verify(users).save(pending);
    }

    @Test
    void administratorCannotApproveSelfOrAnotherInstitution() {
        stubAdministrator();
        when(users.findById(21L)).thenReturn(Optional.of(user(21L, 20L, false)));
        when(users.findById(23L)).thenReturn(Optional.of(user(23L, 21L, false)));

        assertThrows(IllegalArgumentException.class,
                () -> service.approveMembership(21L, "admin@example.test"));
        assertThrows(IllegalArgumentException.class,
                () -> service.approveMembership(23L, "admin@example.test"));
        verify(users, never()).save(any());
    }

    @Test
    void onlyExplicitPendingMembershipCanBeApproved() {
        stubAdministrator();
        when(users.findById(24L)).thenReturn(Optional.of(user(24L, 20L, null)));

        assertThrows(IllegalArgumentException.class,
                () -> service.approveMembership(24L, "admin@example.test"));
        verify(users, never()).save(any());
    }

    @Test
    void pendingQueueIsQueriedOnlyForAdministratorInstitution() {
        stubAdministrator();
        when(users.findByInstitutionIdAndMembershipApprovedFalse(20L))
                .thenReturn(List.of(user(22L, 20L, false)));

        assertEquals(1, service.getPendingMemberships("admin@example.test").size());
        verify(users).findByInstitutionIdAndMembershipApprovedFalse(20L);
    }

    private void stubAdministrator() {
        User administrator = user(21L, 20L, null);
        when(users.findByEmail("admin@example.test")).thenReturn(Optional.of(administrator));
        when(userRoles.findByUserId(21L)).thenReturn(List.of(new UserRole(21L, (short) 3)));
        when(roles.findById((short) 3))
                .thenReturn(Optional.of(new Role("INSTITUTION_ADMIN", "admin")));
    }

    private User user(Long id, Long institutionId, Boolean approved) {
        User user = new User();
        user.setId(id);
        user.setEmail(id == 21L ? "admin@example.test" : "user@example.test");
        user.setInstitutionId(institutionId);
        user.setMembershipApproved(approved);
        return user;
    }
}
