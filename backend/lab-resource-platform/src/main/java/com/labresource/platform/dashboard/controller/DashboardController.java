package com.labresource.platform.dashboard.controller;

import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.dashboard.dto.DashboardDTOs.*;
import com.labresource.platform.dashboard.service.DashboardService;
import com.labresource.platform.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ResourceAccessPolicy resourceAccess;

    public DashboardController(DashboardService dashboardService, ResourceAccessPolicy resourceAccess) {
        this.dashboardService = dashboardService;
        this.resourceAccess = resourceAccess;
    }

    @GetMapping("/researcher")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResearcherDashboardResponse> getResearcherDashboard(Authentication authentication) {
        User user = resourceAccess.currentUser(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session not found"));
        return ResponseEntity.ok(dashboardService.getResearcherDashboard(user));
    }

    @GetMapping("/manager")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ManagerDashboardResponse> getManagerDashboard(
            @RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) Long departmentId,
            Authentication authentication) {
        User user = resourceAccess.currentUser(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session not found"));
        return ResponseEntity.ok(dashboardService.getManagerDashboard(user, days, departmentId));
    }

    @GetMapping("/institution")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<InstitutionDashboardResponse> getInstitutionDashboard(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false, defaultValue = "30") Integer days,
            Authentication authentication) {
        Long scopedInstId = resolveAndValidateInstitution(institutionId, authentication);
        return ResponseEntity.ok(dashboardService.getInstitutionDashboard(scopedInstId, days));
    }

    @GetMapping("/institution/procurement")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<ProcurementRecommendationDTO>> getProcurementRecommendations(
            @RequestParam(required = false) Long institutionId,
            Authentication authentication) {
        Long scopedInstId = resolveAndValidateInstitution(institutionId, authentication);
        return ResponseEntity.ok(dashboardService.getProcurementRecommendations(scopedInstId));
    }

    @GetMapping("/institution/lifecycle")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<Page<EquipmentLifecycleDTO>> getEquipmentLifecycleAndRoi(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,asc") String sort,
            Authentication authentication) {
        Long scopedInstId = resolveAndValidateInstitution(institutionId, authentication);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), parseSort(sort));
        return ResponseEntity.ok(dashboardService.getEquipmentLifecycleAndRoi(scopedInstId, pageable));
    }

    @GetMapping("/institution/system")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<SystemMonitoringResponse> getSystemMonitoring(
            @RequestParam(required = false) Long institutionId,
            Authentication authentication) {
        Long scopedInstId = resolveAndValidateInstitution(institutionId, authentication);
        return ResponseEntity.ok(dashboardService.getSystemMonitoring(scopedInstId));
    }

    private Long resolveAndValidateInstitution(Long requestedId, Authentication authentication) {
        User user = resourceAccess.currentUser(authentication)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session not found"));

        if (resourceAccess.isSystemAdmin(authentication)) {
            if (requestedId != null) {
                return requestedId;
            }
            if (user.getInstitutionId() != null) {
                return user.getInstitutionId();
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "institutionId parameter is required for System Administrator without assigned institution.");
        }

        // INSTITUTION_ADMIN must have an institution and cannot access another institution
        Long userInstId = user.getInstitutionId();
        if (userInstId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not assigned to an institution.");
        }
        if (requestedId != null && !requestedId.equals(userInstId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to foreign institution data.");
        }
        return userInstId;
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "id");
        }
        String[] parts = sort.split(",");
        String prop = parts[0].trim();
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(dir, prop);
    }
}
