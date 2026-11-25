package com.labresource.platform.maintenance.controller;

import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.maintenance.entity.MaintenanceRequest;
import com.labresource.platform.maintenance.service.MaintenanceRequestService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/maintenance/requests")
public class MaintenanceRequestController {

    private final MaintenanceRequestService requestService;
    private final ResourceAccessPolicy resourceAccess;

    public MaintenanceRequestController(MaintenanceRequestService requestService,
                                        ResourceAccessPolicy resourceAccess) {
        this.requestService = requestService;
        this.resourceAccess = resourceAccess;
    }

    private void forbidSystemAdmin(Authentication authentication) {
        if (authentication != null && resourceAccess.hasAnyRole(authentication, "SYSTEM_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SYSTEM_ADMIN cannot access maintenance operations");
        }
    }

    @PostMapping
    public ResponseEntity<MaintenanceRequest> submitRequest(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Long equipmentId = body.get("equipmentId") != null ? Long.valueOf(body.get("equipmentId").toString()) : null;
        String title = body.get("title") != null ? body.get("title").toString() : null;
        String description = body.get("description") != null ? body.get("description").toString() : null;
        String priority = body.get("priority") != null ? body.get("priority").toString() : "MEDIUM";

        MaintenanceRequest created = requestService.submitRequest(equipmentId, title, description, priority, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/mine")
    public ResponseEntity<Page<MaintenanceRequest>> getMyRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Sort sortObj = parseSort(sort, "createdAt");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sortObj);
        return ResponseEntity.ok(requestService.getMyRequests(authentication.getName(), status, equipmentId, priority, pageable));
    }

    @GetMapping("/queue")
    public ResponseEntity<Page<MaintenanceRequest>> getQueue(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Sort sortObj = parseSort(sort, "createdAt");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sortObj);
        return ResponseEntity.ok(requestService.getQueue(authentication.getName(), status, equipmentId, priority, pageable));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<MaintenanceRequest> approveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String remarks = null;
        if (body != null) {
            if (body.get("remarks") != null) remarks = body.get("remarks").toString();
            else if (body.get("notes") != null) remarks = body.get("notes").toString();
        }

        MaintenanceRequest approved = requestService.approveRequest(id, remarks, authentication.getName());
        return ResponseEntity.ok(approved);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<MaintenanceRequest> rejectRequest(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String remarks = null;
        if (body != null) {
            if (body.get("remarks") != null) remarks = body.get("remarks").toString();
            else if (body.get("rejectionReason") != null) remarks = body.get("rejectionReason").toString();
            else if (body.get("reason") != null) remarks = body.get("reason").toString();
        }

        MaintenanceRequest rejected = requestService.rejectRequest(id, remarks, authentication.getName());
        return ResponseEntity.ok(rejected);
    }

    private Sort parseSort(String sortParam, String defaultProperty) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.DESC, defaultProperty);
        }
        String[] parts = sortParam.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }
}
