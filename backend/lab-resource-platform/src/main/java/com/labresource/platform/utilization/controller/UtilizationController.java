
package com.labresource.platform.utilization.controller;

import com.labresource.platform.utilization.entity.Utilization;
import com.labresource.platform.utilization.service.UtilizationService;
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
@RequestMapping("/api/utilization")
public class UtilizationController {

    private final UtilizationService utilizationService;
    private final ResourceAccessPolicy resourceAccess;

    public UtilizationController(UtilizationService utilizationService,
                                 ResourceAccessPolicy resourceAccess) {
        this.utilizationService = utilizationService;
        this.resourceAccess = resourceAccess;
    }

    @GetMapping
    public ResponseEntity<List<Utilization>> getAllRecords() {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(utilizationService.getAllRecords().stream()
                .filter(record -> resourceAccess.canAccessUtilization(record.getId(), auth))
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@resourceAccess.canAccessUtilization(#id, authentication)")
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
    @PreAuthorize("@resourceAccess.canAccessEquipment(#equipmentId, authentication) and @resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<Utilization>> getRecordsByEquipment(
            @PathVariable Long equipmentId) {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(utilizationService.getRecordsByEquipment(equipmentId).stream()
                .filter(record -> resourceAccess.canAccessUtilization(record.getId(), auth))
                .collect(Collectors.toList()));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("@resourceAccess.ownsUser(#userId, authentication) or (@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessUser(#userId, authentication))")
    public ResponseEntity<List<Utilization>> getRecordsByUser(
            @PathVariable Long userId) {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(utilizationService.getRecordsByUser(userId).stream()
                .filter(record -> resourceAccess.canAccessUtilization(record.getId(), auth))
                .collect(Collectors.toList()));
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("@resourceAccess.canAccessBooking(#bookingId, authentication)")
    public ResponseEntity<List<Utilization>> getRecordsByBooking(
            @PathVariable Long bookingId) {
        Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(utilizationService.getRecordsByBooking(bookingId).stream()
                .filter(record -> resourceAccess.canAccessUtilization(record.getId(), auth))
                .collect(Collectors.toList()));
    }

    @PostMapping
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessEquipment(#record.equipmentId, authentication)")
    public ResponseEntity<?> createRecord(
            @RequestBody Utilization record,
            Authentication authentication) {
        try {
            record.setUserId(resourceAccess.currentUserId(authentication));
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
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessUtilization(#id, authentication)")
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
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN') and @resourceAccess.canAccessUtilization(#id, authentication)")
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
        List<Utilization> records = getAllRecords().getBody();
        summary.put("totalRecords", records == null ? 0 : records.size());
        summary.put("totalUsageHours", records == null ? 0.0 : records.stream()
                .mapToDouble(record -> record.getActualHours() == null ? 0.0 : record.getActualHours()).sum());

        return ResponseEntity.ok(summary);
    }


    @GetMapping("/equipment/{equipmentId}/summary")
    @PreAuthorize("@resourceAccess.canAccessEquipment(#equipmentId, authentication)")
    public ResponseEntity<Map<String, Object>> getEquipmentSummary(
            @PathVariable Long equipmentId) {

        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("equipmentId", equipmentId);
        summary.put("utilizationRecordCount",
                getRecordsByEquipment(equipmentId).getBody().size());

        return ResponseEntity.ok(summary);
    }


    @GetMapping("/summary/date-range")
    public ResponseEntity<?> getUsageSummaryByDateRange(
            @RequestParam LocalDateTime start,
            @RequestParam LocalDateTime end) {

        try {
            if (start == null || end == null || !end.isAfter(start)) {
                throw new IllegalArgumentException("A valid start and end time are required");
            }
            List<Utilization> records = getAllRecords().getBody();
            double hours = records == null ? 0.0 : records.stream()
                    .filter(record -> !record.getStartTime().isBefore(start)
                            && !record.getStartTime().isAfter(end))
                    .mapToDouble(record -> record.getActualHours() == null ? 0.0 : record.getActualHours())
                    .sum();
            Map<String, Object> summary = new java.util.HashMap<>();
            summary.put("start", start);
            summary.put("end", end);
            summary.put("totalUsageHours", hours);

            return ResponseEntity.ok(summary);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

}
