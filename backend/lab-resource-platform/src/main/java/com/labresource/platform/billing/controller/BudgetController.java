package com.labresource.platform.billing.controller;

import com.labresource.platform.billing.entity.DepartmentBudget;
import com.labresource.platform.billing.model.BudgetOverviewItem;
import com.labresource.platform.billing.model.CreateBudgetRequest;
import com.labresource.platform.billing.service.BudgetService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final ResourceAccessPolicy resourceAccess;

    public BudgetController(BudgetService budgetService, ResourceAccessPolicy resourceAccess) {
        this.budgetService = budgetService;
        this.resourceAccess = resourceAccess;
    }

    @GetMapping
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'LAB_MANAGER', 'DEPARTMENT_HEAD')")
    public ResponseEntity<List<BudgetOverviewItem>> getBudgets(
            @RequestParam(required = false) Long institutionId,
            @RequestParam(required = false) Integer fiscalYear,
            Authentication authentication) {

        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        return ResponseEntity.ok(budgetService.getBudgetsOverview(scopedInstId, fiscalYear));
    }

    @GetMapping("/department/{departmentId}")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN', 'LAB_MANAGER', 'DEPARTMENT_HEAD')")
    public ResponseEntity<BudgetOverviewItem> getDepartmentBudget(
            @PathVariable Long departmentId,
            @RequestParam(required = false) Integer fiscalYear,
            Authentication authentication) {

        User user = resourceAccess.currentUser(authentication).orElse(null);
        if (resourceAccess.hasAnyRole(authentication, "DEPARTMENT_HEAD")) {
            if (user == null || user.getDepartmentId() == null || !user.getDepartmentId().equals(departmentId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Department heads can only view their own department budget.");
            }
        }
        return ResponseEntity.ok(budgetService.getDepartmentBudget(departmentId, fiscalYear));
    }

    @PostMapping
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN')")
    public ResponseEntity<DepartmentBudget> createBudget(
            @RequestParam(required = false) Long institutionId,
            @RequestBody CreateBudgetRequest request,
            Authentication authentication) {

        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        DepartmentBudget created = budgetService.createBudget(scopedInstId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'INSTITUTION_ADMIN')")
    public ResponseEntity<DepartmentBudget> updateBudget(
            @PathVariable Long id,
            @RequestParam(required = false) Long institutionId,
            @RequestBody Map<String, BigDecimal> body,
            Authentication authentication) {

        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        BigDecimal amount = body.get("allocatedAmount");
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "allocatedAmount is required in body.");
        }
        return ResponseEntity.ok(budgetService.updateBudget(id, amount, scopedInstId));
    }
}
