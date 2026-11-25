package com.labresource.platform.maintenance.controller;

import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.maintenance.model.TechnicianTasksResponse;
import com.labresource.platform.maintenance.model.TechnicianWorkloadDTO;
import com.labresource.platform.maintenance.service.WorkOrderService;
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
@RequestMapping("/api/technicians")
public class TechnicianController {

    private final WorkOrderService workOrderService;
    private final ResourceAccessPolicy resourceAccess;

    public TechnicianController(WorkOrderService workOrderService, ResourceAccessPolicy resourceAccess) {
        this.workOrderService = workOrderService;
        this.resourceAccess = resourceAccess;
    }

    @GetMapping
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_MANAGER', 'INSTITUTION_ADMIN')")
    public ResponseEntity<List<TechnicianWorkloadDTO>> getTechniciansWorkload(
            @RequestParam(required = false) Long institutionId,
            Authentication authentication) {
        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        return ResponseEntity.ok(workOrderService.getTechniciansWorkload(scopedInstId, authentication.getName()));
    }

    @GetMapping("/workload")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_MANAGER', 'INSTITUTION_ADMIN')")
    public ResponseEntity<List<TechnicianWorkloadDTO>> getWorkload(
            @RequestParam(required = false) Long institutionId,
            Authentication authentication) {
        Long scopedInstId = resourceAccess.scopedInstitutionId(institutionId, authentication);
        return ResponseEntity.ok(workOrderService.getTechniciansWorkload(scopedInstId, authentication.getName()));
    }

    @GetMapping("/my-tasks")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN')")
    public ResponseEntity<TechnicianTasksResponse> getMyTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String workOrderType,
            @RequestParam(required = false) String taskGroup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "priority,asc") String sort,
            Authentication authentication) {

        Sort sortObj = parseSort(sort);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sortObj);

        TechnicianTasksResponse response = workOrderService.getTechnicianTasks(
                null, status, priority, workOrderType, taskGroup, pageable, authentication.getName()
        );
        return ResponseEntity.ok(response);
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "scheduledDate");
        }
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
