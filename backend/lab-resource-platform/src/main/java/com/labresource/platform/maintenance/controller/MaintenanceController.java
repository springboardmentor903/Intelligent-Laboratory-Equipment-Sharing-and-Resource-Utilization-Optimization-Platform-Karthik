
package com.labresource.platform.maintenance.controller;

import com.labresource.platform.maintenance.entity.Maintenance;
import com.labresource.platform.maintenance.service.MaintenanceService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;
    private final ResourceAccessPolicy resourceAccess;

    public MaintenanceController(MaintenanceService maintenanceService,
                                 ResourceAccessPolicy resourceAccess) {
        this.maintenanceService = maintenanceService;
        this.resourceAccess = resourceAccess;
    }

    @GetMapping
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<Maintenance>> getAllMaintenanceRecords() {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(maintenanceService.getAllMaintenanceRecords().stream()
                .filter(record -> resourceAccess.canAccessEquipment(record.getEquipmentId(), auth))
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAccess.canAccessMaintenance(#id, authentication)")
    public ResponseEntity<?> getMaintenanceById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(
                    maintenanceService.getMaintenanceById(id)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/equipment/{equipmentId}")
    @PreAuthorize("@resourceAccess.canAccessEquipment(#equipmentId, authentication) and @resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<Maintenance>> getByEquipment(
            @PathVariable Long equipmentId) {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(maintenanceService.getByEquipment(equipmentId).stream()
                .filter(record -> resourceAccess.canAccessMaintenance(record.getId(), auth))
                .collect(Collectors.toList()));
    }

    @GetMapping("/reporter/{reportedBy}")
    @PreAuthorize("@resourceAccess.ownsUser(#reportedBy, authentication) or (@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessUser(#reportedBy, authentication))")
    public ResponseEntity<List<Maintenance>> getByReporter(
            @PathVariable Long reportedBy) {
        return scoped(maintenanceService.getByReporter(reportedBy));
    }

    @GetMapping("/technician/{assignedTo}")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessUser(#assignedTo, authentication)")
    public ResponseEntity<List<Maintenance>> getByTechnician(
            @PathVariable Long assignedTo) {
        return scoped(maintenanceService.getByTechnician(assignedTo));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<Maintenance>> getByStatus(
            @PathVariable String status) {
        return scoped(maintenanceService.getByStatus(status));
    }

    @GetMapping("/priority/{priority}")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<Maintenance>> getByPriority(
            @PathVariable String priority) {
        return scoped(maintenanceService.getByPriority(priority));
    }

    @PostMapping
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessEquipment(#maintenance.equipmentId, authentication)")
    public ResponseEntity<?> createMaintenance(
            @RequestBody Maintenance maintenance,
            Authentication authentication) {
        try {
            maintenance.setReportedBy(resourceAccess.currentUserId(authentication));
            Maintenance created =
                    maintenanceService.createMaintenance(maintenance);

            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessMaintenance(#id, authentication)")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            return ResponseEntity.ok(
                    maintenanceService.updateStatus(
                            id, request.get("status")
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessMaintenance(#id, authentication)")
    public ResponseEntity<?> deleteMaintenance(@PathVariable Long id) {
        try {
            maintenanceService.deleteMaintenance(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }


    @PatchMapping("/{id}/assign")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessMaintenance(#id, authentication) and @resourceAccess.canAccessUser(#request.technicianId, authentication) and @resourceAccess.isTechnician(#request.technicianId)")
    public ResponseEntity<?> assignTechnician(
            @PathVariable Long id,
            @RequestBody Map<String, Long> request) {
        try {
            return ResponseEntity.ok(
                    maintenanceService.assignTechnician(
                            id, request.get("technicianId")
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }


    @GetMapping("/summary")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getMaintenanceSummary() {
        var all = getAllMaintenanceRecords().getBody();
        Map<String, Object> summary = new java.util.HashMap<>();

        summary.put(
                "totalRecords",
                all.size()
        );
        summary.put(
                "reported",
                all.stream().filter(record -> "REPORTED".equals(record.getStatus())).count()
        );
        summary.put(
                "assigned",
                all.stream().filter(record -> "ASSIGNED".equals(record.getStatus())).count()
        );
        summary.put(
                "inProgress",
                all.stream().filter(record -> "IN_PROGRESS".equals(record.getStatus())).count()
        );
        summary.put(
                "completed",
                all.stream().filter(record -> "COMPLETED".equals(record.getStatus())).count()
        );
        summary.put(
                "cancelled",
                all.stream().filter(record -> "CANCELLED".equals(record.getStatus())).count()
        );
        summary.put(
                "highPriority",
                all.stream().filter(record -> "HIGH".equals(record.getPriority())).count()
        );
        summary.put(
                "criticalPriority",
                all.stream().filter(record -> "CRITICAL".equals(record.getPriority())).count()
        );

        return ResponseEntity.ok(summary);
    }


    @PatchMapping("/{id}/resolution")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessMaintenance(#id, authentication)")
    public ResponseEntity<?> updateResolutionNotes(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            return ResponseEntity.ok(
                    maintenanceService.updateResolutionNotes(
                            id, request.get("resolutionNotes")
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }


    @GetMapping("/equipment/{equipmentId}/history")
    @PreAuthorize("@resourceAccess.canAccessEquipment(#equipmentId, authentication)")
    public ResponseEntity<List<Maintenance>> getMaintenanceHistory(
            @PathVariable Long equipmentId) {

        return scoped(maintenanceService.getByEquipment(equipmentId));
    }


    @GetMapping("/date-range")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<?> getMaintenanceBetween(
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) {

        try {
            return scoped(maintenanceService.getMaintenanceBetween(start, end));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    private ResponseEntity<List<Maintenance>> scoped(List<Maintenance> records) {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(records.stream()
                .filter(record -> resourceAccess.canAccessMaintenance(record.getId(), auth))
                .collect(Collectors.toList()));
    }

}
