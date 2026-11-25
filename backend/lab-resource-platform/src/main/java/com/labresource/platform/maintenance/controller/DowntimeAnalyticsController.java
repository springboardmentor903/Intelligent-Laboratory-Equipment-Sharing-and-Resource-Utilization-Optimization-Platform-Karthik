package com.labresource.platform.maintenance.controller;

import com.labresource.platform.common.security.ResourceAccessPolicy;
import com.labresource.platform.maintenance.service.DowntimeAnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance/downtime")
public class DowntimeAnalyticsController {

    private final DowntimeAnalyticsService downtimeAnalyticsService;
    private final ResourceAccessPolicy resourceAccess;

    public DowntimeAnalyticsController(DowntimeAnalyticsService downtimeAnalyticsService,
                                       ResourceAccessPolicy resourceAccess) {
        this.downtimeAnalyticsService = downtimeAnalyticsService;
        this.resourceAccess = resourceAccess;
    }

    private void forbidSystemAdmin(Authentication authentication) {
        if (authentication != null && resourceAccess.hasAnyRole(authentication, "SYSTEM_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SYSTEM_ADMIN cannot access maintenance operations");
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getDowntimeSummary(
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication authentication) {
        forbidSystemAdmin(authentication);
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        LocalDateTime start = null;
        if (startDate != null && !startDate.isBlank()) {
            start = parseStartDateTime(startDate);
        } else {
            // Default to 30 days ago
            start = LocalDateTime.now().minusDays(30).with(LocalTime.MIN);
        }

        LocalDateTime end = null;
        if (endDate != null && !endDate.isBlank()) {
            end = parseEndDateTime(endDate);
        } else {
            // Default to end of today
            end = LocalDateTime.now().with(LocalTime.MAX);
        }

        Map<String, Object> summary = downtimeAnalyticsService.getDowntimeSummary(
                equipmentId, start, end, authentication.getName());
        return ResponseEntity.ok(summary);
    }

    private LocalDateTime parseStartDateTime(String raw) {
        try {
            if (raw.length() <= 10) {
                return LocalDate.parse(raw).atStartOfDay();
            }
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid start date format: " + raw);
        }
    }

    private LocalDateTime parseEndDateTime(String raw) {
        try {
            if (raw.length() <= 10) {
                return LocalDate.parse(raw).atTime(LocalTime.MAX);
            }
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid end date format: " + raw);
        }
    }
}
