
package com.labresource.platform.maintenance.controller;

import com.labresource.platform.maintenance.entity.Maintenance;
import com.labresource.platform.maintenance.service.MaintenanceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @GetMapping
    public ResponseEntity<List<Maintenance>> getAllMaintenanceRecords() {
        return ResponseEntity.ok(
                maintenanceService.getAllMaintenanceRecords()
        );
    }

    @GetMapping("/{id}")
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
    public ResponseEntity<List<Maintenance>> getByEquipment(
            @PathVariable Long equipmentId) {
        return ResponseEntity.ok(
                maintenanceService.getByEquipment(equipmentId)
        );
    }

    @GetMapping("/reporter/{reportedBy}")
    public ResponseEntity<List<Maintenance>> getByReporter(
            @PathVariable Long reportedBy) {
        return ResponseEntity.ok(
                maintenanceService.getByReporter(reportedBy)
        );
    }

    @GetMapping("/technician/{assignedTo}")
    public ResponseEntity<List<Maintenance>> getByTechnician(
            @PathVariable Long assignedTo) {
        return ResponseEntity.ok(
                maintenanceService.getByTechnician(assignedTo)
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Maintenance>> getByStatus(
            @PathVariable String status) {
        return ResponseEntity.ok(
                maintenanceService.getByStatus(status)
        );
    }

    @GetMapping("/priority/{priority}")
    public ResponseEntity<List<Maintenance>> getByPriority(
            @PathVariable String priority) {
        return ResponseEntity.ok(
                maintenanceService.getByPriority(priority)
        );
    }

    @PostMapping
    public ResponseEntity<?> createMaintenance(
            @RequestBody Maintenance maintenance) {
        try {
            Maintenance created =
                    maintenanceService.createMaintenance(maintenance);

            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
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
    public ResponseEntity<Map<String, Object>> getMaintenanceSummary() {
        Map<String, Object> summary = new java.util.HashMap<>();

        summary.put(
                "totalRecords",
                maintenanceService.getTotalMaintenanceRecords()
        );
        summary.put(
                "reported",
                maintenanceService.getCountByStatus("REPORTED")
        );
        summary.put(
                "assigned",
                maintenanceService.getCountByStatus("ASSIGNED")
        );
        summary.put(
                "inProgress",
                maintenanceService.getCountByStatus("IN_PROGRESS")
        );
        summary.put(
                "completed",
                maintenanceService.getCountByStatus("COMPLETED")
        );
        summary.put(
                "cancelled",
                maintenanceService.getCountByStatus("CANCELLED")
        );
        summary.put(
                "highPriority",
                maintenanceService.getCountByPriority("HIGH")
        );
        summary.put(
                "criticalPriority",
                maintenanceService.getCountByPriority("CRITICAL")
        );

        return ResponseEntity.ok(summary);
    }


    @PatchMapping("/{id}/resolution")
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
    public ResponseEntity<List<Maintenance>> getMaintenanceHistory(
            @PathVariable Long equipmentId) {

        return ResponseEntity.ok(
                maintenanceService.getByEquipment(equipmentId)
        );
    }


    @GetMapping("/date-range")
    public ResponseEntity<?> getMaintenanceBetween(
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) {

        try {
            return ResponseEntity.ok(
                    maintenanceService.getMaintenanceBetween(start, end)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

}
