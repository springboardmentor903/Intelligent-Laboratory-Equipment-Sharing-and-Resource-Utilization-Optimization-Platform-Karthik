package com.labresource.platform.billing.controller;

import com.labresource.platform.billing.model.ChargeStatus;
import com.labresource.platform.billing.model.ChargeType;
import com.labresource.platform.billing.model.ChargesPageResponse;
import com.labresource.platform.billing.service.UsageChargeService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/billing/charges")
public class UsageChargeController {

    private final UsageChargeService usageChargeService;
    private final ResourceAccessPolicy resourceAccess;

    public UsageChargeController(UsageChargeService usageChargeService, ResourceAccessPolicy resourceAccess) {
        this.usageChargeService = usageChargeService;
        this.resourceAccess = resourceAccess;
    }

    @GetMapping("/my")
    public ResponseEntity<ChargesPageResponse> getMyCharges(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) ChargeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Authentication authentication) {

        Long userId = resourceAccess.currentUserId(authentication);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), parseSort(sort));
        return ResponseEntity.ok(usageChargeService.getMyCharges(userId, startDate, endDate, status, pageable));
    }

    @GetMapping
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'LAB_MANAGER')")
    public ResponseEntity<ChargesPageResponse> getInstitutionalCharges(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) ChargeType chargeType,
            @RequestParam(required = false) ChargeStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Authentication authentication) {

        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), parseSort(sort));
        return ResponseEntity.ok(usageChargeService.getInstitutionalCharges(
                scopedInstId, departmentId, chargeType, status, startDate, endDate, pageable));
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}
