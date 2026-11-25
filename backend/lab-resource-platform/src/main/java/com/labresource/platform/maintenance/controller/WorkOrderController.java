package com.labresource.platform.maintenance.controller;

import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.maintenance.entity.WorkOrder;
import com.labresource.platform.maintenance.entity.WorkOrderServiceLog;
import com.labresource.platform.maintenance.service.WorkOrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final ResourceAccessPolicy resourceAccess;

    public WorkOrderController(WorkOrderService workOrderService,
                               ResourceAccessPolicy resourceAccess) {
        this.workOrderService = workOrderService;
        this.resourceAccess = resourceAccess;
    }

    private void forbidSystemAdmin(Authentication authentication) {
        if (authentication != null && resourceAccess.hasAnyRole(authentication, "SYSTEM_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SYSTEM_ADMIN cannot access maintenance operations");
        }
    }

    @GetMapping
    public ResponseEntity<Page<WorkOrder>> getWorkOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Boolean assignedToMe,
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
        return ResponseEntity.ok(workOrderService.getWorkOrders(
                status, type, priority, equipmentId, assignedToMe, pageable, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkOrder> getWorkOrderById(
            @PathVariable Long id,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(workOrderService.getWorkOrderById(id, authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<WorkOrder> createWorkOrder(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Long equipmentId = body.get("equipmentId") != null ? Long.valueOf(body.get("equipmentId").toString()) : null;
        Long scheduleId = body.get("scheduleId") != null ? Long.valueOf(body.get("scheduleId").toString()) : null;
        String title = body.get("title") != null ? body.get("title").toString() : null;
        String description = body.get("description") != null ? body.get("description").toString() : null;
        String type = body.get("type") != null ? body.get("type").toString() : "CORRECTIVE";
        String priority = body.get("priority") != null ? body.get("priority").toString() : "MEDIUM";
        Long assignedTo = body.get("assignedTo") != null ? Long.valueOf(body.get("assignedTo").toString()) : null;

        LocalDateTime expectedEndTime = null;
        if (body.get("expectedEndTime") != null) {
            expectedEndTime = parseDateTime(body.get("expectedEndTime").toString());
        }

        WorkOrder created = workOrderService.createWorkOrder(
                equipmentId, scheduleId, title, description, type, priority, assignedTo, expectedEndTime, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<WorkOrder> assignTechnician(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Long technicianId = null;
        if (body != null && body.get("technicianId") != null) {
            technicianId = Long.valueOf(body.get("technicianId").toString());
        } else if (body != null && body.get("assignedTo") != null) {
            technicianId = Long.valueOf(body.get("assignedTo").toString());
        }

        WorkOrder updated = workOrderService.assignTechnician(id, technicianId, authentication.getName());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/start-preview")
    public ResponseEntity<Map<String, Object>> getStartPreview(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expectedEndTime,
            @RequestParam(required = false) String endTime,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        LocalDateTime resolvedEndTime = expectedEndTime;
        if (resolvedEndTime == null && endTime != null && !endTime.isBlank()) {
            resolvedEndTime = parseDateTime(endTime);
        }

        return ResponseEntity.ok(workOrderService.getStartPreview(id, resolvedEndTime, authentication.getName()));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<WorkOrder> startWorkOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        LocalDateTime expectedEndTime = null;
        if (body != null && body.get("expectedEndTime") != null) {
            expectedEndTime = parseDateTime(body.get("expectedEndTime").toString());
        }

        WorkOrder started = workOrderService.startWorkOrder(id, expectedEndTime, authentication.getName());
        return ResponseEntity.ok(started);
    }

    @PostMapping("/{id}/extend")
    public ResponseEntity<WorkOrder> extendWorkOrder(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        LocalDateTime newExpectedEndTime = null;
        if (body.get("newExpectedEndTime") != null) {
            newExpectedEndTime = parseDateTime(body.get("newExpectedEndTime").toString());
        } else if (body.get("expectedEndTime") != null) {
            newExpectedEndTime = parseDateTime(body.get("expectedEndTime").toString());
        }

        String reason = body.get("reason") != null ? body.get("reason").toString() : null;

        WorkOrder extended = workOrderService.extendWorkOrder(id, newExpectedEndTime, reason, authentication.getName());
        return ResponseEntity.ok(extended);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<WorkOrder> completeWorkOrder(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String resolutionNotes = null;
        BigDecimal cost = null;

        if (body != null) {
            if (body.get("resolutionNotes") != null) resolutionNotes = body.get("resolutionNotes").toString();
            else if (body.get("notes") != null) resolutionNotes = body.get("notes").toString();

            if (body.get("cost") != null) {
                try {
                    cost = new BigDecimal(body.get("cost").toString());
                } catch (Exception ignored) {}
            }
        }

        WorkOrder completed = workOrderService.completeWorkOrder(id, resolutionNotes, cost, authentication.getName());
        return ResponseEntity.ok(completed);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<WorkOrder> cancelWorkOrder(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String reason = null;
        if (body != null) {
            if (body.get("reason") != null) reason = body.get("reason").toString();
            else if (body.get("cancellationReason") != null) reason = body.get("cancellationReason").toString();
        }

        WorkOrder cancelled = workOrderService.cancelWorkOrder(id, reason, authentication.getName());
        return ResponseEntity.ok(cancelled);
    }

    @GetMapping("/{id}/service-logs")
    public ResponseEntity<List<WorkOrderServiceLog>> getServiceLogs(
            @PathVariable Long id,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(workOrderService.getServiceLogs(id, authentication.getName()));
    }

    @PostMapping("/{id}/service-logs")
    public ResponseEntity<WorkOrderServiceLog> addServiceLogNote(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String notes = body != null && body.get("notes") != null ? body.get("notes").toString() : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(workOrderService.addServiceLogNote(id, notes, authentication.getName()));
    }

    @GetMapping("/equipment/{equipmentId}/history")
    public ResponseEntity<Page<WorkOrder>> getEquipmentMaintenanceHistory(
            @PathVariable Long equipmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "completedAt,desc") String sort,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Sort sortObj = parseSort(sort, "completedAt");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sortObj);
        return ResponseEntity.ok(workOrderService.getEquipmentMaintenanceHistory(equipmentId, pageable, authentication.getName()));
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception e2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date-time format: " + raw);
            }
        }
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
