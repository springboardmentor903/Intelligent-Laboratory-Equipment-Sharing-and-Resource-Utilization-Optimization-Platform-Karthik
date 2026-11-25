package com.labresource.platform.user.controller;

import com.labresource.platform.maintenance.model.TechnicianWorkloadProjection;
import com.labresource.platform.maintenance.repository.WorkOrderRepository;
import com.labresource.platform.user.dto.PendingMembershipResponse;
import com.labresource.platform.user.dto.SystemAdminApplicationRequest;
import com.labresource.platform.user.dto.UserRegistrationRequest;
import com.labresource.platform.user.entity.User;
import com.labresource.platform.user.service.UserService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final ResourceAccessPolicy resourceAccess;
    private final WorkOrderRepository workOrderRepository;

    @Autowired
    public UserController(
            UserService userService,
            ResourceAccessPolicy resourceAccess,
            @Lazy WorkOrderRepository workOrderRepository) {
        this.userService = userService;
        this.resourceAccess = resourceAccess;
        this.workOrderRepository = workOrderRepository;
    }

    public UserController(UserService userService, ResourceAccessPolicy resourceAccess) {
        this(userService, resourceAccess, null);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody UserRegistrationRequest request) {

        User user = userService.register(request);

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("email", user.getEmail());
        response.put("fullName", user.getFullName());
        response.put("institutionId", user.getInstitutionId());
        response.put("departmentId", user.getDepartmentId());
        response.put("requestedRole", user.getRequestedRole());
        response.put("membershipStatus", "PENDING");
        response.put("message", "Registration submitted. An administrator must approve your membership before you can sign in.");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/apply-system-admin")
    public ResponseEntity<Map<String, Object>> applySystemAdmin(
            @Valid @RequestBody SystemAdminApplicationRequest request) {

        User user = userService.applySystemAdmin(request);

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("email", user.getEmail());
        response.put("fullName", user.getFullName());
        response.put("requestedRole", "SYSTEM_ADMIN");
        response.put("membershipStatus", "PENDING");
        response.put("message", "System Administrator application submitted. An existing System Administrator must approve your application.");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(userToMap(user));
    }

    @GetMapping("/technicians")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getTechnicians(Authentication authentication) {
        Long institutionId = resourceAccess.scopedInstitutionId(null, authentication);
        List<User> technicians = userService.getApprovedTechnicians(institutionId);

        List<Long> techIds = technicians.stream().map(User::getId).toList();
        Map<Long, TechnicianWorkloadProjection> workloadMap = (workOrderRepository != null && !techIds.isEmpty())
                ? workOrderRepository.findWorkloadByTechnicianIds(techIds, LocalDateTime.now()).stream()
                    .collect(Collectors.toMap(TechnicianWorkloadProjection::getTechnicianId, p -> p, (a, b) -> a))
                : Map.of();

        List<Map<String, Object>> result = technicians.stream()
                .map(u -> {
                    TechnicianWorkloadProjection p = workloadMap.get(u.getId());
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", u.getId());
                    map.put("fullName", u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : "Technician #" + u.getId());
                    map.put("email", u.getEmail());
                    map.put("institutionId", u.getInstitutionId() != null ? u.getInstitutionId() : 0L);
                    map.put("departmentId", u.getDepartmentId());
                    map.put("activeTasks", p != null && p.getActiveTasks() != null ? p.getActiveTasks() : 0L);
                    map.put("inProgressTasks", p != null && p.getInProgressTasks() != null ? p.getInProgressTasks() : 0L);
                    map.put("overdueTasks", p != null && p.getOverdueTasks() != null ? p.getOverdueTasks() : 0L);
                    map.put("totalTasks", p != null && p.getTotalTasks() != null ? p.getTotalTasks() : 0L);
                    map.put("nextScheduledDate", p != null ? p.getNextScheduledDate() : null);
                    return map;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateCurrentProfile(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        User user = userService.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.containsKey("departmentId")) {
            if (!resourceAccess.canAssignDepartment(user.getId(), authentication)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Department reassignment is restricted. Contact your institution administrator.");
            }
        }

        try {
            Long departmentId = request.containsKey("departmentId") && request.get("departmentId") != null
                    ? Long.valueOf(request.get("departmentId").toString())
                    : null;
            User updated = userService.updateProfile(user.getId(), departmentId);
            return ResponseEntity.ok(userToMap(updated));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAccess.canAccessUser(#id, authentication)")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id) {
        User user = userService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(userToMap(user));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        if (request.containsKey("departmentId")) {
            if (!resourceAccess.canAssignDepartment(id, authentication)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Department reassignment is restricted. Contact an authorized administrator.");
            }
        } else {
            if (!resourceAccess.ownsUser(id, authentication) && !resourceAccess.isSystemAdmin(authentication)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        }

        try {
            Long departmentId = request.containsKey("departmentId") && request.get("departmentId") != null
                    ? Long.valueOf(request.get("departmentId").toString())
                    : null;
            User updated = userService.updateProfile(id, departmentId);
            return ResponseEntity.ok(userToMap(updated));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/pending-membership")
    @PreAuthorize("isAuthenticated() and @resourceAccess.canManageMemberships(authentication)")
    public ResponseEntity<List<PendingMembershipResponse>> getPendingMemberships(
            Authentication authentication) {
        List<PendingMembershipResponse> pending = userService
                .getPendingMemberships(authentication.getName());
        return ResponseEntity.ok(pending);
    }

    @PatchMapping("/{userId}/membership/approve")
    @PreAuthorize("isAuthenticated() and @resourceAccess.canApproveMembership(#userId, authentication)")
    public ResponseEntity<?> approveMembership(
            @PathVariable Long userId,
            Authentication authentication) {
        try {
            User approved = userService.approveMembership(userId, authentication.getName());
            return ResponseEntity.ok(Map.of(
                    "id", approved.getId(),
                    "email", approved.getEmail(),
                    "fullName", approved.getFullName() != null ? approved.getFullName() : "",
                    "institutionId", approved.getInstitutionId(),
                    "requestedRole", approved.getRequestedRole() != null ? approved.getRequestedRole() : "",
                    "membershipStatus", "APPROVED",
                    "roles", userService.getRoleNames(approved.getId())
            ));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @PatchMapping("/{userId}/membership/reject")
    @PreAuthorize("isAuthenticated() and @resourceAccess.canApproveMembership(#userId, authentication)")
    public ResponseEntity<?> rejectMembership(
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        try {
            String reason = body != null ? body.get("reason") : null;
            User rejected = userService.rejectMembership(userId, authentication.getName(), reason);
            return ResponseEntity.ok(Map.of(
                    "id", rejected.getId(),
                    "email", rejected.getEmail(),
                    "fullName", rejected.getFullName() != null ? rejected.getFullName() : "",
                    "institutionId", rejected.getInstitutionId(),
                    "membershipStatus", "REJECTED"
            ));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    @PatchMapping("/{userId}/deactivate")
    @PreAuthorize("isAuthenticated() and @resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<?> deactivateUser(
            @PathVariable Long userId,
            Authentication authentication) {
        try {
            User deactivated = userService.deactivateUser(userId, authentication.getName());
            return ResponseEntity.ok(Map.of(
                    "id", deactivated.getId(),
                    "email", deactivated.getEmail(),
                    "membershipStatus", "DEACTIVATED",
                    "membershipApproved", false,
                    "message", "User deactivated successfully."
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/pending-system-admins")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<List<PendingMembershipResponse>> getPendingSystemAdmins() {
        return ResponseEntity.ok(userService.getPendingSystemAdmins());
    }

    @PatchMapping("/{userId}/system-admin/approve")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> approveSystemAdmin(
            @PathVariable Long userId,
            Authentication authentication) {
        try {
            User approved = userService.approveSystemAdmin(userId, authentication.getName());
            return ResponseEntity.ok(Map.of(
                    "id", approved.getId(),
                    "email", approved.getEmail(),
                    "fullName", approved.getFullName() != null ? approved.getFullName() : "",
                    "membershipStatus", "APPROVED",
                    "roles", userService.getRoleNames(approved.getId()),
                    "message", "System Administrator application approved."
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PatchMapping("/{userId}/system-admin/reject")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<?> rejectSystemAdmin(
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        try {
            String reason = body != null ? body.get("reason") : "Application rejected by platform administrator";
            User rejected = userService.rejectSystemAdmin(userId, authentication.getName(), reason);
            return ResponseEntity.ok(Map.of(
                    "id", rejected.getId(),
                    "email", rejected.getEmail(),
                    "membershipStatus", "REJECTED",
                    "message", "System Administrator application rejected."
            ));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/email-exists")
    public ResponseEntity<Map<String, Boolean>> emailExists(
            @RequestParam String email) {

        boolean exists = userService.emailExists(email);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("email", user.getEmail());
        result.put("fullName", user.getFullName());
        result.put("phoneNumber", user.getPhoneNumber());
        result.put("institutionId", user.getInstitutionId());
        result.put("departmentId", user.getDepartmentId());
        result.put("requestedRole", user.getRequestedRole());
        result.put("membershipStatus", user.getMembershipStatus());
        result.put("membershipApproved", user.getMembershipApproved());
        result.put("rejectionReason", user.getRejectionReason());
        result.put("roles", userService.getRoleNames(user.getId()));
        return result;
    }
}
