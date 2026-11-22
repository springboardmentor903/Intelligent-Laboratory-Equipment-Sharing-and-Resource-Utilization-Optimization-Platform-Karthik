package com.labresource.platform.sharing.controller;

import com.labresource.platform.sharing.service.SharingAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/sharing/analytics")
@PreAuthorize("hasAnyRole('INSTITUTION_ADMIN','SYSTEM_ADMIN')")
public class SharingAnalyticsController {
    private final SharingAnalyticsService analytics;

    public SharingAnalyticsController(SharingAnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestParam(required = false) Long institutionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication auth) {
        return run(from, to, () -> analytics.summary(analytics.resolveInstitution(institutionId, auth), from, to));
    }

    @GetMapping("/trends")
    public ResponseEntity<?> trends(@RequestParam(required = false) Long institutionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "WEEKLY") String grouping, Authentication auth) {
        if (!grouping.equalsIgnoreCase("WEEKLY") && !grouping.equalsIgnoreCase("MONTHLY"))
            return ResponseEntity.badRequest().body(Map.of("message", "grouping must be WEEKLY or MONTHLY."));
        return run(from, to, () -> analytics.trends(analytics.resolveInstitution(institutionId, auth), from, to, grouping));
    }

    @GetMapping("/partners")
    public ResponseEntity<?> partners(@RequestParam(required = false) Long institutionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "hoursProvided") String sort,
            @RequestParam(defaultValue = "desc") String direction, Authentication auth) {
        if (page < 0 || size < 1 || size > 100) return ResponseEntity.badRequest().body(Map.of("message", "Invalid pagination values."));
        return run(from, to, () -> analytics.partners(analytics.resolveInstitution(institutionId, auth), from, to, page, size, sort, direction));
    }

    @GetMapping("/partnership-report")
    public ResponseEntity<?> partnershipReport(@RequestParam Long firstInstitutionId, @RequestParam Long secondInstitutionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, Authentication auth) {
        if (firstInstitutionId == null || secondInstitutionId == null)
            return ResponseEntity.badRequest().body(Map.of("message", "Both institution IDs are required."));
        if (firstInstitutionId.equals(secondInstitutionId))
            return ResponseEntity.badRequest().body(Map.of("message", "Select two different institutions."));
        analytics.authorizePair(firstInstitutionId, secondInstitutionId, auth);
        return run(from, to, () -> analytics.partnershipReport(firstInstitutionId, secondInstitutionId, from, to, "MONTHLY"));
    }

    @GetMapping("/equipment/most-shared")
    public ResponseEntity<?> mostSharedEquipment(@RequestParam(required = false) Long institutionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "providedMinutes") String sort,
            @RequestParam(defaultValue = "desc") String direction, Authentication auth) {
        if (page < 0 || size < 1 || size > 100) return ResponseEntity.badRequest().body(Map.of("message", "Invalid pagination values."));
        return run(from, to, () -> analytics.mostSharedEquipment(analytics.resolveInstitution(institutionId, auth), from, to, page, size, sort, direction));
    }

    private ResponseEntity<?> run(LocalDate from, LocalDate to, java.util.function.Supplier<Object> action) {
        if (from == null || to == null || to.isBefore(from))
            return ResponseEntity.badRequest().body(Map.of("message", "A valid from/to date range is required."));
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        }
    }
}
