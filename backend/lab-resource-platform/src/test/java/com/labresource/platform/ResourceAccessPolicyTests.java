package com.labresource.platform;

import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.entity.Equipment;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.maintenance.entity.Maintenance;
import com.labresource.platform.maintenance.repository.MaintenanceRepository;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceAccessPolicyTests {
    @Mock private UserRepository users;
    @Mock private EquipmentRepository equipment;
    @Mock private BookingRepository bookings;
    @Mock private UtilizationRepository utilization;
    @Mock private MaintenanceRepository maintenance;
    @Mock private UserRoleRepository userRoles;
    @Mock private RoleRepository roles;

    private ResourceAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ResourceAccessPolicy(users, equipment, bookings,
                utilization, maintenance, userRoles, roles);
    }

    @Test
    void researcherCanAccessOwnUserButNotAnotherUser() {
        User self = user(4L, 20L);
        when(users.findByEmail("researcher@example.test"))
                .thenReturn(Optional.of(self));
        var researcher = auth("researcher@example.test", "RESEARCHER_STUDENT");

        assertTrue(policy.ownsUser(4L, researcher));
        assertFalse(policy.ownsUser(5L, researcher));
        assertTrue(policy.canAccessUser(4L, researcher));
        assertFalse(policy.canAccessUser(5L, researcher));
    }

    @Test
    void staffResourceReadsAreInstitutionScoped() {
        when(users.findByEmail("tech@example.test"))
                .thenReturn(Optional.of(user(6L, 20L)));
        Equipment sameInstitution = new Equipment();
        sameInstitution.setId(10L);
        sameInstitution.setInstitutionId(20L);
        Equipment otherInstitution = new Equipment();
        otherInstitution.setId(11L);
        otherInstitution.setInstitutionId(21L);
        when(equipment.findById(10L)).thenReturn(Optional.of(sameInstitution));
        when(equipment.findById(11L)).thenReturn(Optional.of(otherInstitution));
        var technician = auth("tech@example.test", "LAB_TECHNICIAN");

        assertTrue(policy.canAccessEquipment(10L, technician));
        assertFalse(policy.canAccessEquipment(11L, technician));
    }

    @Test
    void departmentHeadUsesInstitutionScopeAndSystemAdminIsGlobal() {
        when(users.findByEmail("head@example.test"))
                .thenReturn(Optional.of(user(7L, 20L)));
        Equipment otherInstitution = new Equipment();
        otherInstitution.setInstitutionId(21L);
        when(equipment.findById(11L)).thenReturn(Optional.of(otherInstitution));
        when(users.findByEmail("system@example.test"))
                .thenReturn(Optional.of(user(8L, 20L)));

        assertFalse(policy.canAccessEquipment(11L,
                auth("head@example.test", "DEPARTMENT_HEAD")));
        assertTrue(policy.canAccessEquipment(11L,
                auth("system@example.test", "SYSTEM_ADMIN")));
    }

    @Test
    void pendingMemberCannotAccessInstitutionOrOwnedResourceScopes() {
        User pending = user(9L, 20L);
        pending.setMembershipApproved(false);
        when(users.findByEmail("pending@example.test"))
                .thenReturn(Optional.of(pending));
        var authentication = auth("pending@example.test", "RESEARCHER_STUDENT");

        assertFalse(policy.canSearchInstitution(null, authentication));
        assertFalse(policy.canAccessInstitution(20L, authentication));
        assertFalse(policy.canAccessEquipment(11L, authentication));
        assertFalse(policy.ownsUser(9L, authentication));
        assertNull(policy.currentUserId(authentication));
    }

    @Test
    void legacyNullApprovalPreservesExistingApprovedUserAccess() {
        when(users.findByEmail("legacy@example.test"))
                .thenReturn(Optional.of(user(10L, 20L)));

        assertTrue(policy.canAccessInstitution(20L,
                auth("legacy@example.test", "RESEARCHER_STUDENT")));
    }

    @Test
    void institutionAdministratorCanApproveOnlyAnotherUserInTheirInstitution() {
        User administrator = user(11L, 20L);
        administrator.setMembershipApproved(true);
        User localPendingUser = user(12L, 20L);
        localPendingUser.setMembershipApproved(false);
        User remotePendingUser = user(13L, 21L);
        remotePendingUser.setMembershipApproved(false);
        when(users.findByEmail("admin@example.test"))
                .thenReturn(Optional.of(administrator));
        when(users.findById(12L)).thenReturn(Optional.of(localPendingUser));
        when(users.findById(13L)).thenReturn(Optional.of(remotePendingUser));
        var authentication = auth("admin@example.test", "INSTITUTION_ADMIN");

        assertTrue(policy.canManageMemberships(authentication));
        assertTrue(policy.canApproveMembership(12L, authentication));
        assertFalse(policy.canApproveMembership(13L, authentication));
        assertFalse(policy.canApproveMembership(11L, authentication));
    }

    @Test
    void researcherCanReadOnlyOwnedUtilizationAndReportedMaintenance() {
        when(users.findByEmail("researcher@example.test"))
                .thenReturn(Optional.of(user(4L, 20L)));
        Utilization ownUsage = new Utilization();
        ownUsage.setId(30L);
        ownUsage.setUserId(4L);
        ownUsage.setEquipmentId(10L);
        Utilization otherUsage = new Utilization();
        otherUsage.setId(31L);
        otherUsage.setUserId(5L);
        when(utilization.findById(30L)).thenReturn(Optional.of(ownUsage));
        when(utilization.findById(31L)).thenReturn(Optional.of(otherUsage));
        Maintenance ownReport = new Maintenance();
        ownReport.setId(40L);
        ownReport.setReportedBy(4L);
        ownReport.setEquipmentId(10L);
        Maintenance otherReport = new Maintenance();
        otherReport.setId(41L);
        otherReport.setReportedBy(5L);
        when(maintenance.findById(40L)).thenReturn(Optional.of(ownReport));
        when(maintenance.findById(41L)).thenReturn(Optional.of(otherReport));
        var researcher = auth("researcher@example.test", "RESEARCHER_STUDENT");

        assertTrue(policy.canAccessUtilization(30L, researcher));
        assertFalse(policy.canAccessUtilization(31L, researcher));
        assertTrue(policy.canAccessMaintenance(40L, researcher));
        assertFalse(policy.canAccessMaintenance(41L, researcher));
    }

    @Test
    void departmentAssignmentIsRestrictedToAdminsWithinSameInstitution() {
        User instAdmin = user(50L, 20L);
        instAdmin.setMembershipApproved(true);
        User studentSameInst = user(51L, 20L);
        studentSameInst.setMembershipApproved(true);
        User studentOtherInst = user(52L, 21L);
        studentOtherInst.setMembershipApproved(true);

        when(users.findByEmail("admin20@example.test")).thenReturn(Optional.of(instAdmin));
        when(users.findByEmail("student@example.test")).thenReturn(Optional.of(studentSameInst));
        when(users.findById(51L)).thenReturn(Optional.of(studentSameInst));
        when(users.findById(52L)).thenReturn(Optional.of(studentOtherInst));

        var adminAuth = auth("admin20@example.test", "INSTITUTION_ADMIN");
        var studentAuth = auth("student@example.test", "RESEARCHER_STUDENT");
        var sysAdminAuth = auth("sysadmin@example.test", "SYSTEM_ADMIN");
        User sysUser = user(99L, 1L);
        sysUser.setMembershipApproved(true);
        when(users.findByEmail("sysadmin@example.test")).thenReturn(Optional.of(sysUser));

        // Institution Admin can assign department to user in same institution
        assertTrue(policy.canAssignDepartment(51L, adminAuth));
        // Institution Admin cannot assign department to user in another institution
        assertFalse(policy.canAssignDepartment(52L, adminAuth));
        // Student/Researcher cannot assign department
        assertFalse(policy.canAssignDepartment(51L, studentAuth));
        // System Admin can assign department
        assertTrue(policy.canAssignDepartment(51L, sysAdminAuth));
        assertTrue(policy.canAssignDepartment(52L, sysAdminAuth));
    }

    @Test
    void systemAdminCannotBeAssignedADepartmentViaPolicy() {
        var sysAdminAuth = auth("sysadmin@example.test", "SYSTEM_ADMIN");
        User sysUser = user(99L, null);
        sysUser.setMembershipApproved(true);
        when(users.findByEmail("sysadmin@example.test")).thenReturn(Optional.of(sysUser));

        when(userRoles.findByUserId(100L)).thenReturn(List.of(new UserRole(100L, (short) 6)));
        when(roles.findById((short) 6)).thenReturn(Optional.of(new Role("SYSTEM_ADMIN", "System Admin")));

        // Even a System Admin cannot assign a department to a user who holds the SYSTEM_ADMIN role
        assertFalse(policy.canAssignDepartment(100L, sysAdminAuth));
    }

    private User user(Long id, Long institutionId) {
        User user = new User();
        user.setId(id);
        user.setInstitutionId(institutionId);
        return user;
    }

    private UsernamePasswordAuthenticationToken auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
