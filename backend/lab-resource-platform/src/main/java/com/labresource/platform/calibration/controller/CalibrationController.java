package com.labresource.platform.calibration.controller;

import com.labresource.platform.calibration.entity.CalibrationRecord;
import com.labresource.platform.calibration.service.CalibrationRecordService;
import com.labresource.platform.common.security.ResourceAccessPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/calibrations")
public class CalibrationController {

    private final CalibrationRecordService calibrationRecordService;
    private final ResourceAccessPolicy resourceAccess;

    public CalibrationController(CalibrationRecordService calibrationRecordService,
                                 ResourceAccessPolicy resourceAccess) {
        this.calibrationRecordService = calibrationRecordService;
        this.resourceAccess = resourceAccess;
    }

    @GetMapping
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<CalibrationRecord>> getAllCalibrations(
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        Long institutionId = resourceAccess.scopedInstitutionId(null, authentication);
        List<CalibrationRecord> records = calibrationRecordService.getAll(institutionId);

        if (equipmentId != null) {
            records = records.stream()
                    .filter(r -> r.getEquipment() != null && equipmentId.equals(r.getEquipment().getId()))
                    .toList();
        }
        if (status != null && !status.isBlank()) {
            records = records.stream()
                    .filter(r -> status.equalsIgnoreCase(r.getStatus()))
                    .toList();
        }

        return ResponseEntity.ok(records.stream()
                .filter(r -> r.getEquipment() != null && resourceAccess.canAccessEquipment(r.getEquipment().getId(), authentication))
                .collect(Collectors.toList()));
    }

    @GetMapping("/due")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<CalibrationRecord>> getDueCalibrations(
            @RequestParam(defaultValue = "30") int daysAhead,
            Authentication authentication) {
        Long institutionId = resourceAccess.scopedInstitutionId(null, authentication);
        List<CalibrationRecord> due = calibrationRecordService.getDueCalibrations(institutionId, daysAhead);
        return ResponseEntity.ok(due.stream()
                .filter(r -> r.getEquipment() != null && resourceAccess.canAccessEquipment(r.getEquipment().getId(), authentication))
                .collect(Collectors.toList()));
    }

    @GetMapping("/my-tasks")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<List<CalibrationRecord>> getMyAssignedCalibrations(Authentication authentication) {
        Long currentUserId = resourceAccess.currentUserId(authentication);
        if (currentUserId == null) return ResponseEntity.ok(List.of());

        List<CalibrationRecord> tasks = calibrationRecordService.getByTechnician(currentUserId);
        return ResponseEntity.ok(tasks.stream()
                .filter(r -> r.getEquipment() != null && resourceAccess.canAccessEquipment(r.getEquipment().getId(), authentication))
                .collect(Collectors.toList()));
    }

    @GetMapping("/summary")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'RESEARCHER_STUDENT', 'LAB_TECHNICIAN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<Map<String, Object>> getSummary(Authentication authentication) {
        Long institutionId = resourceAccess.scopedInstitutionId(null, authentication);
        return ResponseEntity.ok(calibrationRecordService.getSummary(institutionId));
    }

    @PostMapping("/schedule")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<?> scheduleCalibration(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            Long equipmentId = request.get("equipmentId") != null
                    ? Long.valueOf(request.get("equipmentId").toString()) : null;
            if (equipmentId == null || !resourceAccess.canAccessEquipment(equipmentId, authentication)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Access denied for this equipment."));
            }

            CalibrationRecord record = new CalibrationRecord();
            if (request.containsKey("scheduledDate") && request.get("scheduledDate") != null) {
                record.setScheduledDate(LocalDate.parse(request.get("scheduledDate").toString()));
            }
            if (request.containsKey("nextDueDate") && request.get("nextDueDate") != null) {
                record.setNextDueDate(LocalDate.parse(request.get("nextDueDate").toString()));
            } else if (record.getScheduledDate() != null) {
                record.setNextDueDate(record.getScheduledDate());
            }

            if (request.containsKey("assignedTo") && request.get("assignedTo") != null) {
                Long techId = Long.valueOf(request.get("assignedTo").toString());
                if (resourceAccess.isTechnician(techId) && resourceAccess.canAccessUser(techId, authentication)) {
                    record.setAssignedTo(techId);
                }
            }

            if (request.containsKey("notes") && request.get("notes") != null) {
                record.setNotes(request.get("notes").toString());
            }

            CalibrationRecord scheduled = calibrationRecordService.schedule(equipmentId, record);
            return ResponseEntity.status(HttpStatus.CREATED).body(scheduled);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("@resourceAccess.hasAnyRole(authentication, 'LAB_TECHNICIAN', 'LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<?> completeCalibration(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            LocalDate calibrationDate = request.get("calibrationDate") != null
                    ? LocalDate.parse(request.get("calibrationDate").toString()) : LocalDate.now();
            String result = request.get("result") != null ? request.get("result").toString() : null;
            LocalDate nextDueDate = request.get("nextDueDate") != null
                    ? LocalDate.parse(request.get("nextDueDate").toString()) : null;
            String notes = request.get("notes") != null ? request.get("notes").toString() : null;
            String certificateNumber = request.get("certificateNumber") != null
                    ? request.get("certificateNumber").toString() : null;

            CalibrationRecord completed = calibrationRecordService.complete(
                    id, calibrationDate, result, nextDueDate, notes, certificateNumber);
            return ResponseEntity.ok(completed);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
