package com.labresource.platform.maintenance.controller;

import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.maintenance.entity.PreventiveMaintenanceSchedule;
import com.labresource.platform.maintenance.service.PreventiveScheduleService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance/schedules")
public class PreventiveScheduleController {

    private final PreventiveScheduleService scheduleService;
    private final ResourceAccessPolicy resourceAccess;

    public PreventiveScheduleController(PreventiveScheduleService scheduleService,
                                        ResourceAccessPolicy resourceAccess) {
        this.scheduleService = scheduleService;
        this.resourceAccess = resourceAccess;
    }

    private void forbidSystemAdmin(Authentication authentication) {
        if (authentication != null && resourceAccess.hasAnyRole(authentication, "SYSTEM_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SYSTEM_ADMIN cannot access maintenance operations");
        }
    }

    @GetMapping
    public ResponseEntity<Page<PreventiveMaintenanceSchedule>> getSchedules(
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "nextDueDate,asc") String sort,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Sort sortObj = parseSort(sort, "nextDueDate");
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), sortObj);
        return ResponseEntity.ok(scheduleService.getSchedules(equipmentId, active, pageable, authentication.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreventiveMaintenanceSchedule> getScheduleById(
            @PathVariable Long id,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(scheduleService.getScheduleById(id, authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<PreventiveMaintenanceSchedule> createSchedule(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Long equipmentId = body.get("equipmentId") != null ? Long.valueOf(body.get("equipmentId").toString()) : null;
        String title = body.get("title") != null ? body.get("title").toString() : null;
        String description = body.get("description") != null ? body.get("description").toString() : null;
        Integer repeatPeriodDays = body.get("repeatPeriodDays") != null ? Integer.valueOf(body.get("repeatPeriodDays").toString()) : null;

        LocalDate nextDueDate = null;
        if (body.get("nextDueDate") != null) {
            nextDueDate = parseLocalDate(body.get("nextDueDate").toString());
        }

        boolean active = body.get("active") == null || Boolean.parseBoolean(body.get("active").toString());

        PreventiveMaintenanceSchedule created = scheduleService.createSchedule(
                equipmentId, title, description, repeatPeriodDays, nextDueDate, active, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PreventiveMaintenanceSchedule> updateSchedule(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String title = body.get("title") != null ? body.get("title").toString() : null;
        String description = body.get("description") != null ? body.get("description").toString() : null;
        Integer repeatPeriodDays = body.get("repeatPeriodDays") != null ? Integer.valueOf(body.get("repeatPeriodDays").toString()) : null;

        LocalDate nextDueDate = null;
        if (body.get("nextDueDate") != null) {
            nextDueDate = parseLocalDate(body.get("nextDueDate").toString());
        }

        Boolean active = body.get("active") != null ? Boolean.valueOf(body.get("active").toString()) : null;

        PreventiveMaintenanceSchedule updated = scheduleService.updateSchedule(
                id, title, description, repeatPeriodDays, nextDueDate, active, authentication.getName());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<PreventiveMaintenanceSchedule> activateSchedule(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        boolean active = body.get("active") != null && Boolean.parseBoolean(body.get("active").toString());
        PreventiveMaintenanceSchedule updated = scheduleService.setScheduleActive(id, active, authentication.getName());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable Long id,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        scheduleService.deleteSchedule(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private LocalDate parseLocalDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw);
        } catch (Exception e) {
            try {
                return LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format: " + raw);
            }
        }
    }

    private Sort parseSort(String sortParam, String defaultProperty) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.ASC, defaultProperty);
        }
        String[] parts = sortParam.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
