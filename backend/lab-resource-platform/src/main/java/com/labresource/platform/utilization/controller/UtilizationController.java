
package com.labresource.platform.utilization.controller;

import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.service.UtilizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/utilization")
public class UtilizationController {

    private final UtilizationService utilizationService;

    public UtilizationController(UtilizationService utilizationService) {
        this.utilizationService = utilizationService;
    }

    @GetMapping
    public ResponseEntity<List<Utilization>> getAllRecords() {
        return ResponseEntity.ok(utilizationService.getAllRecords());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getRecordById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(
                    utilizationService.getRecordById(id)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/equipment/{equipmentId}")
    public ResponseEntity<List<Utilization>> getRecordsByEquipment(
            @PathVariable Long equipmentId) {
        return ResponseEntity.ok(
                utilizationService.getRecordsByEquipment(equipmentId)
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Utilization>> getRecordsByUser(
            @PathVariable Long userId) {
        return ResponseEntity.ok(
                utilizationService.getRecordsByUser(userId)
        );
    }

    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<List<Utilization>> getRecordsByBooking(
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(
                utilizationService.getRecordsByBooking(bookingId)
        );
    }

    @PostMapping
    public ResponseEntity<?> createRecord(
            @RequestBody Utilization record) {
        try {
            Utilization createdRecord =
                    utilizationService.createRecord(record);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(createdRecord);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRecord(@PathVariable Long id) {
        try {
            utilizationService.deleteRecord(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        }
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateRecord(
            @PathVariable Long id,
            @RequestBody Utilization updatedRecord) {
        try {
            return ResponseEntity.ok(
                    utilizationService.updateRecord(id, updatedRecord)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }


    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("totalRecords", utilizationService.getTotalRecords());
        summary.put("totalUsageHours", utilizationService.getTotalUsageHours());

        return ResponseEntity.ok(summary);
    }


    @GetMapping("/equipment/{equipmentId}/summary")
    public ResponseEntity<Map<String, Object>> getEquipmentSummary(
            @PathVariable Long equipmentId) {

        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("equipmentId", equipmentId);
        summary.put(
                "utilizationRecordCount",
                utilizationService.getRecordCountByEquipment(equipmentId)
        );

        return ResponseEntity.ok(summary);
    }


    @GetMapping("/summary/date-range")
    public ResponseEntity<?> getUsageSummaryByDateRange(
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) {

        try {
            Map<String, Object> summary = new java.util.HashMap<>();
            summary.put("start", start);
            summary.put("end", end);
            summary.put(
                    "totalUsageHours",
                    utilizationService.getUsageHoursBetween(start, end)
            );

            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

}
