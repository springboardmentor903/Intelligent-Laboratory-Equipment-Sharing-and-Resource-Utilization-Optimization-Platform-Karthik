package com.labresource.platform.institution.controller;

import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.institution.entity.Department;
import com.labresource.platform.institution.service.DepartmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final ResourceAccessPolicy resourceAccess;

    public DepartmentController(DepartmentService departmentService,
                                ResourceAccessPolicy resourceAccess) {
        this.departmentService = departmentService;
        this.resourceAccess = resourceAccess;
    }

    @GetMapping
    public List<Department> getDepartments(
            @RequestParam(required = false) Long institutionId,
            Authentication authentication) {
        if (!resourceAccess.canSearchInstitution(institutionId, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Institution access denied");
        }
        Long scopedId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        return departmentService.getDepartmentsByInstitution(scopedId);
    }

    @GetMapping("/{id}")
    public Department getDepartmentById(@PathVariable Long id, Authentication authentication) {
        try {
            Department department = departmentService.getDepartmentById(id);
            if (!resourceAccess.canAccessInstitution(department.getInstitutionId(), authentication)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Department access denied");
            }
            return department;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PreAuthorize("hasAnyRole('INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessInstitution(#department.institutionId, authentication)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<?> createDepartment(@RequestBody Department department,
                                              Authentication authentication) {
        try {
            Department created = departmentService.createDepartment(department);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateDepartment(
            @PathVariable Long id,
            @RequestBody Department department,
            Authentication authentication) {
        try {
            Department existing = departmentService.getDepartmentById(id);
            if (!resourceAccess.canAccessInstitution(existing.getInstitutionId(), authentication)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Department access denied");
            }
            Department updated = departmentService.updateDepartment(id, department.getName());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PreAuthorize("hasAnyRole('INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDepartment(@PathVariable Long id, Authentication authentication) {
        try {
            Department existing = departmentService.getDepartmentById(id);
            if (!resourceAccess.canAccessInstitution(existing.getInstitutionId(), authentication)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Department access denied");
            }
            departmentService.deleteDepartment(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }
}
