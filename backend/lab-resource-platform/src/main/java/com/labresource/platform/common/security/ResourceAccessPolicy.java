package com.labresource.platform.common.security;

import com.labresource.platform.booking.repository.BookingRepository;
import com.labresource.platform.equipment.repository.EquipmentRepository;
import com.labresource.platform.maintenance.repository.MaintenanceRepository;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.entity.UserRole;
import com.labresource.platform.user.entity.Role;
import com.labresource.platform.user.repository.RoleRepository;
import com.labresource.platform.user.repository.UserRepository;
import com.labresource.platform.user.repository.UserRoleRepository;
import com.labresource.platform.utilization.repository.UtilizationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;

/** Shared ownership and institution-scope checks used by controller method security. */
@Component("resourceAccess")
public class ResourceAccessPolicy {
    private final UserRepository users;
    private final EquipmentRepository equipment;
    private final BookingRepository bookings;
    private final UtilizationRepository utilization;
    private final MaintenanceRepository maintenance;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;

    public ResourceAccessPolicy(UserRepository users,
                                EquipmentRepository equipment,
                                BookingRepository bookings,
                                UtilizationRepository utilization,
                                MaintenanceRepository maintenance,
                                UserRoleRepository userRoles,
                                RoleRepository roles) {
        this.users = users;
        this.equipment = equipment;
        this.bookings = bookings;
        this.utilization = utilization;
        this.maintenance = maintenance;
        this.userRoles = userRoles;
        this.roles = roles;
    }

    public boolean hasAnyRole(Authentication auth, String... roles) {
        if (auth == null) return false;
        return Arrays.stream(roles).anyMatch(role -> auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role)));
    }

    public boolean isSystemAdmin(Authentication auth) {
        return hasAnyRole(auth, "SYSTEM_ADMIN") && isCurrentUserApproved(auth);
    }

    public Long currentUserId(Authentication auth) {
        if (!isCurrentUserApproved(auth)) return null;
        return currentUser(auth).map(User::getId).orElse(null);
    }

    public boolean ownsUser(Long userId, Authentication auth) {
        return isCurrentUserApproved(auth)
                && userId != null
                && Objects.equals(currentUserId(auth), userId);
    }

    public boolean canAccessInstitution(Long institutionId, Authentication auth) {
        if (institutionId == null || auth == null || !auth.isAuthenticated()
                || !isCurrentUserApproved(auth)) return false;
        if (isSystemAdmin(auth)) return true;
        return currentUser(auth).map(user -> user.getInstitutionId() != null
                && Objects.equals(user.getInstitutionId(), institutionId)).orElse(false);
    }

    public boolean canSearchInstitution(Long institutionId, Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || !isCurrentUserApproved(auth)) return false;
        return institutionId == null || canAccessInstitution(institutionId, auth);
    }

    public Long scopedInstitutionId(Long requestedInstitutionId, Authentication auth) {
        if (!isCurrentUserApproved(auth)) return null;
        if (isSystemAdmin(auth)) return requestedInstitutionId;
        return currentUser(auth).map(User::getInstitutionId).orElse(null);
    }

    public boolean canManageMemberships(Authentication auth) {
        if (!isCurrentUserApproved(auth)) return false;
        if (isSystemAdmin(auth)) return true;
        return hasAnyRole(auth, "INSTITUTION_ADMIN")
                && currentUser(auth).map(user -> user.getInstitutionId() != null).orElse(false);
    }

    public boolean canApproveMembership(Long userId, Authentication auth) {
        if (!canManageMemberships(auth) || userId == null) return false;
        User administrator = currentUser(auth).orElse(null);
        if (administrator == null || Objects.equals(administrator.getId(), userId)) return false;
        if (isSystemAdmin(auth)) return true;
        return users.findById(userId)
                .map(target -> Objects.equals(
                        administrator.getInstitutionId(), target.getInstitutionId()))
                .orElse(false);
    }

    public boolean canAssignDepartment(Long targetUserId, Authentication auth) {
        if (targetUserId == null || auth == null || !isCurrentUserApproved(auth)) return false;
        if (isSystemAdminUser(targetUserId)) return false;
        if (isSystemAdmin(auth)) return true;
        if (!hasAnyRole(auth, "INSTITUTION_ADMIN")) return false;
        User administrator = currentUser(auth).orElse(null);
        if (administrator == null || administrator.getInstitutionId() == null) return false;
        return users.findById(targetUserId)
                .map(target -> Objects.equals(administrator.getInstitutionId(), target.getInstitutionId()))
                .orElse(false);
    }

    public boolean isSystemAdminUser(Long userId) {
        if (userId == null) return false;
        return userRoles.findByUserId(userId).stream()
                .map(UserRole::getRoleId)
                .map(roles::findById)
                .flatMap(java.util.Optional::stream)
                .map(Role::getName)
                .anyMatch("SYSTEM_ADMIN"::equals);
    }

    public boolean canAccessUser(Long userId, Authentication auth) {
        if (userId == null || auth == null || !isCurrentUserApproved(auth)) return false;
        if (ownsUser(userId, auth) || isSystemAdmin(auth)) return true;
        if (!hasAnyRole(auth, "LAB_TECHNICIAN", "LAB_MANAGER",
                "DEPARTMENT_HEAD", "INSTITUTION_ADMIN")) return false;
        return users.findById(userId)
                .map(user -> canAccessInstitution(user.getInstitutionId(), auth))
                .orElse(false);
    }

    public boolean isTechnician(Long userId) {
        if (userId == null) return false;
        return userRoles.findByUserId(userId).stream()
                .map(UserRole::getRoleId)
                .map(roles::findById)
                .flatMap(java.util.Optional::stream)
                .map(Role::getName)
                .anyMatch("LAB_TECHNICIAN"::equals);
    }

    public boolean canCreateEquipment(Long institutionId, Authentication auth) {
        return canAccessInstitution(institutionId, auth);
    }

    public boolean canAccessEquipment(Long equipmentId, Authentication auth) {
        if (equipmentId == null || auth == null || !auth.isAuthenticated()) return false;
        if (isSystemAdmin(auth)) return true;
        return equipment.findById(equipmentId)
                .map(item -> canAccessInstitution(item.getInstitutionId(), auth))
                .orElse(false);
    }

    public boolean canAccessBooking(Long bookingId, Authentication auth) {
        if (bookingId == null || auth == null || !isCurrentUserApproved(auth)) return false;
        return bookings.findById(bookingId).map(booking ->
                (hasAnyRole(auth, "RESEARCHER_STUDENT")
                        && Objects.equals(currentUserId(auth), booking.getUserId()))
                        || (hasAnyRole(auth, "LAB_TECHNICIAN", "LAB_MANAGER",
                        "DEPARTMENT_HEAD", "INSTITUTION_ADMIN", "SYSTEM_ADMIN")
                        && canAccessEquipment(booking.getEquipmentId(), auth)))
                .orElse(false);
    }

    public boolean ownsBooking(Long bookingId, Authentication auth) {
        if (bookingId == null || auth == null || !isCurrentUserApproved(auth)) return false;
        return bookings.findById(bookingId)
                .map(booking -> Objects.equals(currentUserId(auth), booking.getUserId()))
                .orElse(false);
    }

    public boolean canAccessUtilization(Long recordId, Authentication auth) {
        if (recordId == null || auth == null || !isCurrentUserApproved(auth)) return false;
        return utilization.findById(recordId).map(record ->
                (hasAnyRole(auth, "RESEARCHER_STUDENT")
                        && Objects.equals(currentUserId(auth), record.getUserId()))
                        || (hasAnyRole(auth, "LAB_TECHNICIAN", "LAB_MANAGER",
                        "DEPARTMENT_HEAD", "INSTITUTION_ADMIN", "SYSTEM_ADMIN")
                        && canAccessEquipment(record.getEquipmentId(), auth)))
                .orElse(false);
    }

    public boolean canAccessMaintenance(Long recordId, Authentication auth) {
        if (recordId == null || auth == null || !isCurrentUserApproved(auth)) return false;
        return maintenance.findById(recordId).map(record ->
                (hasAnyRole(auth, "RESEARCHER_STUDENT")
                        && Objects.equals(currentUserId(auth), record.getReportedBy()))
                        || (hasAnyRole(auth, "LAB_TECHNICIAN", "LAB_MANAGER",
                        "DEPARTMENT_HEAD", "INSTITUTION_ADMIN", "SYSTEM_ADMIN")
                        && canAccessEquipment(record.getEquipmentId(), auth)))
                .orElse(false);
    }

    private java.util.Optional<User> currentUser(Authentication auth) {
        if (auth == null || auth.getName() == null) return java.util.Optional.empty();
        return users.findByEmail(auth.getName().trim().toLowerCase());
    }

    private boolean isCurrentUserApproved(Authentication auth) {
        return currentUser(auth)
                .map(user -> !Boolean.FALSE.equals(user.getMembershipApproved()))
                .orElse(false);
    }
}
