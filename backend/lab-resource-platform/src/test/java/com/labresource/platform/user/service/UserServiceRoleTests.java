package com.labresource.platform.user.service;

import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.entity.Institution;
import com.labresource.platform.institution.repository.DepartmentRepository;
import com.labresource.platform.institution.repository.InstitutionRepository;
import com.labresource.platform.user.dto.SystemAdminApplicationRequest;
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
class UserServiceRoleTests {

    @Mock private UserRepository users;
    @Mock private InstitutionRepository institutions;
    @Mock private UserRoleRepository userRoles;
    @Mock private RoleRepository roles;
    @Mock private DepartmentRepository departments;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(users, institutions, userRoles, roles, departments);
    }

    // 1. System Admin cannot be assigned an institution
    @Test
    void systemAdminCannotBeAssignedAnInstitution() {
        // A: Application creates a user with null institution_id and null department_id
        when(users.existsByEmail("newsys@platform.test")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemAdminApplicationRequest req = new SystemAdminApplicationRequest();
        req.setEmail("newsys@platform.test");
        req.setFullName("Platform Admin Candidate");
        req.setPassword("SecurePass123!");

        User created = service.applySystemAdmin(req);
        assertNull(created.getInstitutionId(), "System Admin applicant must have null institutionId");
        assertNull(created.getDepartmentId(), "System Admin applicant must have null departmentId");

        // B: Approving a System Admin ensures institutionId is set to null
        User reviewer = createApprovedUser(1L, "sysadmin@platform.test", null, "SYSTEM_ADMIN", (short) 6);
        when(users.findByEmail("sysadmin@platform.test")).thenReturn(Optional.of(reviewer));
        when(userRoles.findByUserId(1L)).thenReturn(List.of(new UserRole(1L, (short) 6)));
        when(roles.findById((short) 6)).thenReturn(Optional.of(new Role("SYSTEM_ADMIN", "System Admin")));

        User candidate = new User();
        candidate.setId(2L);
        candidate.setEmail("pending-sys@platform.test");
        candidate.setRequestedRole("SYSTEM_ADMIN");
        candidate.setMembershipApproved(false);
        candidate.setInstitutionId(10L); // simulate legacy / stray institution_id
        when(users.findById(2L)).thenReturn(Optional.of(candidate));
        when(userRoles.findByUserId(2L)).thenReturn(List.of());
        when(roles.findByName("SYSTEM_ADMIN")).thenReturn(Optional.of(new Role("SYSTEM_ADMIN", "System Admin")));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User approved = service.approveSystemAdmin(2L, "sysadmin@platform.test");
        assertNull(approved.getInstitutionId(), "Approved System Admin must have institutionId set to null");
        assertNull(approved.getDepartmentId(), "Approved System Admin must have departmentId set to null");
    }

    // 2. System Admin cannot be assigned a department
    @Test
    void systemAdminCannotBeAssignedADepartment() {
        User sysAdmin = createApprovedUser(5L, "sys@platform.test", null, "SYSTEM_ADMIN", (short) 6);
        when(users.findById(5L)).thenReturn(Optional.of(sysAdmin));
        when(userRoles.findByUserId(5L)).thenReturn(List.of(new UserRole(5L, (short) 6)));
        when(roles.findById((short) 6)).thenReturn(Optional.of(new Role("SYSTEM_ADMIN", "System Admin")));

        // Attempting to assign any department to a System Admin must throw IllegalArgumentException
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.updateProfile(5L, 101L)
        );
        assertTrue(ex.getMessage().contains("System Administrator cannot be assigned to any department"));

        // Updating profile with null department is allowed and preserves null institution
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User cleared = service.updateProfile(5L, null);
        assertNull(cleared.getDepartmentId());
        assertNull(cleared.getInstitutionId());
    }

    // 3. System Admin cannot simultaneously hold Institution Admin
    @Test
    void systemAdminCannotSimultaneouslyHoldInstitutionAdmin() {
        User reviewer = createApprovedUser(1L, "sysadmin@platform.test", null, "SYSTEM_ADMIN", (short) 6);
        when(users.findByEmail("sysadmin@platform.test")).thenReturn(Optional.of(reviewer));
        when(userRoles.findByUserId(1L)).thenReturn(List.of(new UserRole(1L, (short) 6)));
        when(roles.findById((short) 6)).thenReturn(Optional.of(new Role("SYSTEM_ADMIN", "System Admin")));

        // Candidate already has INSTITUTION_ADMIN role
        User candidate = new User();
        candidate.setId(3L);
        candidate.setEmail("instadmin@college.test");
        candidate.setRequestedRole("SYSTEM_ADMIN");
        candidate.setMembershipApproved(false);
        candidate.setInstitutionId(20L);

        when(users.findById(3L)).thenReturn(Optional.of(candidate));
        when(userRoles.findByUserId(3L)).thenReturn(List.of(new UserRole(3L, (short) 5)));
        when(roles.findById((short) 5)).thenReturn(Optional.of(new Role("INSTITUTION_ADMIN", "Institution Admin")));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.approveSystemAdmin(3L, "sysadmin@platform.test")
        );
        assertTrue(ex.getMessage().contains("INSTITUTION_ADMIN"));
    }

    // 4. Institution Admin cannot simultaneously hold System Admin
    @Test
    void institutionAdminCannotSimultaneouslyHoldSystemAdmin() {
        User instAdminApprover = createApprovedUser(10L, "approver@college.test", 20L, "INSTITUTION_ADMIN", (short) 5);
        when(users.findByEmail("approver@college.test")).thenReturn(Optional.of(instAdminApprover));
        when(userRoles.findByUserId(10L)).thenReturn(List.of(new UserRole(10L, (short) 5)));
        when(roles.findById((short) 5)).thenReturn(Optional.of(new Role("INSTITUTION_ADMIN", "Institution Admin")));

        // Target user currently holds SYSTEM_ADMIN role
        User target = new User();
        target.setId(11L);
        target.setEmail("dual@platform.test");
        target.setRequestedRole("INSTITUTION_ADMIN");
        target.setMembershipApproved(false);
        target.setInstitutionId(20L);

        when(users.findById(11L)).thenReturn(Optional.of(target));
        when(userRoles.findByUserId(11L)).thenReturn(List.of(new UserRole(11L, (short) 6)));
        when(roles.findById((short) 6)).thenReturn(Optional.of(new Role("SYSTEM_ADMIN", "System Admin")));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.approveMembership(11L, "approver@college.test")
        );
        assertTrue(ex.getMessage().contains("SYSTEM_ADMIN"));
    }

    // 5. Institution Admin cannot manage another institution
    @Test
    void institutionAdminCannotManageAnotherInstitution() {
        User instAdmin = createApprovedUser(10L, "admin20@college.test", 20L, "INSTITUTION_ADMIN", (short) 5);
        when(users.findByEmail("admin20@college.test")).thenReturn(Optional.of(instAdmin));
        when(userRoles.findByUserId(10L)).thenReturn(List.of(new UserRole(10L, (short) 5)));
        when(roles.findById((short) 5)).thenReturn(Optional.of(new Role("INSTITUTION_ADMIN", "Institution Admin")));

        // Target belongs to institution 21
        User targetOtherInst = new User();
        targetOtherInst.setId(22L);
        targetOtherInst.setInstitutionId(21L);
        targetOtherInst.setMembershipApproved(false);
        targetOtherInst.setRequestedRole("RESEARCHER_STUDENT");

        when(users.findById(22L)).thenReturn(Optional.of(targetOtherInst));
        when(userRoles.findByUserId(22L)).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.approveMembership(22L, "admin20@college.test")
        );
        assertTrue(ex.getMessage().contains("different institution"));
    }

    // 6. System Admin can manage platform-level operations
    @Test
    void systemAdminCanManagePlatformLevelOperations() {
        User sysAdmin = createApprovedUser(1L, "sysadmin@platform.test", null, "SYSTEM_ADMIN", (short) 6);
        when(users.findByEmail("sysadmin@platform.test")).thenReturn(Optional.of(sysAdmin));
        when(userRoles.findByUserId(1L)).thenReturn(List.of(new UserRole(1L, (short) 6)));
        when(roles.findById((short) 6)).thenReturn(Optional.of(new Role("SYSTEM_ADMIN", "System Admin")));

        // System Admin can approve user memberships in any institution (e.g. institution 99)
        User targetAnyInst = new User();
        targetAnyInst.setId(33L);
        targetAnyInst.setInstitutionId(99L);
        targetAnyInst.setMembershipApproved(false);
        targetAnyInst.setRequestedRole("RESEARCHER_STUDENT");

        when(users.findById(33L)).thenReturn(Optional.of(targetAnyInst));
        when(userRoles.findByUserId(33L)).thenReturn(List.of());
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(roles.findByName("RESEARCHER_STUDENT")).thenReturn(Optional.of(new Role("RESEARCHER_STUDENT", "Student")));

        User approved = service.approveMembership(33L, "sysadmin@platform.test");
        assertTrue(approved.getMembershipApproved());
        assertEquals("APPROVED", approved.getMembershipStatus());
        verify(userRoles).save(argThat(ur -> ur.getUserId().equals(33L)));
    }

    // 7. Normal users cannot assign either administrative role
    @Test
    void normalUsersCannotAssignEitherAdministrativeRole() {
        // A: Normal user registration cannot request SYSTEM_ADMIN
        when(users.existsByEmail("hacker@student.test")).thenReturn(false);
        when(institutions.existsById(10L)).thenReturn(true);

        UserRegistrationRequest req = new UserRegistrationRequest();
        req.setEmail("hacker@student.test");
        req.setPassword("Password123!");
        req.setInstitutionId(10L);
        req.setRequestedRole("SYSTEM_ADMIN");

        IllegalArgumentException regEx = assertThrows(IllegalArgumentException.class, () ->
                service.register(req)
        );
        assertTrue(regEx.getMessage().contains("System Admin role cannot be self-assigned"));

        // B: Normal user cannot approve memberships
        User student = createApprovedUser(40L, "student@college.test", 10L, "RESEARCHER_STUDENT", (short) 1);
        when(users.findByEmail("student@college.test")).thenReturn(Optional.of(student));
        when(userRoles.findByUserId(40L)).thenReturn(List.of(new UserRole(40L, (short) 1)));
        when(roles.findById((short) 1)).thenReturn(Optional.of(new Role("RESEARCHER_STUDENT", "Student")));

        IllegalArgumentException appEx = assertThrows(IllegalArgumentException.class, () ->
                service.approveMembership(41L, "student@college.test")
        );
        assertTrue(appEx.getMessage().contains("approved institution or system administrator is required"));

        // C: Normal user cannot approve System Admin applications
        IllegalArgumentException sysAppEx = assertThrows(IllegalArgumentException.class, () ->
                service.approveSystemAdmin(42L, "student@college.test")
        );
        assertTrue(sysAppEx.getMessage().contains("approved System Administrator is required"));
    }

    // 8. Institution registration and System Admin application workflows preserve role separation
    @Test
    void workflowsPreserveRoleSeparation() {
        // System Admin application workflow: null institution, null department, SYSTEM_ADMIN role
        when(users.existsByEmail("sysapplicant@platform.test")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        SystemAdminApplicationRequest sysReq = new SystemAdminApplicationRequest();
        sysReq.setEmail("sysapplicant@platform.test");
        sysReq.setFullName("Platform Admin Applicant");
        sysReq.setPassword("P@ssword123");

        User sysUser = service.applySystemAdmin(sysReq);
        assertNull(sysUser.getInstitutionId());
        assertNull(sysUser.getDepartmentId());
        assertEquals("SYSTEM_ADMIN", sysUser.getRequestedRole());
        assertEquals("PENDING", sysUser.getMembershipStatus());
        assertFalse(sysUser.getMembershipApproved());
    }

    // 9. Existing valid role assignments continue working
    @Test
    void existingValidRoleAssignmentsContinueWorking() {
        when(userRoles.findByUserId(17L)).thenReturn(List.of(
                new UserRole(17L, (short) 2),
                new UserRole(17L, (short) 1)));
        when(roles.findById((short) 2)).thenReturn(Optional.of(new Role("LAB_TECHNICIAN", "Lab Technician")));
        when(roles.findById((short) 1)).thenReturn(Optional.of(new Role("RESEARCHER_STUDENT", "Student")));

        assertEquals(
                List.of("LAB_TECHNICIAN", "RESEARCHER_STUDENT"),
                service.getRoleNames(17L));
        verify(userRoles).findByUserId(17L);
        verify(roles).findById((short) 1);
        verify(roles).findById((short) 2);
    }

    private User createApprovedUser(Long id, String email, Long institutionId, String roleName, short roleId) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setInstitutionId(institutionId);
        u.setMembershipApproved(true);
        u.setMembershipStatus("APPROVED");
        return u;
    }
}
