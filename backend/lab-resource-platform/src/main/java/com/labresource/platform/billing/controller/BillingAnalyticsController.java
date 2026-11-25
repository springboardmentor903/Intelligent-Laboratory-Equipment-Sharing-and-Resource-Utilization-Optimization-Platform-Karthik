package com.labresource.platform.billing.controller;

import com.labresource.platform.billing.model.DepartmentAllocationResponse;
import com.labresource.platform.billing.model.EquipmentCostDTO;
import com.labresource.platform.billing.model.FinancialSummaryDTO;
import com.labresource.platform.billing.service.BillingAnalyticsService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/billing")
public class BillingAnalyticsController {

    private final BillingAnalyticsService analyticsService;
    private final ResourceAccessPolicy resourceAccess;

    public BillingAnalyticsController(BillingAnalyticsService analyticsService, ResourceAccessPolicy resourceAccess) {
        this.analyticsService = analyticsService;
        this.resourceAccess = resourceAccess;
    }

    @GetMapping("/costs/equipment")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'LAB_MANAGER')")
    public ResponseEntity<List<EquipmentCostDTO>> getEquipmentCosts(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {

        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        return ResponseEntity.ok(analyticsService.getEquipmentCosts(scopedInstId, departmentId, startDate, endDate));
    }

    @GetMapping("/allocations/department")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'LAB_MANAGER', 'DEPARTMENT_HEAD')")
    public ResponseEntity<DepartmentAllocationResponse> getDepartmentAllocations(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {

        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        return ResponseEntity.ok(analyticsService.getDepartmentAllocations(scopedInstId, startDate, endDate));
    }

    @GetMapping("/summary")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'LAB_MANAGER')")
    public ResponseEntity<FinancialSummaryDTO> getFinancialSummary(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication) {

        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        return ResponseEntity.ok(analyticsService.getFinancialSummary(scopedInstId, startDate, endDate));
    }
}
